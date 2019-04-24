(ns clj-karaoke.core
  (:require   [clj-cli-progress.core :as progress :refer [progress-bar-wrapped-collection]]
              [clj-karaoke.lyrics :as l]
              [clojure.core.async :as async :refer [>! <! go go-loop chan]]
              [clojure.core.reducers :as r]
              [clojure.java.io :as io]
              [clojure.data.json :as json]
              [clojure.tools.cli :refer [parse-opts]])
  (:import [java.io File])
  (:gen-class))

(set! *warn-on-reflection* true)
;; (set! ^:dynamic *print-length* nil)
(def valid-formats #{:edn :json})
(def opts
  [["-t" "--type TYPE" "Type of output"
    :default :json
    :parse-fn {"json" :json
               "edn" :edn}
    :validate [#(contains? valid-formats %) "Type must be either json or edn"]]
   ["-i" "--input-dir DIR" "Input Directory of midi files"]
   ["-o" "--output-dir DIR" "Output Directory of lyrics files"
    :default "lyrics"]
   ["-h" "--help"]])
(declare extract-lyrics-from-file)
(declare extract-lyrics-from-input-directory)

(defn -main [& args]
  (let [options (parse-opts args opts)
        [input-file output-file] (:arguments options)]
    (if (nil? (:errors options))
      (cond
        (-> options :options :help)
        (println (:summary options))
        (some? (-> options :options :input-dir))
        (do
          (extract-lyrics-from-input-directory
           (get-in options [:options :input-dir])
           (get-in options [:options :output-dir])
           (get-in options [:options :type])))
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
  (assert (contains? valid-formats format))
  (let [frames  (map l/->MidiSimpleLyricsFrame (l/load-lyrics-from-midi input))
        wrapped (progress-bar-wrapped-collection frames "frames")]
    (if-not (empty? frames)
      (do
        (case format
          :edn (spit output (pr-str (map l/->map wrapped)))
          :json (spit output (json/json-str (map l/->map wrapped))))
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

(defn- is-midi? [^File file-obj]
  (.. file-obj
      (getName)
      (endsWith ".mid")))

(defn- filter-midis [path]
  (let [dir (io/file path)
        files (.listFiles dir)
        midi-files (filter is-midi? files)]
     (map #(.getAbsolutePath ^File %) midi-files)))

(defn extract-lyrics-from-input-directory [input-dir output-dir format]
  (assert (contains? valid-formats format))
  (let [input-files (filter-midis input-dir)
        wrapped (progress-bar-wrapped-collection input-files "midi files")]
    (r/fold
     16
     (fn
       ([] [])
       ([& r] (apply concat r)))
     (fn [res f]
       (let [file-name (clojure.string/replace f (re-pattern input-dir) "")
             file-extension (case format
                              :edn ".edn"
                              :json ".json")
             out-file-name (clojure.string/replace file-name #".mid" file-extension)
             out-path (str output-dir "/" out-file-name)
             frames (map l/->MidiSimpleLyricsFrame (l/load-lyrics-from-midi f))]
         (if-not (empty? frames)
           (do
             (case format
               :edn (spit out-path (pr-str (map l/->map frames)))
               :json (spit out-path (json/json-str (map l/->map frames))))
             ;; (println "Generated " out-path)
             (conj res out-path))
           (do
             ;; (println "Skipping " file-name ", empty frames")
             res))))
     wrapped)))
