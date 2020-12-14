(ns clj-karaoke.song-data
  (:require [tick.core :as t]
            [clojure.string :as cstr]))
(def division-types
  {:PPQ          0.0
   :SMPTE_24     24.0
   :SMPTE_25     25.0
   :SMPTE_30     30.0
   :SMPTE_30DROP 29.97})

(defn tick-time [song-data]
  (let [{:keys [tempo-bpm division-type resolution]} song-data
        ticks-sec (condp = division-type
                    (:PPQ division-types) (* resolution (/ tempo-bpm 60.0))
                    (* division-type resolution))
        delta (/ 1000.0 ticks-sec)]
    (fn [ticks]
      (* delta ticks))))

(defrecord SongData [title date frames tempo-bpm division-type resolution])


(defn create-song-data
  [& {:keys [title date frames tempo-bpm division-type resolution]
      :or   {date          (str (t/now))
             division-type (:PPQ division-types)}}]
  (assert (and (not (cstr/blank? title))
               (seq? frames)))
  (->SongData title date frames tempo-bpm division-type resolution))
