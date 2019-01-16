(ns clj-karaoke.lyrics
  (:require [clojure.string :refer [trim-newline join]]
            [clj-karaoke.karaoke :as k])
  (:import [javax.sound.midi MidiSystem Track MidiEvent Sequencer Sequence MetaEventListener MetaMessage]
           [java.io File]
           [java.lang String]))


#_(comment
   (def cx "007074704954011898567:vq4nmfwmtgc")

   (def key "AIzaSyDf_X7uI76-BTI7Y9fIH0CZQ6JOE9ow9jg")

   (def base-search-url "https://www.googleapis.com/customsearch/v1")
   (defn build-search-url [q]
     (str "https://www.googleapis.com/customsearch/v1?key=" key "&cx=" cx "&q=" q))

   (def sample-url (build-search-url "take on me"))

   (defn search-music [q]
     (http/get base-search-url
               {:query-params {:q q
                               :key key
                               :cx cx}
                :as :json}))

   (def sample-res (http/get base-search-url
                             {:query-params {:q "take on me"
                                             :key key
                                             :cx cx}
                              :as :json})))


  (def sample-file "/home/victor/Downloads/Africa.mid")

(def sample-file-format (MidiSystem/getMidiFileFormat (File. sample-file)))

(def seqr (MidiSystem/getSequencer))

(def mid-seq (MidiSystem/getSequence (File. sample-file)))


(deftype MyEvListener []
  MetaEventListener
  (meta [this evt]
    (println "Evt -- type : " (.getType evt) ", message: "
             (->> (.getMessage evt)
                  #(String. %)
                  (drop 3)
                  (apply str)))))


(doto seqr
  (.open)
  (.setSequence mid-seq)
  (.addMetaEventListener (->MyEvListener))
  (.start))

(.stop seqr)


(defn event-text [ evt]
  (let [msg (.getMessage evt)]
    (->> (String. msg)
         (drop 3)
         (apply str)
         (trim-newline))))

(def tracks (.getTracks mid-seq))

(let [msgs (apply
            concat
            (for [t tracks]
              (for [tcs (range (.size t))
                    :let [evt (.get t tcs)
                          msg (.getMessage evt)
                          offset (.getTick evt)]
                    :when (and
                           (> offset 0)
                           (instance? MetaMessage msg))]
                [offset (event-text msg) (.getType msg)])))
      sorted-msgs (sort-by first msgs)]
  (reduce (fn [res [offset text type]]
            (if-not (= type 5)
              res
              (do
                (cond
                  (empty? res) [text]
                  (empty? text) (conj res text)
                  :else (conj
                         (vec ((comp reverse rest reverse) res))
                         (join "" [(last res) text]))))))
          []
          sorted-msgs))


(defrecord MidiLyricsEvent [text ticks])

(defrecord MidiLyricsFrame [events ticks])

(defn lyrics-events
  ([sequence resolution]
   (let [tracks (.getTracks sequence)
         msgs (apply concat
                     (for [t tracks :let [event-count (.size t)]]
                       (for [event-id (range event-count)
                             :let [evt (.get t event-id)
                                   msg (.getMessage evt)
                                   offset (.getTick evt)]
                             :when (and
                                    (> offset 0)
                                    (instance? MetaMessage msg)
                                    (= (.getType msg) 5))]
                         (->MidiLyricsEvent (event-text msg) offset))))
         sorted-msgs (sort-by :ticks msgs)]
     (mapv #(assoc % :offset (* resolution (:ticks %))) sorted-msgs)))
  ([sequence]
   (lyrics-events sequence 192)))

(defn tick-time [seqr sequence]
  (let [bpm (.getTempoInBPM seqr)
        ppq (if (pos? Sequence/PPQ) Sequence/PPQ 120.0)
        delta (/ 60000.0 (* bpm ppq))]
    (fn [tick]
      (* delta tick))))

#_(defrecord MidiKaraoke [sequencer sequence]
   Playable
   (play [this]))
