(defproject clj-karaoke "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 ;; [clj-http "3.9.1"]
                 ;; [cheshire "5.8.1"]
                 ;; [com.cerner/clara-rules "0.19.0"]
                 [com.rpl/specter "1.1.2"]
                 [fn-fx/fn-fx-javafx "0.5.0-SNAPSHOT"]
                 [org.clojure/core.async "0.4.490"]]
  :main ^:skip-aot clj-karaoke.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
