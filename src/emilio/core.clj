(ns emilio.core
  (:require [clj-http.client :as client]
            [pleajure.core :as plj]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.string :as clojure.string]
            [ring.adapter.jetty :as jetty]))

(def config
  (plj/parse-from-file "resources/configs.plj"))

(def port
  (plj/get-at config [:port]))

(def api-key
  (plj/get-at config [:api-key]))

(def thread-id
  (plj/get-at config [:thread-id]))

(def assistant-id
  (plj/get-at config [:assistant-id]))

(defn ask-a-question [message, thread-id, api-key]
  (let [url (str "https://api.openai.com/v1/threads/" thread-id "/messages")
        token (str "Bearer " api-key)]
    (client/post url
                 {:headers {"Content-Type" "application/json"
                            "Authorization" token
                            "OpenAI-Beta" "assistants=v2"}
                  :body (str "{\"role\": \"user\", \"content\": \"" message "\"}")})))

(defn demand-an-answer [thread-id, assistant-id, api-key]
  (let [url (str "https://api.openai.com/v1/threads/" thread-id "/runs")
        token (str "Bearer " api-key)]
    (client/post url
                 {:headers {"Content-Type" "application/json"
                            "Authorization" token
                            "OpenAI-Beta" "assistants=v2"}
                  :body (str "{\"assistant_id\": \"" assistant-id "\", \"stream\": true}")
                  :as :stream})))

(defn message-completed-event? [event]
  (clojure.string/starts-with? event "event: thread.message.completed"))

(defn data-package-message? [message]
  (clojure.string/starts-with? message "data:"))

(defn extract-answer [message]
  (get-in
   (first (get-in
           (json/read-str (clojure.string/replace message "data: " ""))
           ["content"]
           "keys not found"))
   ["text", "value"]
   "keys not found"))

(defn stream-filter
  ([lines]
   (if (empty? lines)
     ""
     (stream-filter lines :find-event "")))

  ([lines mode answer]
   (cond
     (empty? lines) answer
     :else (case mode
             :find-event (cond
                           (message-completed-event? (first lines)) (stream-filter (rest lines) :get-data answer)
                           :else (stream-filter (rest lines) mode answer))
             :get-data (cond
                         (data-package-message? (first lines)) (extract-answer (first lines))
                         :else (stream-filter (rest lines) mode answer))))))


(defn process-response [raw-response]
  (with-open [reader (io/reader (raw-response :body))]
    (stream-filter (line-seq reader))))

(defn emilio [request]
  (case (request :request-method)
    :get (let [query (request :query-string)
               _ (ask-a-question query thread-id api-key)
               raw-response (demand-an-answer thread-id assistant-id api-key)
               answer (process-response raw-response)]
           {:status 200
            :headers {"Content-Type" "text/html"}
            :body answer})
    {:status 401
     :headers {"Content-Type" "text/html"}
     :body "Forbidden"}))

(defn -main
  [& _]
  (jetty/run-jetty emilio {:port port}))

