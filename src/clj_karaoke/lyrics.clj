(ns clj-karaoke.lyrics
  (:require [clojure.edn :as edn]
            [clojure.java.io :refer [file input-stream] :as io]
            [clj-karaoke.lyrics-event :as levent]
            [clj-karaoke.lyrics-frame :as lframe]
            [clj-karaoke.midi :as midi]
            [clj-karaoke.song-data :as song-data]
            [clj-karaoke.protocols :as p]
            [clojure.string :as cstr]))


(defn play-file  [file-path]
  (let [reader (midi/create-midi-reader (file file-path))]
    (p/play-midi reader)))

(defn- with-midi-reader [midi-file f]
  (with-open [in (input-stream midi-file)]
    (let [mreader (midi/create-midi-reader in)]
      (try
        (f mreader)
        (finally
          (p/close-midi-reader mreader))))))

(defn load-lyrics-from-midi [midi-file]
  (try
    (with-midi-reader
      midi-file
      (fn [mreader]
        (lframe/lyrics-frames (p/get-lyrics-events mreader))))
    (catch Exception e
      (println (.getMessage e))
      (println "Failed to load lyrics from " midi-file)
      nil)))

(defn load-song-data-from-midi [midi-file]
  (try
    (with-midi-reader
      midi-file
      (fn [mreader]
        (let [frames        (lframe/lyrics-frames (p/get-lyrics-events mreader))
              bpm           (p/get-tempo-bpm mreader)
              resolution    (p/get-resolution mreader)
              title         (cstr/replace (-> (io/as-file midi-file)
                                              (.getName))
                                          #".mid"
                                          "")
              division-type (p/get-division-type mreader)]
          (song-data/create-song-data :title title
                                      :tempo-bpm bpm
                                      :division-type division-type
                                      :resolution resolution
                                      :frames frames))))
    (catch Exception e
      (println (.getMessage e))
      (println "Failed to load song data from " midi-file)
      nil)))

(defn save-lyrics [midi-file-path output-file]
  (if-let [frames (load-lyrics-from-midi midi-file-path)]
    (let [output-str (pr-str (map p/->map frames))]
      (spit output-file output-str))
    (println "skipping " midi-file-path ", failed to load lyrics")))

(defn deserialize-lyrics [lyrics-file]
  (let [reader-map {'clj_karaoke.lyrics.MidiLyricsEvent levent/map->MidiLyricsEvent
                    'clj_karaoke.lyrics.MidiLyricsFrame lframe/map->MidiLyricsFrame}]
    (edn/read-string {:readers reader-map} (slurp lyrics-file))))
