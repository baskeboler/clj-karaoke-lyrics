(ns clj-karaoke.db
  (:require [clojure.java.jdbc :as j]
            [hugsql.core :as hugs]
            [clojure.string :as str]))
(def mysql {:dbtype "mysql"
            :dbname "midis"
            :port 32768
            :user "root"
            :password "root"})

(comment 
  ;; (hugs/def-db-fns "db.sql" {:quoting :mysql})

  ;; (get-all-midis mysql)

  (defn insert-midi [name]
    (let [id (str (java.util.UUID/randomUUID))]
      (insert-midi! mysql {:id id
                           :name name})
      id))

  (defn find-midi [id]
    (midi-by-id mysql {:id id}))

  (defn insert-event [midi-id type message ticks offset]
    (let [id (str (java.util.UUID/randomUUID))]
      (insert-meta-message! mysql {:id id
                                   :midi_id midi-id
                                   :message message
                                   :type type
                                   :ticks ticks
                                   :offset offset})
      id))

  (defn fetch-midis [] (get-all-midis mysql))

  (def mid-pref "/home/victor/dev/clj-karaoke/./new-midis/")

  (comment
    (let [midis (fetch-midis)]
      (doseq [m midis
              :let [title (:name m)
                    new-name (str/replace title (re-pattern mid-pref) "")]]
        (println new-name)
        (update-midi! mysql {:id (:id m)
                             :name new-name}))))

  (defn is-start-of-frame? [evt]
    (when-not (nil? evt)
      (or
       (str/blank? (:message evt))
       (str/starts-with? (:message evt) "\\"))))

  (defn group-events [evts]
    (loop [groups []
           evts-left evts]
      (if (empty? evts-left)
        groups
        (let [grp (concat         [(first evts-left)]
                                  (take-while
                                   (comp not is-start-of-frame?)
                                   (rest evts-left)))]
          (recur (conj groups grp) (drop (count grp) evts-left)))))

    ;; (def example-groups
    (let [mid (first (fetch-midis))
          evts (meta-messages-by-midi-id mysql {:midi_id (:id mid)})]
      
      (group-events evts)))

  ;; (def stats (midi-stats mysql))

  ;; (def type-1-evts (filter #(> (:type_1_count %) 0) stats))
  ;; (def type-5-evts (filter #(> (:type_5_count %) 0) stats))

  (defn build-lyrics-evt [evt]
    {:id (:id evt)
     :text (:message evt)
     :ticks (:ticks evt)
     :offset (:offset evt)
     :type :lyrics-event})

  (defn build-frame-evt [lyrics-evts]
    {:id (str (java.util.UUID/randomUUID))
     :events (map build-lyrics-evt lyrics-evts)
     :type :frame-event
     :ticks (first  (map :ticks lyrics-evts))
     :offset (first (map :offset lyrics-evts))})


  (defn lyrics [midi]
    (let [evts (meta-messages-by-midi-id mysql {:midi_id (:id midi)})]
      (map build-lyrics-evt evts)))

  (comment
    
    (-> (mapv lyrics type-1-evts)
        (group-events))
    (doseq [m type-5-evts
            :let [fst-evt (first
                           (meta-messages-by-midi-id mysql {:midi_id (:id m)}))]
            :when (not (str/starts-with? (:message fst-evt) "\\"))]
      (println fst-evt)))

  (defn get-frames [midi-id]
    (let [midi (midi-by-id mysql {:id midi-id})
          evts (meta-messages-by-midi-id mysql {:midi_id midi-id})
          grps (group-events (filter #(= 1 (:type %)) evts))]
      (map (fn [g] {:type :frame-event}
                   :events g ) grps)))

  ;; (def sample-frames (get-frames (:id (first type-1-evts))))

  #_(def type-1-songs
      (filter #(= 0 (:type_5_count %))
              (filter #(< 0 (:type_1_count %)) stats)))

  (defn- substract [x]
    (fn [a] (- a x)))

  (comment
    (for [s type-1-songs
          :let [name (:name s)
                evts (meta-messages-by-midi-id mysql {:midi_id (:id s)})
                evts (filter #(= 1 (:type %)) evts)
                grps (group-events evts)
                frames (map build-frame-evt grps)
                frames (map (fn [fr]
                              (update fr
                                      :events
                                      (fn [evts]
                                        (map #(update
                                               %
                                               :offset
                                               (substract (:offset fr)))
                                             evts))))
                            frames)
                out-str (pr-str frames)]]
      (spit (str "lyrics4/" name ".edn") out-str)))

  (comment
    (def type-5 (filter #(not= 0 (:type_5_count %)) stats))

    (doseq [s type-5
            :let [id (:id s)
                  evts (->> (meta-messages-by-midi-id mysql {:midi_id id})
                            (filter #(= 5 (:type %))))
                  grps (group-events evts)
                  frames (map build-frame-evt grps)
                  frames (map (fn [fr]
                                (update fr :events
                                        (fn [evts]
                                          (map #(update % :offset (substract (:offset fr))) evts))))
                              frames)
                  out-str (pr-str frames)]]
      (spit (str "lyrics4/" (:name s) ".edn") out-str)
      (println "saved " (:name s)))))
