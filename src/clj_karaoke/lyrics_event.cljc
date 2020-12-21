(ns clj-karaoke.lyrics-event
  (:require [clj-karaoke.protocols :refer [->map with-offset PMap
                                           PLyrics get-text get-offset
                                           played? get-next-event
                                           map->]]
            [clj-karaoke.lyrics-event-specs :as specs]
            [clojure.spec.alpha :as s]))

(s/check-asserts false)

(def generate-id (comp str gensym))

(defn event->map
  "generate a map representation of the event"
  [this]
  {:pre  [(s/and
           (s/valid? ::specs/lyrics-event this))]
           ;; (s/valid? #(= 'clj_karaoke.lyrics_event.MidiLyricsEvent (type %)) this))]
   :post [(s/valid? ::specs/lyrics-event-map %)]}

  {:id        (:id this)
   :type      :lyrics-event
   :offset    (:offset this)
   :ticks     (:ticks this)
   :text      (:text this)
   :midi-type (:midi-type this)})

(s/fdef event->map
  :args (s/and  ::specs/lyrics-event)
                ;; #(= 'clj_karaoke.lyrics_event.MidiLyricsEvent (type %)))
  :ret ::specs/lyrics-event-map)


(defrecord ^:export MidiLyricsEvent [text ticks midi-type]
  PMap
  (->map [this]
    (event->map this))
  PLyrics
  (get-text [this] text)
  (get-offset [this] (:offset this))
  (played? [this time] (> time (:offset this)))
  (get-next-event [this time]
    (if (< time (:offset this))
      this
      nil)))

(defn create-lyrics-event
  [& {:keys [id text ticks type offset midi-type]
      :or   {text      ""
             midi-type 5
             type      :lyrics-event
             ticks     0
             id        (generate-id)
             offset    0}}]
  {:pre  [(s/valid? ::specs/lyrics-event-map
                    {:id        id
                     :text      text
                     :ticks     ticks
                     :offset    offset
                     :type      type
                     :midi-type midi-type})]
   :post [(s/valid? ::specs/lyrics-event %)]}
  (-> (->MidiLyricsEvent text ticks midi-type)
      (assoc :id id :offset offset)))

(defmethod map-> :lyrics-event
  [{:keys [ticks text midi-type offset id]
    :or   {id (generate-id)}
    :as   evt}]
  (-> (->MidiLyricsEvent text ticks midi-type)
      (assoc :offset offset
             :id id)))
