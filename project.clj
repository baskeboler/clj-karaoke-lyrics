(defproject baskeboler/clj-karaoke-lyrics "1.0.0-SNAPSHOT"
  :description "Rip karaoke lyrics from karaoke midi files"
  :url "http://github.com/baskeboler/clj-karaoke-lyrics.git"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/core.async "0.4.490"]
                 [org.clojure/tools.cli "0.4.2"]
                 [clj-cli-progress "0.1.0"]]
  :main ^:skip-aot clj-karaoke.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all #_[clj-karaoke.core clj-karaoke.karaoke clj-karaoke.lyrics]}}
  :repositories
  {"clojars" {:url "https://clojars.org/repo"
              :sign-releases false
              :username "baskeboler"
              :password :env/CLOJARS_TOKEN}
   "github" {:url "https://maven.pkg.github.com/baskeboler/cljs-karaoke-lyrics"
             :sign-releases false}}
  :deploy-repositories [["releases" :clojars] ["snapshots" :clojars]])
