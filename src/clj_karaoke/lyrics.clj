(ns clj-karaoke.lyrics
  (:require [clojure.string :refer [trim-newline join]]
            ;; [clj-karaoke.karaoke :as k]
            [clojure.edn :as edn]
            [clojure.java.io :refer [file input-stream]]
            ; [clj-karaoke.db :as db]
            [clojure.core.async :as async :refer [<! >! go go-loop chan]])
  (:import [javax.sound.midi MidiSystem Track MidiEvent Sequencer Sequence MetaEventListener MetaMessage]
           ;; [java.io File IOException]
           [java.lang String]))

#_((def sample-file "/home/victor/Downloads/Crazy.mid")

   (def sample-file-format (MidiSystem/getMidiFileFormat (File. sample-file)))

   (def seqr (MidiSystem/getSequencer))

   (def mid-seq (MidiSystem/getSequence (File. sample-file))))

(deftype MyEvListener []
  MetaEventListener
  (meta [this evt]
    (println "Evt -- type : " (.getType evt) ", message: "
             (->> (.getMessage evt)
                  #(String. %)
                  (drop 3)
                  (apply str)))))

#_(doto seqr
    (.open)
    (.setSequence mid-seq)
    (.addMetaEventListener (->MyEvListener))
    (.start))

#_(.stop seqr)

(defn event-text [evt]
  (let [msg (.getMessage evt)]
    (->> (String. msg)
         (drop 3)
         (apply str)
         (trim-newline))))

#_(def tracks (.getTracks mid-seq))

#_(let [msgs (apply)
            concat
            (for [t tracks]
              (for [tcs (range (.size t))
                    :let [evt (.get t tcs)
                          msg (.getMessage evt)
                          offset (.getTick evt)]
                    :when (and
                           (> offset 0)
                           (instance? MetaMessage msg))]
                [offset (event-text msg) (.getType msg)]))]
      sorted-msgs (sort-by first msgs)
    (reduce (fn [res [offset text type]]
              (cond
                (empty? res) [text]
                (empty? text) (conj res text)
                :else (conj
                       (vec ((comp reverse rest reverse) res))
                       (join "" [(last res) type])))
              #_(if-not (= type 5))
              res
              (do
                (cond
                  (empty? res) [text]
                  (empty? text) (conj res text)
                  :else (conj
                         (vec ((comp reverse rest reverse) res))
                         (join "" [(last res) text])))))
            []
            sorted-msgs))

(defprotocol PMap
  (->map [this]))

(defrecord MidiLyricsEvent [text ticks])

(defrecord MidiLyricsFrame [events ticks])


(extend-protocol PMap
  MidiLyricsEvent
  (->map [this]
    {:type :lyrics-event
     :offset (:offset this)
     :ticks (:ticks this)
     :text (:text this)})

  MidiLyricsFrame
  (->map [this]
    {:type :frame-event
     :ticks (:ticks this)
     :events (map ->map (:events this))
     :offset (:offset (first (:events this)))}))

(defn tick-time [sequencer sequence]
  (let [bpm (.getTempoInBPM sequencer)
        ppq (if (pos? Sequence/PPQ)
              Sequence/PPQ
              (.getResolution sequence))
        delta (/ 60000.0 (* bpm ppq))]
    (fn [tick]
      (* delta tick))))

(defn lyrics-events
  ([sequence sequencer]
   (let [tracks (.getTracks sequence)
         tick-fn (tick-time sequencer sequence)
         msgs (apply concat
                     (for [t tracks :let [event-count (.size t)]]
                       (for [event-id (range event-count)
                             :let [evt (.get t event-id)
                                   msg (.getMessage evt)
                                   offset (.getTick evt)]
                             :when (and
                                    (> offset 0)
                                    (instance? MetaMessage msg)
                                    (or
                                     (= (.getType msg) 1)
                                     (= (.getType msg) 5)))]
                         (->MidiLyricsEvent (event-text msg) offset))))
         sorted-msgs (sort-by :ticks msgs)]
     (mapv #(assoc % :offset (tick-fn (:ticks %))) sorted-msgs)))
  ([sequence]
   (lyrics-events sequence 192)))

(defn clean-frame? [evt]
  (empty? (:text evt)))

(defn lyrics-events-grouped [evts]
  (loop [res [] events evts]
    (if (empty? events)
      res
      (let [grp (take-while (comp not clean-frame?) events)
            remaining (drop-while (comp not clean-frame?) events)]
        (recur (conj res grp) (rest remaining))))))

(defn lyrics-frames [evts]
  (let [grps (lyrics-events-grouped evts)]
    (map (fn [gr]
           (->
            (->MidiLyricsFrame gr (-> gr first :ticks))
            (assoc :offset (:offset (first gr)))))
         grps)))

(defn frame-text [frame]
  (let [evts-text (mapv :text (:events frame))]
    (apply str evts-text)))

(defn play-file  [file-path]
  (let [sequencer (MidiSystem/getSequencer)
        sequence (MidiSystem/getSequence (file file-path))]
    (doto sequencer
      (.open)
      (.setSequence sequence))
    (let [evts (lyrics-events sequence sequencer)
          frames (lyrics-frames evts)
          tick-fn (tick-time sequencer sequence)
          out-chan (chan)
          tos    (->>
                  (for [f (vec frames)]
                    (async/go
                      (async/<! (async/timeout (tick-fn (:ticks f))))
                      (async/>! out-chan f)))
                     ;; (println (frame-text f))))
                  (into []))]
      (.start sequencer)
      {:timeouts tos
       :out-chan out-chan
       :sequencer sequencer
       :sequence sequence
       :frames frames})))
#_(defrecord MidiKaraoke [sequencer sequence]
    Playable
    (play [this]))

(defn load-lyrics-from-midi [midi-file]
  (with-open [in (input-stream midi-file)]
    (try
      (let [sequence (MidiSystem/getSequence in)
            sequencer (doto (MidiSystem/getSequencer)
                       (.open)
                       (.setSequence sequence))
            frames (lyrics-frames (lyrics-events sequence sequencer))]
        (.close sequencer)
        frames)
      (catch Exception e
       (println (.getMessage e))
       (println "Failed to load lyrics from " midi-file)
       []))))


; (defn load-events-into-db [midi-file]
;   (let [sequencer (MidiSystem/getSequencer)]
;     (try
;       (let [sequence (MidiSystem/getSequence (File. midi-file))
;             sequencer (doto sequencer
;                         (.open)
;                         (.setSequence sequence))
;             tracks (.getTracks sequence)
;             tick-fn (tick-time sequencer sequence)
;             midi-id (db/insert-midi midi-file)]
;         (doseq [t tracks :let [event-count (.size t)]]
;           (doseq [event-id (range event-count)
;                   :let [evt (.get t event-id)
;                         msg (.getMessage evt)
;                         ticks (.getTick evt)
;                         offset (tick-fn ticks)]
;                           ;; message-type (.getType msg)]
;                   :when (and
;                          (> ticks 0)
;                          (instance? MetaMessage msg))]
;             (db/insert-event midi-id (.getType msg) (event-text msg) ticks (double offset))
;             (.close sequencer))))
;       (catch Exception e
;         (println "There was an error: " (.getMessage e)))
;       (finally
;         (.close sequencer)))))


(defn save-lyrics [midi-file-path output-file]
  (let [frames (load-lyrics-from-midi midi-file-path)
        output-str (pr-str (map ->map frames))]
    (spit output-file output-str)))

(defn deserialize-lyrics [lyrics-file]
  (let [reader-map {'clj_karaoke.lyrics.MidiLyricsEvent map->MidiLyricsEvent
                    'clj_karaoke.lyrics.MidiLyricsFrame map->MidiLyricsFrame}]
    (edn/read-string {:readers reader-map} (slurp lyrics-file))))
