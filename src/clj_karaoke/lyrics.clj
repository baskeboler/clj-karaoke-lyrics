(ns clj-karaoke.lyrics
  (:require [clojure.string :refer [replace-first trim-newline join starts-with?]]
            [clojure.edn :as edn]
            [clojure.java.io :refer [file input-stream]]
            [clojure.core.async :as async :refer [<! >! go go-loop chan]])
  (:import [javax.sound.midi MidiSystem Track MidiEvent Sequencer Sequence MetaEventListener MetaMessage]
           [java.lang String]))


(deftype MyEvListener []
  MetaEventListener
  (meta [this evt]
    (println "Evt -- type : " (.getType evt) ", message: "
             (->> (.getMessage evt)
                  #(String. %)
                  (drop 3)
                  (apply str)))))

(defn event-text [evt]
  (let [msg (.getMessage evt)]
    (->> (String. msg)
         (drop 3)
         (apply str)
         (trim-newline))))


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

(defn start-of-frame? [evt]
  (or
   (starts-with? (:text evt) "\\")
   (starts-with? (:text evt) "/")
   (clean-frame? evt)))
(defn clean-start-of-frame [evt]
  (-> evt
      (update :text (fn [t]
                      (-> t
                          (replace-first #"\\" "")
                          (replace-first #"/" ""))))))

(defn lyrics-events-grouped [evts]
  (loop [res [] events evts]
    (if (empty? events)
      res
      (let [frame-start (-> (first events)
                            clean-start-of-frame)
            grp (concat
                 [frame-start]
                 (take-while (comp not start-of-frame?) (rest events)))
            remaining (drop-while (comp not start-of-frame?) (rest events))]
        (recur (conj res grp) remaining)))))

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
                  (into []))]
      (.start sequencer)
      {:timeouts tos
       :out-chan out-chan
       :sequencer sequencer
       :sequence sequence
       :frames frames})))

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



(defn save-lyrics [midi-file-path output-file]
  (let [frames (load-lyrics-from-midi midi-file-path)
        output-str (pr-str (map ->map frames))]
    (spit output-file output-str)))

(defn deserialize-lyrics [lyrics-file]
  (let [reader-map {'clj_karaoke.lyrics.MidiLyricsEvent map->MidiLyricsEvent
                    'clj_karaoke.lyrics.MidiLyricsFrame map->MidiLyricsFrame}]
    (edn/read-string {:readers reader-map} (slurp lyrics-file))))
