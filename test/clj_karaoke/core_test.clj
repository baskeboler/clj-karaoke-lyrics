(ns clj-karaoke.core-test
  (:require [clojure.test :refer :all]
            [clj-cli-progress.core :as progress]
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
              (p/map->)))

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
    
      (is (= "He" (p/get-text an-event)))
      (is (= 0 (p/get-offset an-event)))
      (is (p/played? an-event 100))
      (is (p/played? an-event 121))
      (is (not-empty (p/get-next-event a-frame 20)))
      (is (empty? (p/get-next-event a-frame 200))))
  (testing "splitting and joining frames"
    (let [duration (-> a-frame :events last :offset)
          middle   (/ duration 2)
          [f1 f2]  (lf/split-frame-at a-frame 20)
          joined   (lf/join-frames f1 f2)]
      (is (= (-> a-frame :events count) (-> joined :events count)))
      (is (= (p/get-text a-frame) (p/get-text joined))))))

(deftest single-file-load-failure-test
  (testing "single file mode should not crash when load-song-data returns nil"
    (let [writes (atom 0)]
      (with-redefs [clj-karaoke.lyrics/load-song-data-from-midi (fn [_] nil)
                    clj-karaoke.core/write-song-data! (fn [& _] (swap! writes inc))]
        (is (false? (clj-karaoke.core/extract-song-data-from-file
                     "broken.mid"
                     "/tmp/out.json"
                     :json
                     0)))
        (is (= 0 @writes))))))

(deftest directory-mode-applies-offset-test
  (testing "directory mode applies offset before serialization"
    (let [writes (atom [])
          song {:title "x"
                :frames [{:offset 100 :events []}]}]
      (with-redefs [clj-karaoke.core/filter-midis (fn [_] ["/tmp/a.mid"])
                    progress/progress-bar-wrapped-collection (fn [coll _] coll)
                    clj-karaoke.lyrics/load-song-data-from-midi (fn [_] song)
                    clj-karaoke.core/write-song-data! (fn [song out fmt]
                                                        (swap! writes conj {:song song
                                                                            :out out
                                                                            :fmt fmt}))]
        (is (= ["/tmp/out/a.json"]
               (vec (clj-karaoke.core/extract-song-data-from-input-directory
                     "/tmp/in"
                     "/tmp/out"
                     :json
                     -1000))))
        (is (= -900 (-> @writes first :song :frames first :offset)))
        (is (= "/tmp/out/a.json" (-> @writes first :out)))
        (is (= :json (-> @writes first :fmt)))))))
