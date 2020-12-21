(ns clj-karaoke.song-data-specs
  (:require [clojure.spec.alpha :as s]))

(s/def ::offset double?)
(s/def ::midi-type #{1 5})
(s/def ::text string?)
(s/def ::ticks integer?)
(s/def ::type #{:lyrics-event :song-data :frame-event})
(s/def
  ::events
  (s/coll-of
   (s/keys :req-un [::midi-type ::offset ::text ::ticks ::type])))
(s/def
  ::frames
  (s/coll-of (s/keys :req-un [::events ::offset ::ticks ::type])))
(s/def ::resolution integer?)
(s/def ::division-type #{0.0})
(s/def ::tempo-bpm double?)
(s/def ::date string?)
(s/def ::title string?)
(s/def
  ::song-data
  (s/keys
   :req-un
   [::date
    ::division-type
    ::frames
    ::resolution
    ::tempo-bpm
    ::title
    ::type]))
