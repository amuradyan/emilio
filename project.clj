(defproject emilio "0.1.0-SNAPSHOT"
  :description "Emilio Hernandez-Azatyan is a Telegram bot over OpenAI's assistant"
  :url "https://github.com/amuradyan/emilio"
  :license {:name         "Zero-Clause BSD"
            :url          "https://opensource.org/license/0bsd"
            :distribution :repo}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [clj-http "3.13.0"]
                 [org.clojure/data.json "2.5.0"]
                 [am.dekanat/pleajure "0.1.0"]
                 [ring/ring-core "1.8.2"]
                 [org.telegram/telegrambots "6.9.7.1"]
                 [ring/ring-jetty-adapter "1.8.2"]]
  :repl-options {:init-ns emilio.core}
  :profiles {:uberjar {:aot :all}}
  :main ^:skip-aot emilio.core)
