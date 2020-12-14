(ns clj-karaoke.protocols)

(defprotocol PMap
  (->map [this]))

(defprotocol POffset
  (with-offset [this offset]))

(defprotocol PMidiReader
  (close-midi-reader [this])
  (get-tick-time [this])
  (get-lyrics-events [this])
  (play-midi [this]))
