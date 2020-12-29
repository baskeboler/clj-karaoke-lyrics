(ns clj-karaoke.lyrics-frame
  (:require [clj-karaoke.protocols :as p :refer [PMap POffset ->map with-offset map->
                                                 PLyrics PSong get-text get-offset played?
                                                 get-next-event get-current-frame]]
            [clj-karaoke.lyrics-event]
            [clj-karaoke.utils :as utils :refer [generate-id ensure-id]]
            ;; [clj-karaoke.lyrics-event-specs :as events-specs]
            [clojure.string :as cstr :refer [starts-with? replace-first]]))
            ;; [clojure.spec.alpha :as s]))

            ;; [clojure.spec.gen.alpha :as gen]))

;; (s/def ::id string?)

;; (defn sorted-fn [k]
;;   (fn [col]
;;     (let [sorted (sort-by k col)]
;;       (= col sorted))))
;; (s/def :clj-karaoke.lyrics-frame.frame/events (s/and (s/coll-of ::events-specs/lyrics-event :into [])
;;                                                      (comp not empty?)
;;                                                      (sorted-fn :offset)
;;                                                      (sorted-fn :ticks)))

;; (s/def :clj-karaoke.lyrics-frame.frame-map/events (s/and (s/coll-of ::events-specs/lyrics-event-map :into [])
;;                                                          (comp not empty?)
;;                                                          (sorted-fn :offset)
;;                                                          (sorted-fn :ticks)))

;; (s/def ::offset (s/and number? (s/or :zero zero? :positive pos?)))
;; (s/def ::ticks (s/and int? (s/or :zero zero? :positive pos-int?)))
;; (s/def ::type #{:frame-event})
;; (s/def ::lyrics-frame (s/keys :req-un [:clj-karaoke.lyrics-frame.frame/events
;;                                        ::ticks]
;;                               :opt-un [::offset ::id]))

;; (s/def ::lyrics-frame-map (s/merge ::lyrics-frame
;;                                    (s/keys :req-un [::type
;;                                                     :clj-karaoke.lyrics-frame.frame-map/events])))

(declare frame-text)

(defrecord MidiLyricsFrame [events ticks]
  PMap
  (->map [this]
    {:type   :frame-event
     :id     (:id this)
     :ticks  (:ticks this)
     :events (map ->map (:events this))
     :offset (:offset this)})
  PLyrics
  (get-text [this] (apply str (map get-text events)))
  (get-offset [this] (:offset this))
  (played? [this time]
    (let [last-offset (->> events
                           last
                           :offset
                           (+ (get-offset this)))]
      (<= last-offset time)))
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
  [{:keys [ticks events offset id] :or {id (generate-id)} :as frame-map}]
  ;; {:pre [(s/valid? ::lyrics-frame-map frame-map)]
  ;; :post [(s/valid? ::lyrics-frame %)]]
  (-> (->MidiLyricsFrame (mapv map-> events) ticks)
      (assoc :id id :offset offset)
      (ensure-id)))

(defn split-frame-at [frame ms]
  (let [{:keys [events ticks  offset]} frame
        events-left                    (take-while #(< (get-offset %) ms) events)
        events-right                   (drop (count events-left) events)
        offset-right                   (-> events-right (nth 0) :offset)
        ticks-right                    (-> events-right (nth 0) :ticks)
        events-right                   (map    (comp
                                                #(update % :offset - offset-right)
                                                #(update % :ticks - ticks-right))
                                               events-right)]
    [(-> (->MidiLyricsFrame events-left ticks)
         (assoc :offset offset
                :id (:id frame)))
     (-> (->MidiLyricsFrame events-right (+ ticks ticks-right))
         (assoc :offset (+ offset offset-right)
                :id (generate-id)))]))

(defn frame-tick-duration [frame]
  (-> frame :events last :ticks))
(defn frame-ms-duration [frame]
  (-> frame :events last :offset))

(def not-nil? (comp not nil?))

(defn offsets? [frame]
  (and (:offset frame)
       (every? #(not-nil? (:offset %)) (:events frame))))
(defn sorted-events? [frame]
  (let [sorted (sort-by :offset (:events frame))]
    (= sorted (:events frame))))

(defn valid-frame? [frame]
  (and
   (not-nil? frame)
   (offsets? frame)
   (sorted-events? frame)))

(defn overlaps? [i1 i2]
  (let [[[_ y1] [x2 _]] (sort-by first [i1 i2])]
    (> y1 x2)))

(defn join-frames [f1 f2]
  (let [[f1 f2] (sort-by :offset [f1 f2])]
    (if (and (valid-frame? f1)
             (valid-frame? f2)
             (not (overlaps? [(p/get-offset f1) (+ (p/get-offset f1) (frame-ms-duration f1))]
                             [(p/get-offset f2) (+ (p/get-offset f2) (frame-ms-duration f2))])))
      (->MidiLyricsFrame
       (concat
        (:events f1)
        (map  (comp
               #(update % :offset
                        (fn [oldval]
                          (+ oldval (:offset f2) (* -1 (:offset f1)))))
               #(update % :ticks
                        (fn [oldval]
                          (+ oldval (:ticks f2) (* -1 (:ticks f1))))))
              (:events f2)))
       (:ticks f1)))))

(defn veclast [^clojure.lang.PersistentVector v]
  (nth v (-> v .length dec)))
