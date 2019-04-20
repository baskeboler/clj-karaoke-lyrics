(ns clj-karaoke.core
  (:require
              [clj-karaoke.lyrics :as l]
              [clojure.core.async :as async :refer [>! <! go go-loop chan]]
              [clojure.core.reducers :as r]
              [clojure.java.io :as io]
              [clojure.data.json :as json]
              [clojure.tools.cli :refer [parse-opts]])
  (:gen-class))



(def opts
  [["-t" "--type TYPE" "Type of output"
    :default :json
    :parse-fn {"json" :json
               "edn" :edn}
    :validate [#(contains? #{:json :edn} %) "Type must be either json or edn"]]
   ["-h" "--help"]])
(declare extract-lyrics-from-file)

(defn -main [& args]
  (let [options (parse-opts args opts)
        [input-file output-file] (:arguments options)]
    (if (nil? (:errors options))
      (cond
        (-> options :options :help)
        (println (:summary options))
        :else 
        (do
          (extract-lyrics-from-file input-file output-file (-> options :options :type))
          (println "Done.")))
      (doseq [e (:errors options)]
        (println e)))))




#_(defn extract-lyrics
   ([midi-dir output-dir]
    (let [files (filter-midis midi-dir)]
     (doseq [f (vec files)]
            :let [file-name (clojure.string/replace
                             f
                             (re-pattern midi-dir)
                             "")
                  out-file-name (clojure.string/replace file-name #".mid" ".edn")
                  out-path (str output-dir "/" out-file-name)
                  absolute-path f
                  lyrics (l/load-lyrics-from-midi absolute-path)]
            :when (not-empty lyrics)
      (println "Processing " absolute-path)
      (spit out-path (pr-str (map l/->map lyrics)))
      (println "Done! Generated " out-path))))
   ([midi-dir]
    (extract-lyrics midi-dir "lyrics")))

(defn extract-lyrics-from-file [input output format]
  (assert (contains? #{:edn :json} format))
  (let [frames (l/load-lyrics-from-midi input)]
   (if-not (empty? frames)
     (do
       (case format
         :edn (spit output (pr-str (map l/->map frames)))
         :json (spit output (json/json-str (map l/->map frames))))
       (println "Done! generated " output))
     (println "Skipping " input ", empty frames"))))

#_(defn extract-lyrics-2
   ([midi-dir output-dir]
    (let [files (filter-midis midi-dir)]
     (r/fold
      200
      (fn
        ([] [])
        ([& r] (apply concat r)))
      (fn [res f]
        (println "processing " f)
        (let [file-name (clojure.string/replace
                         f (re-pattern midi-dir) "")
              out-file-name (clojure.string/replace
                             file-name #".mid" ".edn")
              out-path (str output-dir "/" out-file-name)
              frames (l/load-lyrics-from-midi f)]
          (if-not (empty? frames)
            (do
              (spit out-path (pr-str (map l/->map frames)))
              (println "Done! generated " out-path)
              (conj res out-path))
            (do
              (println "Skipping " file-name ", empty frames")
              res))))
      (vec files))))
   ([midi-dir] (extract-lyrics-2 midi-dir "lyrics")))

