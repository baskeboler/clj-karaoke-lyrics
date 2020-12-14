(ns clj-karaoke.lyrics
  (:require [clojure.string :refer [replace-first trim-newline join starts-with?]]
            [clojure.edn :as edn]
            [clojure.java.io :refer [file input-stream] :as io]
            [clj-karaoke.protocols :refer [->map with-offset PMap POffset]]
            [clojure.core.async :as async :refer [<! >! go go-loop chan]]
            [clj-karaoke.lyrics-event :as levent :refer [create-lyrics-event]]
            [clj-karaoke.lyrics-frame :as lframe]
            [clj-karaoke.midi :as midi]
            [clj-karaoke.song-data :as song-data]
            [clj-karaoke.protocols :as p]
            [clojure.string :as cstr])
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

(defn track-meta-message-events
  [^Track track]
  (let [evt-count (.size track)]
    (for [evt-id (range evt-count)
          :let [evt (.get track evt-id)
                msg (.getMessage evt)
                offset (.getTick evt)]
          :when (and (pos? offset)
                     (= MetaMessage (.getType msg)))]
      evt)))


;; (defn lyrics-events
;;   ([^Sequence sequence ^Sequencer sequencer]
;;    (let [tracks      (.getTracks sequence)
;;          tick-fn     (tick-time sequencer sequence)
;;          msgs        (apply concat
;;                             (for [^Track t tracks]
;;                               (getInterestingEvents t)))
;;          sorted-msgs (sort-by :ticks msgs)]
;;      (map #(assoc % :offset (tick-fn (:ticks %))) sorted-msgs))))


(defn play-file  [file-path]
  (let [reader (midi/create-midi-reader (file file-path))]
    (p/play-midi reader)))

(defn load-lyrics-from-midi [midi-file]
  (with-open [in (input-stream midi-file)]
    (try
      (let [mreader (midi/create-midi-reader in)
            frames    (lframe/lyrics-frames (p/get-lyrics-events mreader))]
        (p/close-midi-reader mreader)
        frames)
      (catch Exception e
        (println (.getMessage e))
        (println "Failed to load lyrics from " midi-file)
        []))))

(defn load-song-data-from-midi [midi-file]
  (with-open [in (input-stream midi-file)]
    (try
      (let [mreader       (midi/create-midi-reader in)
            frames        (lframe/lyrics-frames (p/get-lyrics-events mreader))
            bpm           (p/get-tempo-bpm mreader)
            resolution    (p/get-resolution mreader)
            title         (cstr/replace (-> (io/as-file midi-file)
                                            (.getName))
                                        #".mid"
                                        "")
            division-type (p/get-division-type mreader)
            song-data     (song-data/create-song-data :title title
                                                      :tempo-bpm bpm
                                                      :division-type division-type
                                                      :resolution resolution
                                                      :frames frames)]
        (p/close-midi-reader mreader)
        song-data)
      (catch Exception e
        (println (.getMessage e))
        (println "Failed to load song data from " midi-file)
        []))))

(defn save-lyrics [midi-file-path output-file]
  (let [frames     (load-lyrics-from-midi midi-file-path)
        output-str (pr-str (map ->map frames))]
    (spit output-file output-str)))

(defn deserialize-lyrics [lyrics-file]
  (let [reader-map {'clj_karaoke.lyrics.MidiLyricsEvent levent/map->MidiLyricsEvent
                    'clj_karaoke.lyrics.MidiLyricsFrame lframe/map->MidiLyricsFrame}]
    (edn/read-string {:readers reader-map} (slurp lyrics-file))))
