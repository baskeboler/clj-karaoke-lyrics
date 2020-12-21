(ns clj-karaoke.common-specs
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]))


(s/def ::zero-pos (s/or :zero zero? :pos pos?))

(s/valid? ::zero-pos 0)
(s/valid? ::zero-pos 1)
(s/valid? ::zero-pos 0.12) 
(s/valid? ::zero-pos -1)

