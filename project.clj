(defproject baskeboler/clj-karaoke-lyrics "1.0.6-SNAPSHOT"
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
                 [org.clojure/test.check "1.1.0"]
                 [tick "0.4.27-alpha"]
                 [metosin/spec-tools "0.10.4"]
                 [spec-provider "0.4.14"]
                 [criterium "0.4.6"]
                 [time-literals "0.1.4"]
                 [clj-cli-progress "0.1.0"]]
  :main  clj-karaoke.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot [
                             clj-karaoke.core
                             ]}}
  :repositories
  {"clojars" {:url "https://clojars.org/repo"
              :sign-releases false
              :username "baskeboler"
              :password :env/CLOJARS_TOKEN}
   "github" {:url "https://maven.pkg.github.com/baskeboler/cljs-karaoke-lyrics"
             :sign-releases false}}
  :deploy-repositories [["releases" :clojars] ["snapshots" :clojars]])
