(defproject clj-karaoke-lyrics "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/data.json "0.2.6"]
                 [com.rpl/specter "1.1.2"]
                 [org.clojure/core.async "0.4.490"]
                 [org.clojure/tools.cli "0.4.2"]]
  :main ^:skip-aot clj-karaoke.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all #_[clj-karaoke.core clj-karaoke.karaoke clj-karaoke.lyrics]}})
