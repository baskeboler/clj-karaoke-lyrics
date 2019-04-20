(ns clj-karaoke.dev
  (:require [clj-karaoke.lyrics :as l]
            [clj-karaoke.db :as db]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.datafy :as datafy])
  (:import java.io.File))
(comment 
  (def ^:dynamic *print-length* nil)

  (def new-midis-str (slurp "newmidis.txt"))

  (def path-list (string/split-lines  new-midis-str))

  (def prefix-to-strip "/home/victor/Downloads/midi3/50000 MIDI FILES/Karaoke files")

  (defn strip-leading-dash [s]
    (if (string/starts-with? s "-")
      (string/replace-first s #"-" "")
      s))

  (defn build-file-name [path]
    (-> path
        (string/replace (re-pattern prefix-to-strip) "")
        (string/replace #"-|_" " ")
        (string/replace #"/" "-")
        (strip-leading-dash)))

  (def output-names (map build-file-name path-list))
  (def output-mid-path "new-midis")

  (defn copy-file [path output-folder]
    (let [output-file-name (build-file-name path)
          output-path (str output-folder "/" output-file-name)]
      (io/copy
       (io/file path)
       (io/file output-path))))

  (defn copyfiles []
    (doseq [p path-list]
      (println "Copying p")
      (copy-file p "new-midis")))

                                        ; (db/fetch-midis)

                                        ; (def sample-midi (db/find-midi "0a5e6610-ba44-45c2-ab38-6e21d3395ccf"))

                                        ; (def sample-frames (db/get-frames "0a5e6610-ba44-45c2-ab38-6e21d3395ccf"))
                                        ; (keys sample-frames)

                                        ; (keys (first db/sample-frames))
  (->> (first (drop 0 db/sample-frames))
       :events 
       (map :message))

  (count db/sample-frames)
  db/sample-frames
  (json/pprint db/sample-frames)
  (datafy/datafy db/sample-frames)

  (extend-protocol clojure.core.protocols/Datafiable
    java.sql.Timestamp
    (datafy [q] (.toString q)))

  (extend-protocol clojure.data.json/JSONWriter 
    java.sql.Timestamp
    (-write [obj out] (.print out (.getTime obj)) ))

  #_(->> (slurp "lyrics4/Aaron Tippin-aint nothin wrong with the radio aaron tippon.edn")
         (read-string)
                                        ;  (json/write-str)
         (clojure.pprint/pprint)))
