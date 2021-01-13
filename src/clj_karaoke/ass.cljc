(ns clj-karaoke.ass
  (:require [clj-karaoke.protocols :as p]
            [clj-karaoke.lyrics-frame :as lf]
            [clj-karaoke.lyrics-event :as le]
            [clojure.string :refer [join]]
            #?(:cljs [goog.string :refer [format]])))

(defprotocol AssSection
  (print-ass-section [this]))

(defn alignment-> [a]
  (condp = a
    :bottom-left   1
    :bottom-center 2
    :bottom-right  3
    :middle-left   4
    :middle-center 5
    :middle-right  6
    :top-left      7
    :top-center    8
    :top-right     9
    2))
(defrecord ScriptInfo [play-res-x play-res-y scaled-border-and-shadow? alignment]
  AssSection
  (print-ass-section [this]
    (format "[Script Info]
; Script generated by FFmpeg/Lavc58.91.100
ScriptType: v4.00+
PlayResX: %d
PlayResY: %d
ScaledBorderAndShadow: %s

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Default,Arial,20,&H0000ff,&H000000,&Hffffff,&H000000,0,0,0,0,100,100,0,0,1,1,0,%d,10,10,10,0"
            play-res-x play-res-y (if scaled-border-and-shadow? "yes" "no") (alignment-> alignment))))

(defn create-script-info-section
  [& {:keys [play-res-x play-res-y scaled-border-and-shadow? alignment]
      :or {play-res-x 384 play-res-y 288 scaled-border-and-shadow? true alignment :bottom-center}}]
  (->ScriptInfo play-res-x play-res-y scaled-border-and-shadow? alignment))

(defn ms->lyrics-timing [ms]
  (let [total-s (long (/ ms 1000))
        total-m (long (/ total-s 60))
        total-h (long (/ ms (* 1000.0 60 60)))
        subsec (mod (long (/ ms 10)) 100)]
    (format "%d:%02d:%02d.%02d"
            (mod total-h 24)
            (mod total-m 60)
            (mod total-s 60)
            subsec)))

(defn lyrics-ass-events [song]
  (for [[f end] (map vector
                     (:frames song)
                     (concat
                      (map :offset (rest (:frames song)))
                      '(nil)))
        :let    [evt-intervals (-> f
                                   :events
                                   (->>
                                    (map :offset))
                                   (concat 
                                    [(- (if-not (nil? end)
                                          end
                                          (+ (p/get-offset f) 1000))
                                        (p/get-offset f))])
                                   (->> (partition 2 1))) 
                 start (ms->lyrics-timing (- (p/get-offset f) 1000))
                 end (ms->lyrics-timing (if-not (nil? end) end (+ (p/get-offset f) 1000)))
                 evt-lengths  (map (comp int (partial * 0.1) (fn [arg] (apply - arg)) reverse) evt-intervals)
                 evt-lengths (concat [100] evt-lengths)
                 text (apply str (for [[evt evt-len] (map vector
                                                          (concat [(le/create-lyrics-event :text "... ")]
                                                                  (:events f))
                                                          evt-lengths)]
                                   (str "{\\K" evt-len "\\fad(200,200)}" (p/get-text evt))))]]
    {:start start
     :end   end
     :text  text}))

(defrecord EventsSection [song]
  AssSection
  (print-ass-section [this]
    (apply str
           "

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
"
           (join "\n"
                 (for [{:keys [start end text]} (lyrics-ass-events song)]
                   (format "Dialogue: 0,%s,%s,Default,,0,0,0,,%s" start end text))))))

(defn ^:export ass-string
  "Returns a string with the lyrics in ASS subtitle format and the specified titles alignment"
  [song & {:keys [alignment] :or {alignment :middle-center}}]
  (str (print-ass-section (create-script-info-section :alignment alignment))
       (print-ass-section (->EventsSection song))))
