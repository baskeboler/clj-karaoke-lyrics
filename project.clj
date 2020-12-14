(defproject baskeboler/clj-karaoke-lyrics "1.0.2"
  :description "Rip karaoke lyrics from karaoke midi files"
  :url "http://github.com/baskeboler/clj-karaoke-lyrics.git"
  :scm {:name "git" 
  		:url  "http://github.com/baskeboler/clj-karaoke-lyrics.git"}
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/core.async "0.4.490"]
                 [org.clojure/tools.cli "0.4.2"]
                 [tick "0.4.27-alpha"]
                 [time-literals "0.1.4"]
                 [clj-cli-progress "0.1.0"]]
  :main ^:skip-aot clj-karaoke.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot [clj-karaoke.core clj-karaoke.karaoke clj-karaoke.lyrics
                             clj-karaoke.lyrics-event clj-karaoke.lyrics-frame 
                             clj-karaoke.protocols clj-karaoke.midi
                             clj-karaoke.song-data]}}
  :repositories
  {"clojars" {:url "https://clojars.org/repo"
              :sign-releases false
              :username "baskeboler"
              :password :env/CLOJARS_TOKEN}
   "github" {:url "https://maven.pkg.github.com/baskeboler/cljs-karaoke-lyrics"
             :sign-releases false}}
  :deploy-repositories [["releases" :clojars] ["snapshots" :clojars]])
