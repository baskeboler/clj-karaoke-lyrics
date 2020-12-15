(ns clj-karaoke.lyrics-frame
  (:require [clj-karaoke.protocols :as p :refer [PMap POffset ->map with-offset map->
                                                 PLyrics PSong get-text get-offset played?
                                                 get-next-event get-current-frame]]
            [clojure.string :as cstr :refer [starts-with? replace-first]]))


(declare frame-text)

(defrecord MidiLyricsFrame [events ticks]
  PMap
  (->map [this]
         {:type   :frame-event
          :ticks  (:ticks this)
          :events (map ->map (:events this))
          :offset (:offset this)})
  PLyrics
  (get-text [this] (frame-text this))
  (get-offset [this] (:offset this))
  (played? [this time]
    (let [last-offset (->> this
                           :events
                           last
                           :offset
                           (+ (get-offset this)))]
      (>= last-offset time)))
  (get-next-event [this offset]
    (->> this
         :events
         (filter #(< offset (+ (get-offset this) (get-offset %))))
         first)))



(defn frame-text [frame]
  (let [evts-text (mapv :text (:events frame))]
    (apply str evts-text)))



(defrecord MidiSimpleLyricsFrame [frame]
  PMap
  (->map [this]
         {:text          (frame-text (:frame this))
          :ticks         (-> this :frame :ticks)
          :offset        (-> this :frame :events first :offset)
          :event_offsets (map
                          (fn [evt]
                            {:offset     (:offset evt)
                             :char_count (count (:text evt))})
                          (-> this :frame :events))})
  POffset
  (with-offset [this offset]
    (-> this
        (update-in
         [:frame :events]
         (fn [evts]
           (map
            (fn [evt]
              (-> evt
                  (update :offset + offset)))
            evts))))))
 


(defn clean-frame? [evt]
  (empty? (:text evt)))

(defn start-of-frame? [evt]
  (or
   (starts-with? (:text evt) "\\")
   (starts-with? (:text evt) "/")
   (clean-frame? evt)))
(defn clean-start-of-frame [evt]
  (-> evt
      (update :text (fn [t]
                      (-> t
                          (replace-first #"\\" "")
                          (replace-first #"/" ""))))))


(defn lyrics-events-grouped [evts]
  (loop [res [] events evts]
    (if (empty? events)
      res
      (let [frame-start (-> (first events)
                            clean-start-of-frame)
            grp         (concat
                         [frame-start]
                         (take-while (comp not start-of-frame?) (rest events)))
            remaining   (drop-while (comp not start-of-frame?) (rest events))]
        (recur (conj res grp) remaining)))))

(defn lyrics-frames [evts]
  (let [grps (lyrics-events-grouped evts)]
    (map (fn [gr]
           (->
            (->MidiLyricsFrame gr (-> gr first :ticks))
            (assoc :offset (-> gr first :offset))
            (update :events
                    (fn [evts]
                      (let [base-offset (-> gr first :offset)
                            base-ticks (-> gr first :ticks)]
                        (map (fn [evt]
                               (-> evt
                                   (update :ticks #(- % base-ticks))
                                   (update :offset #(- % base-offset))))
                             evts))))))
         grps)))

(defmethod map-> :frame-event
  [{:keys [ticks events offset]}]
  (-> (->MidiLyricsFrame (map map-> events) ticks)
      (assoc :offset offset)))
