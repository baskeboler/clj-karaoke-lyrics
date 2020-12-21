(ns clj-karaoke.core-test
  (:require [clojure.test :refer :all]
            [clj-karaoke.core :refer :all]
            [clj-karaoke.lyrics-event :as le :refer [create-lyrics-event]]
            [clj-karaoke.lyrics-frame :as lf]
            [clj-karaoke.protocols :as p]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]))
(deftest events-test
  (testing "empty lyrics events" 
    (let [e (le/create-lyrics-event)]
      (is (= (type e) `clj_karaoke.lyrics_event.MidiLyricsEvent))
      (is (= "" (:text e) "")) 
      (is (= 5 (:midi-type e)))
      (is (string? (:id e)))
      (is (number? (:offset e)))
      (is (number? (:ticks e)))))
  (testing "some regular event"
    (let [evt (create-lyrics-event :id "myid" :ticks 100 :offset 120
                                   :midi-type 1 :text "hey")
          fr (-> (gen/generate  (s/gen ::le/lyrics-frame))
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
      (is (empty? (p/get-next-event evt 200))))))
