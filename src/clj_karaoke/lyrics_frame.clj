(ns clj-karaoke.lyrics-frame
  (:require [clj-karaoke.protocols :as p :refer [PMap POffset ->map with-offset]]
            [clojure.string :as cstr :refer [starts-with? replace-first]]))

(defrecord MidiLyricsFrame [events ticks]
  PMap
  (->map [this]
         {:type   :frame-event
          :ticks  (:ticks this)
          :events (map ->map (:events this))
          :offset (:offset (first (:events this)))}))




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
            (assoc :offset (:offset (first gr)))))
         grps)))
