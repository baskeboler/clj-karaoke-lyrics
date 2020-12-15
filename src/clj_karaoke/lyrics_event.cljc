(ns clj-karaoke.lyrics-event
  (:require [clj-karaoke.protocols :refer [->map with-offset PMap
                                           PLyrics get-text get-offset
                                           played? get-next-event
                                           map->]]))

(defrecord MidiLyricsEvent [text ticks midi-type]
  PMap
  (->map [this]
         {:type      :lyrics-event
          :offset    (:offset this)
          :ticks     (:ticks this)
          :text      (:text this)
          :midi-type (:midi-type this)})
  PLyrics
  (get-text [this] text)
  (get-offset [this] (:offset this))
  (played? [this time] (> time (:offset this)))
  (get-next-event [this time]
    (if (< time (:offset this))
      this
      nil)))



(defn create-lyrics-event
  [&{:keys [id text ticks offset midi-type]
     :or {text "" midi-type 5 ticks 0 id nil offset 0}}]
  (->MidiLyricsEvent text ticks midi-type))


(defmethod map-> :lyrics-event
  [{:keys [ticks text midi-type]}]
  (->MidiLyricsEvent text ticks midi-type))
