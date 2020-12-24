(ns clj-karaoke.core-test
  (:require [clojure.test :refer :all]
            [clj-karaoke.core]
            [clj-karaoke.song-data :as sd]
            [clj-karaoke.lyrics-event :as le :refer [create-lyrics-event]]
            [clj-karaoke.lyrics-frame :as lf]
            ;; [clj-karaoke.lyrics-event-specs :as evtspecs]
            [clj-karaoke.protocols :as p]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen])
  (:import [clj_karaoke.lyrics_event MidiLyricsEvent]))


(def generate-id (comp str gensym))
(def song-map
  {:title "a song"
   :type  :song-data
   :frames
   [{:id     (generate-id)
     :type   :frame-event
     :ticks  123
     :offset 123
     :events
     [{:id     (generate-id)
       :type   :lyrics-event
       :text   "He"
       :ticks  0
       :offset 0
       :midi-type 1}
      {:id     (generate-id)
       :type   :lyrics-event
       :text   "llo"
       :ticks  50
       :offset 50
       :midi-type 1}]}
    {:id     (generate-id)
     :type   :frame-event
     :ticks  500
     :offset 500
     :events
     [{:id     (generate-id)
       :type   :lyrics-event
       :text   " World!"
       :ticks  0
       :offset 0
       :midi-type 1}]}]})

(def song (-> song-map
              p/map->))

(def frames (:frames song))
(def a-frame (first frames))
(def an-event (first (:events a-frame)))
(deftest events-test
  (testing "empty lyrics events" 
    (let [e (le/create-lyrics-event)]
      ;; (is (= (type e) MidiLyricsEvent))
      (is (= "" (:text e) "")) 
      (is (= 5 (:midi-type e)))
      (is (string? (:id e)))
      (is (number? (:offset e)))
      (is (number? (:ticks e)))))
  (testing "some regular event"
    (let [evt (create-lyrics-event :id "myid" :ticks 100 :offset 120
                                   :midi-type 1 :text "hey")
          fr  (-> (gen/generate  (s/gen ::lf/lyrics-frame))
                  (update-in [:events]
                             (comp #(mapv le/map->MidiLyricsEvent %)
                                #(sort-by :ticks %)
                                (fn [evts] (map #(assoc % :offset (:ticks %)) evts))))
                  (lf/map->MidiLyricsFrame))]
      (is (= "hey" (p/get-text evt)))
      (is (= 120 (p/get-offset evt)))
      (is (not (p/played? evt 100)))
      (is (p/played? evt 121))
      (is (not-empty (p/get-next-event evt 100)))
      (is (empty? (p/get-next-event evt 200)))))
  (testing "splitting and joining frames"
    (let [duration (-> a-frame :events last :offset)
          middle   (/ duration 2)
          [f1 f2]  (lf/split-frame-at a-frame middle)
          joined   (lf/join-frames f1 f2)]
      (is (= (-> a-frame :events count) (-> joined :events count)))
      (is (= (p/get-text a-frame) (p/get-text joined))))))
