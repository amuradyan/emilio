(ns emilio.core
  (:gen-class)
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as clojure.string]
            [pleajure.core :as plj]
            [ring.adapter.jetty :as jetty])
  (:import [org.telegram.telegrambots.bots TelegramLongPollingBot]
           [org.telegram.telegrambots.meta TelegramBotsApi]
           [org.telegram.telegrambots.meta.api.methods.send SendMessage]
           [org.telegram.telegrambots.meta.api.objects Update]
           [org.telegram.telegrambots.meta.exceptions TelegramApiException]
           [org.telegram.telegrambots.updatesreceivers DefaultBotSession]))

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

(def telegram-bot-token
  (plj/get-at config [:telegram-bot-token]))

(defn respond-with [bot ^Long who ^String what]
  (let [sm (-> (SendMessage/builder)
               (.chatId (str who))
               (.text what)
               (.build))]
    (try
      (.execute bot sm)
      (catch TelegramApiException e
        (throw (RuntimeException. e))))))

(declare communicate)

(def Emilio
  (proxy [TelegramLongPollingBot] []
    (getBotUsername []
      "Emilio Hernandez-Azatyan")

    (getBotToken []
      telegram-bot-token)

    (onUpdateReceived [^Update update]
      (let [msg (.getMessage update)
            user (.getFrom msg)]
        (respond-with this (.getId user) (communicate (.getText msg)))))))

(defn query-the-assistant [message, thread-id, api-key]
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

(defn communicate [message]
  (let [_ (query-the-assistant message thread-id api-key)
        raw-response (demand-an-answer thread-id assistant-id api-key)]
    (process-response raw-response)))

(defn emilio [request]
  (case (request :request-method)
    :get (let [message (request :query-string)
               answer (communicate message)]
           {:status 200
            :headers {"Content-Type" "text/html"}
            :body answer})
    {:status 401
     :headers {"Content-Type" "text/html"}
     :body "Forbidden"}))

(defn run-emilio []
  (try
    (let [bots-api (TelegramBotsApi. DefaultBotSession)]
      (.registerBot bots-api Emilio)
      (println "Bot successfully registered!"))
    (catch TelegramApiException e
      (println "Error registering bot:" (.getMessage e)))))

(defn -main
  [& _]
  (run-emilio)
  (jetty/run-jetty emilio {:port port}))

