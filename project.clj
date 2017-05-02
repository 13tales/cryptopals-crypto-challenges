(defproject cryptopals-crypto-challenges "0.2.0-SNAPSHOT"
  :description "The Cryptopals crypto challenges"
  :url "https://github.com/KingMob/cryptopals-crypto-challenges"

  :dependencies [[org.clojure/clojure "1.9.0-alpha15"]
                 [commons-codec "1.10"]
                 [com.taoensso/tufte "1.1.1"]
                 [medley "0.8.4"]
                 [byte-streams "0.2.2"]]
  :profiles {:dev {:dependencies [[org.clojure/test.check "0.9.0"]
                                  [alembic "0.3.2"]]}}
  :jvm-opts ^:replace [])
