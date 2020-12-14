(ns clj-karaoke.protocols)

(defprotocol PMap
  (->map [this]))

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
  (get-current-frame [this offset]))
