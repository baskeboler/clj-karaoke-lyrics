(ns clj-karaoke.protocols)

(defprotocol PMap
  (->map [this]))

(defn resolve-type [arg]
  (cond
    (map? arg) (:type arg)
    (and
     (seq? arg)
     (every? #(and (map? %)
                   (contains? % :type)
                   (= :frame-event (:type %)))
             arg))
    :sequence-of-frames))

(defmulti map-> resolve-type)

(defprotocol POffset
  (with-offset [this offset]))

(defprotocol PMidiReader
  (close-midi-reader [this])
  (get-tick-time [this])
  (get-lyrics-events [this])
  (get-resolution [this])
  (get-tempo-bpm [this])
  (get-division-type [this])
  (play-midi [this]))

(defprotocol PLyrics
  (get-text [this])
  (get-offset [this])
  (played? [this offset])
  (get-next-event [this offset]))

(defprotocol PSong
  (get-current-frame [this offset])
  (get-song-length [this])
  (validate [this])
  (sanitize [this]))

(defprotocol PSubtitles
  (->ass [this]))
