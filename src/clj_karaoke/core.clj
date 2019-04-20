(ns clj-karaoke.core
  (:require
   ;; [seesaw.core :as ss]
              ;; [seesaw.font :as sf]
              ;; [seesaw.icon :as si]
              ;; [clojurefx.clojurefx :as fx]
              ;; [clojurefx.fxml :as fxml]
              [clj-karaoke.lyrics :as l]
              ; [clj-karaoke.db :as db]
              [clojure.core.async :as async :refer [>! <! go go-loop chan]]
              [clojure.core.reducers :as r]
              [clojure.java.io :as io])
  (:import [javax.swing JFileChooser]
           [javax.swing.filechooser FileFilter FileNameExtensionFilter]
           [java.io File])
   ;; [fn-fx.fx-dom :as dom]
            ;; [fn-fx.controls :as ui]
            ;; [fn-fx.diff :refer [component defui render should-update?]])
  (:gen-class))


;; (defonce force-toolkit-init (javafx.embed.swing.JFXPanel.))

;; (set! *print-length* nil)

;; (def main-font (ui/font :family "Helvetica" :size 20))

(defn select-file ^File []
  (let [file-filter (FileNameExtensionFilter. "Midi File" (into-array String ["mid"]))
        chooser (doto (JFileChooser.)
                  (.addChoosableFileFilter file-filter))
        result (.showOpenDialog chooser nil)]
    (if (= result JFileChooser/APPROVE_OPTION)
      (.getSelectedFile chooser)
      nil)))

(defn open-midi
  ([out-fn]
   (let [f (select-file)
         {:keys [out-chan frames] :as ret} (l/play-file (.getAbsolutePath f))]
     (go-loop [in-frame (<! out-chan)]
       (when-not (nil? in-frame)
         (println (l/frame-text in-frame))
         (out-fn (l/frame-text in-frame))
         (recur (<! out-chan))))
     ret))
  ([] (open-midi identity)))

;; (def big-text (ss/label "This is some text. Fuck you."))
;; (ss/config! big-text :font (sf/font :name :monospaced
                                    ;; :style #{:bold :italic}
                                    ;; :size 42
                                    ;; :icon (seesaw.icon/icon "Dolphin.jpg")
                                    ;; :foreground "#FF0000")))

;; (defn change-frame [text]
  ;; (ss/config! big-text :text text))
;; (defn init! []
  ;; (-> (ss/frame :title "Hi" :size [800 :by 600] :icon (seesaw.icon/icon "Dolphin.jpg")
                ;; :content (ss/grid-panel
                          ;; :items [
                                  ;; (ss/vertical-panel :items [big-text "victor" "pepe"]))
      ;; ss/pack!
      ;; ss/show!)
  ;; (open-midi change-frame))

;; (defn start [^javafx.stage.Stage stage]
  ;; (.show stage))
(declare extract-lyrics-from-file)

(defn -main [input-file output-file & args]
  (println "Hi!" input-file)
  (extract-lyrics-from-file input-file output-file)
  (println "Done."))


(defn- is-midi? [^File file-obj]
  (.. file-obj
      (getName)
      (endsWith ".mid")))

(defn filter-midis [path]
  (let [dir (io/file path)
        files (.listFiles dir)
        midi-files (filter is-midi? files)]
    (map #(.getAbsolutePath %) midi-files)))

;; (def winfx (fxml/load-fxml  "resources/cosa.fxml"))

(defn extract-lyrics
  ([midi-dir output-dir]
   (let [files (filter-midis midi-dir)]
    (doseq [f (vec files)
            :let [file-name (clojure.string/replace
                             f
                             (re-pattern midi-dir)
                             "")
                  out-file-name (clojure.string/replace file-name #".mid" ".edn")
                  out-path (str output-dir "/" out-file-name)
                  absolute-path f
                  lyrics (l/load-lyrics-from-midi absolute-path)]
            :when (not-empty lyrics)]
      (println "Processing " absolute-path)
      ;; (l/save-lyrics absolute-path out-path)
      (spit out-path (pr-str (map l/->map lyrics)))
      (println "Done! Generated " out-path))))
  ([midi-dir]
   (extract-lyrics midi-dir "lyrics")))

; (defn load-db [midi-dir]
;   (let [files (filter-midis midi-dir)]
;     (r/fold
;      200
;      (fn ([] []) ([& r] (apply concat r)))
;      (fn
;        ([res f]
;         (println "processing " f)
;         (l/load-events-into-db f)
;         f))
;      files)))
(defn extract-lyrics-from-file [input output]
  (let [frames (l/load-lyrics-from-midi input)]
   (if-not (empty? frames)
     (do
       (spit output (pr-str (map l/->map frames)))
       (println "Done! generated " output))
     (println "Skipping " input ", empty frames"))))

(defn extract-lyrics-2
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

