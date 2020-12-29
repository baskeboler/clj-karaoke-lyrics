(ns clj-karaoke.utils)


(def ^:export generate-id (comp str gensym))

(defn ^:export  ensure-id [arg]
  (if-not (some? (:id arg))
    (assoc arg :id (generate-id))
    arg))

