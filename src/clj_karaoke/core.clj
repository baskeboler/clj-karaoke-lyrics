(ns clj-karaoke.core
  (:require   [clj-cli-progress.core :as progress :refer [progress-bar-wrapped-collection]]
              [clj-karaoke.lyrics :as l]
              [clj-karaoke.protocols :as p]
              [clj-karaoke.song-data]
              [clj-karaoke.midi]
              [clj-karaoke.lyrics-frame]
              [clj-karaoke.lyrics-event]
              [clj-karaoke.ass :as ass]
              [clojure.core.reducers :as r]
              [clojure.java.io :as io]
              [clojure.data.json :as json]
              [clojure.tools.cli :refer [parse-opts]]
              [clojure.spec.alpha :as s]
              [clojure.string :as cstr])
  (:import [java.io File])
  (:gen-class))

(set! *warn-on-reflection* true)

;; (set! ^:dynamic *print-length* nil)

(def valid-formats #{:edn :json :ass})

(def opts
  [["-t" "--type TYPE" "Type of output"
    :default "json"
    :parse-fn {"json" :json
               "edn" :edn
               "ass" :ass}
    :validate [#(contains? valid-formats %) "Type must be either json, edn or ass"]]
   ["-z" "--offset NUMBER" "Lyrics offset"
    :default 0
    :parse-fn #(Integer/parseInt %)]
   ["-i" "--input-dir DIR" "Input Directory of midi files"]
   ["-o" "--output-dir DIR" "Output Directory of lyrics files"
    :default "lyrics"]
   ["-h" "--help"]])

(defn usage [summary]
  (-> ["midi lyrics extractor"
       ""
       "usage: clj-karaoke-lyrics [options] action"
       ""
       "Options:"
       summary]
      (cstr/join \newline)))

(defn error-msg [errors]
  (str "The following errors occured:\n\n"
       (cstr/join \newline errors)))

(defn process-opts [args]
  (let [{:keys [arguments errors summary options]}      (parse-opts args opts)
        {:keys [help input-dir output-dir type offset]} options
        [input-file output-file]                        arguments]
    (cond
      (some? help)
      {:exit-message (usage summary) :ok? true}
      errors
      {:exit-message (error-msg errors)}
      (and input-dir output-dir)
      {:action :process-dir :options options}
      (and input-file output-file)
      {:action :process-file :options options :input input-file :output output-file}
      :else
      {:exit-message (usage summary)})))
(declare extract-lyrics-from-file extract-song-data-from-file)
(declare extract-lyrics-from-input-directory extract-song-data-from-input-directory)

(defn -main [& args]
  (let [options                  (parse-opts args opts)
        [input-file output-file] (:arguments options)]
    (if (nil? (:errors options))
      (cond
        (-> options :options :help)
        (println (:summary options))
        (some? (-> options :options :input-dir))
        ;; (do
        (extract-song-data-from-input-directory
         (get-in options [:options :input-dir])
         (get-in options [:options :output-dir])
         (get-in options [:options :type])
         (get-in options [:options :offset]))
        :else
        (do
          (extract-song-data-from-file
           input-file
           output-file
           (-> options :options :type)
           (-> options :options :offset))
          (println "Done.")))
      (doseq [e (:errors options)]
        (println e)))))


(defn extract-song-data-from-file
  [input output format offset]
  (let [song    (-> (l/load-song-data-from-midi input)
                    (update :frames #(map (fn [frame]
                                            (update frame :offset + offset))
                                          %)))]
    (if-not (empty? (:frames song))
      (do
        (case format
          :edn  (spit output (pr-str (p/->map song)))
          :json (spit output (json/json-str (p/->map song)))
          :ass  (spit output (p/->ass song)))
        (println "done! generated " output))
      (println "skipping " input ", empty frames")))) 


(defn- is-midi? [^File file-obj]
  (.. file-obj
      (getName)
      (endsWith ".mid")))

(defn- filter-midis [path]
  (let [dir (io/file path)
        files (.listFiles dir)
        midi-files (filter is-midi? files)]
    (map #(.getAbsolutePath ^File %) midi-files)))

(defn extract-song-data-from-input-directory [input-dir output-dir format offset]
  (let [input-files (filter-midis input-dir)
        wrapped (progress-bar-wrapped-collection input-files "midi files")]
    (r/fold
     (fn
       ([] [])
       ([& r] (apply concat r)))
     (fn [res f]
       (let [file-name (.getName (io/file f))
             file-extension (case format
                              :ass ".ass"
                              :edn ".edn"
                              :json ".json")
             out-file-name (cstr/replace file-name #".mid" file-extension)
             out-path (str output-dir "/" out-file-name)
             song (l/load-song-data-from-midi f)]
         (if-not (empty? (:frames song))
           (do
             (case format
               :edn (spit out-path (pr-str (p/->map song)))
               :json (spit out-path (json/json-str (p/->map song)))
               :ass (spit out-path  (p/->ass song)))
             (conj res out-path))
           res)))
     wrapped)))
