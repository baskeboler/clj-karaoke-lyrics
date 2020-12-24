(ns clj-karaoke.song-data
  (:require [tick.core :as t]
            [clojure.string :as cstr]
            [clj-karaoke.protocols :as p :refer [PMap ->map map-> PSong
                                                 PLyrics get-current-frame
                                                 get-text get-offset played?
                                                 get-next-event validate sanitize]]
            [clj-karaoke.lyrics-frame] ; make sure these map-> imeplementations are available
            [clj-karaoke.lyrics-event]))

(def division-types
  {:PPQ          0.0
   :SMPTE_24     24.0
   :SMPTE_25     25.0
   :SMPTE_30     30.0
   :SMPTE_30DROP 29.97})

(defn tick-time
  "returns a funtion that converts ticks to ms"
  [song-data]
  (let [{:keys [tempo-bpm division-type resolution]} song-data
        ticks-sec (condp = division-type
                    (:PPQ division-types) (* resolution (/ tempo-bpm 60.0))
                    (* division-type resolution))
        delta (/ 1000.0 ticks-sec)]
    (fn [ticks]
      (* delta ticks))))

(defn estimated-song-length [s]
  (let [last-frame (last (:frames s))
        last-event (last (:events last-frame))]
    (+ (p/get-offset last-frame)
       (p/get-offset last-event)
       5000)))

(defn get-events [song]
  (apply concat (map :events (:frames song))))

(defn midi-types [song]
  (into #{} (map :midi-type (get-events song))))

(defn midi-type-counts [song midi-type]
  (count (filter #(= midi-type (:midi-type %)) (get-events song))))

(defrecord SongData [title date frames tempo-bpm division-type resolution]
  PMap
  (->map [this]
    {:title         title
     :date          date
     :type          :song-data
     :tempo-bpm     tempo-bpm
     :division-type division-type
     :resolution    resolution
     :frames        (map ->map frames)})
  PSong
  (get-current-frame [this offset]
    (->> this
         :frames
         (filterv (comp not #(played? % offset)))
         first))
  (get-song-length [this] (estimated-song-length this))
  (validate [this] true)
  (sanitize [this] this)
  PLyrics
  (get-text [this]
    (apply str (map get-text frames)))
  (get-offset [this] 0)
  (played? [this offset]
    (let [last-frame        (-> frames last)
          last-frame-offset (get-offset last-frame)
          last-evt          (-> last-frame :events last)
          last-evt-offset   (-> last-evt :offset (+ last-frame-offset))]
      (> offset last-evt-offset)))
  (get-next-event [this offset]
    (let [current-frame (get-current-frame this offset)]
      (-> (filter #(> (+ (get-offset current-frame)
                         (get-offset %))
                      offset)
                  (:events current-frame))
          first))))
    

(defn create-song-data
  "Creates a song data object that includes:

  * song title
  * creation date
  * lyrics frames
  * sequencer tempo in bpm
  * sequence resolution
  * division type

  the last 3 properties are required to calculate the song tick time."
  [& {:keys [title date frames tempo-bpm division-type resolution]
      :or   {date          (str (t/now))
             division-type (:PPQ division-types)}}]
  (assert (and (not (cstr/blank? title))
               (seq? frames)))
  (->SongData title date frames tempo-bpm division-type resolution))

(defmethod map-> :song-data [m]
  (-> m
      (update :frames (partial mapv map->))
      (map->SongData)))
