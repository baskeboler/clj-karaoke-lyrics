(ns clj-karaoke.lyrics
  (:require [clojure.string :refer [replace-first trim-newline join starts-with?]]
            [clojure.edn :as edn]
            [clojure.java.io :refer [file input-stream]]
            [clj-karaoke.protocols :refer [->map with-offset PMap POffset]]
            [clojure.core.async :as async :refer [<! >! go go-loop chan]]
            [clj-karaoke.lyrics-event :as levent :refer [create-lyrics-event]]
            [clj-karaoke.lyrics-frame :as lframe])
  (:import [javax.sound.midi MidiSystem Track MidiEvent Sequencer Sequence MetaEventListener MetaMessage MidiMessage]
           [java.lang String]
           [java.io File]))



(def generate-id (comp str gensym))

(defn set-event-id
  [evt]
  (if (nil? (:id evt))
    (assoc evt :id (generate-id))
    evt))

(deftype MyEvListener []
  MetaEventListener
  (meta [this evt]
    (println "Evt -- type : " (.getType evt) ", message: "
             (->> (.getMessage evt)
                  #(str  (bytes %))
                  (drop 3)
                  (apply str)))))



(defn event-text [^MetaMessage evt]
  (let [msg (.getMessage evt)]
    (->> (String. (bytes msg))
         (drop 3)
         (apply str)
         (trim-newline))))

(defn ensure-event-id [evt]
  (if (nil? (:id evt))
    (assoc evt :id (generate-id))
    evt))


(declare frame-text)

(defn tick-time [sequencer sequence]
  (let [bpm   (.getTempoInBPM sequencer)
        division-type (.getDivisionType sequence)
        ticks-sec   (condp = division-type
                      Sequence/PPQ
                      (* (.getResolution sequence) (/ bpm 60.0))
                      (* division-type (.getResolution sequence)))
        delta (/ 1000.0 ticks-sec)]
    (fn [tick]
      (* delta tick))))

(defn meta-messages
  [^Sequence sequence ^Sequencer sequencer]
  (.setSequence sequencer sequence)
  (.addMetaEventListener sequencer (MyEvListener.))
  (.play sequencer))

(defn file-meta-messages [filename]
  (with-open [in (input-stream filename)]
    (let [sequencer (MidiSystem/getSequencer)
          sequence (MidiSystem/getSequence in)]
      (meta-messages sequence sequencer))))


(defn getInterestingEvents [^Track t]
  (let [evt-count (.size t)]
    (for [evt-id (range evt-count)
          :let [evt (.get t evt-id)
                msg (.getMessage evt)
                offset (.getTick evt)]
          :when (and (pos? offset)
                     (= (type msg) MetaMessage)
                     (#{1 5} (.getType msg)))]
      (-> (create-lyrics-event :text (event-text msg)
                               :ticks offset
                               :midi-type (.getType msg))
          (set-event-id)))))
(defn lyrics-events
  ([^Sequence sequence ^Sequencer sequencer]
   (let [tracks      (.getTracks sequence)
         tick-fn     (tick-time sequencer sequence)
         msgs        (apply concat
                            (for [^Track t tracks]
                              (getInterestingEvents t)))
         sorted-msgs (sort-by :ticks msgs)]
     (map #(assoc % :offset (tick-fn (:ticks %))) sorted-msgs))))




(defn play-file  [file-path]
  (let [sequencer (MidiSystem/getSequencer)
        sequence  (MidiSystem/getSequence (file file-path))]
    (doto sequencer
      (.open)
      (.setSequence sequence))
    (let [evts     (lyrics-events sequence sequencer)
          frames   (lframe/lyrics-frames evts)
          tick-fn  (tick-time sequencer sequence)
          out-chan (chan)
          tos      (->>
                    (for [f (vec frames)]
                     (async/go
                      (async/<! (async/timeout (tick-fn (:ticks f))))
                      (async/>! out-chan f)))
                    (into []))]
      (.start sequencer)
      {:timeouts  tos
       :out-chan  out-chan
       :sequencer sequencer
       :sequence  sequence
       :frames    frames})))

(defn load-lyrics-from-midi [midi-file]
  (with-open [in (input-stream midi-file)]
    (try
      (let [sequence  (MidiSystem/getSequence in)
            sequencer (doto (MidiSystem/getSequencer)
                        (.open)
                        (.setSequence sequence))
            frames    (lframe/lyrics-frames (lyrics-events sequence sequencer))]
        (.close sequencer)
        frames)
      (catch Exception e
       (println (.getMessage e))
       (println "Failed to load lyrics from " midi-file)
       []))))



(defn save-lyrics [midi-file-path output-file]
  (let [frames     (load-lyrics-from-midi midi-file-path)
        output-str (pr-str (map ->map frames))]
    (spit output-file output-str)))

(defn deserialize-lyrics [lyrics-file]
  (let [reader-map {'clj_karaoke.lyrics.MidiLyricsEvent levent/map->MidiLyricsEvent
                    'clj_karaoke.lyrics.MidiLyricsFrame lframe/map->MidiLyricsFrame}]
    (edn/read-string {:readers reader-map} (slurp lyrics-file))))
