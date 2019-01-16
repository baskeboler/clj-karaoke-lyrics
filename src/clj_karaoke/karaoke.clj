(ns clj-karaoke.karaoke
  (:require [clojure.string :as str :refer [join]]
            [clojure.core.async :as async :refer [<! >! chan go go-loop]]))

(defprotocol Playable
  (play [this output]))

(defrecord TextOffset [offset-ms text])

(defrecord LyricsFrame [base-offset text-offset-list])

(defrecord Lyrics [frames])

(defrecord PrintOutput [text])
(defrecord NewFrameOutput [frame])

(defn text-offset
  ([text ms]
   (->TextOffset ms text))
  ([text]
   (text-offset text 0)))

(defn empty-frame []
  (->LyricsFrame 0 []))

(defn add-text-to-frame [frame text offset]
  (let [last-offset (get (last (:text-offset-list frame)) :offset-ms 0)
        t (text-offset text (+ last-offset offset))]
    (-> frame
        (update :text-offset-list conj t))))

(defn frame-text [frame]
  (join " " (map :text (:text-offset-list frame))))

(defn empty-lyrics []
  (->Lyrics []))

(defn player [] (chan 100))

(extend-protocol Playable
  TextOffset
  (play [this output]
    (go-loop [_ (<! (async/timeout (:offset-ms this)))]
      (>! output (->PrintOutput (:text this)))))
  LyricsFrame
  (play [this output]
    (async/>!! output (->NewFrameOutput this))
    (go-loop [parts (:text-offset-list this)]
      (when-not (empty? parts)
        (play (first parts) output)
        (recur (rest parts)))))
  Lyrics
  (play [this output]
    (go-loop [f (:frames this)]
      (when-not (empty? f)
        (let [frame (first f)]
          (<! (async/timeout (:base-offset frame)))
          (play frame output)
          (recur (rest f)))))))
      

(def test-frame
  (-> (empty-frame)
      (add-text-to-frame "A        " 1000)
      (add-text-to-frame "B        " 2000)
      (add-text-to-frame "C        " 3000)))

(def test-frame-2
  (-> (empty-frame)
      (assoc :base-offset 11000)
      (add-text-to-frame "D        " 2000)
      (add-text-to-frame "E        " 2000)
      (add-text-to-frame "F        " 2000)))

(def test-lyrics (->Lyrics [test-frame test-frame-2]))

(defprotocol Player
  (new-frame [this frame])
  (print-output [this text])
  (player-start [this lyrics]))

(deftype MyPlayer [])

(extend-protocol  Player
  MyPlayer
  (new-frame [this frame]
    (println "\nnew frame ..."))
  (print-output [this text]
    (print (:text text)))
  (player-start [this lyrics]
    (let [in-chan (chan 1)]
      (play lyrics in-chan)
      (go-loop [o (<! in-chan)]
        (cond
          (instance? NewFrameOutput o) (new-frame this o)
          (instance? PrintOutput o) (print-output this o))
        (recur (<! in-chan))))))
