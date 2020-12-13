(ns clj-karaoke.protocols)

(defprotocol PMap
  (->map [this]))

(defprotocol POffset
  (with-offset [this offset]))
