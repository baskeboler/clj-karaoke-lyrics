(ns clj-karaoke.midi
  (:require [clj-karaoke.protocols :refer [PMidiReader
                                           get-tick-time get-lyrics-events]]
            [clj-karaoke.lyrics-event :refer [create-lyrics-event]]
            [clj-karaoke.lyrics-frame :as lframe]
            [clojure.core.async :as async]
            [clojure.string :as cstr :refer [trim-newline]])
  (:import [javax.sound.midi MidiSystem Track MidiEvent Sequencer Sequence MetaEventListener MetaMessage MidiMessage]
           [java.lang String]
           [java.io File]))
(def generate-id (comp str gensym))

(defn set-event-id
  [evt]
  (if (nil? (:id evt))
    (assoc evt :id (generate-id))
    evt))
(defn event-text [^MetaMessage evt]
  (let [msg (.getMessage evt)]
    (->> (String. (bytes msg))
         (drop 3)
         (apply str)
         (trim-newline))))



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



(defrecord MidiReader [midi-sequence midi-sequencer]
  PMidiReader
  (get-tick-time [this] (tick-time midi-sequencer midi-sequence))
  (get-lyrics-events [this] (lyrics-events midi-sequence midi-sequencer))
  (close-midi-reader [thid] (.close midi-sequencer))
  (play-midi [this]
   (let [evts     (get-lyrics-events this)
         frames   (lframe/lyrics-frames evts)
         tick-fn  (get-tick-time this)
         out-chan (async/chan)
         tos      (->>
                   (for [f (vec frames)]
                     (async/go
                       (async/<! (async/timeout (tick-fn (:ticks f))))
                       (async/>! out-chan f)))
                   (into []))]
     (.start midi-sequencer)
     {:timeouts  tos
      :out-chan  out-chan
      :sequencer midi-sequencer
      :sequence  midi-sequence 
      :frames    frames})))
;; (get-midi-events [this]))


(defn ^:export create-midi-reader [file]
  (let [midi-sequence (MidiSystem/getSequence file)
        midi-sequencer (MidiSystem/getSequencer)]
    (doto midi-sequencer
      (.open)
      (.setSequence midi-sequence))
    (->MidiReader midi-sequence midi-sequencer)))
