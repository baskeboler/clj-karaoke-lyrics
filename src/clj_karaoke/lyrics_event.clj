(ns clj-karaoke.lyrics-event
 (:require [clj-karaoke.protocols :refer [->map with-offset PMap]]))

(defrecord MidiLyricsEvent [text ticks midi-type]
  PMap
  (->map [this]
         {:type      :lyrics-event
          :offset    (:offset this)
          :ticks     (:ticks this)
          :text      (:text this)
          :midi-type (:midi-type this)}))



(defn create-lyrics-event
  [&{:keys [text ticks midi-type] :or {text "" midi-type 5 ticks 0}}]
  (->MidiLyricsEvent text ticks midi-type))
