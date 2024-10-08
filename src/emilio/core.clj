(ns emilio.core
  (:require [clj-http.client :as client]
            [ring.adapter.jetty :as jetty]))

(defn emilio [request]
  (case (request :request-method)
    :get (let [query (request :query-string)]
           (client/get)
           {:status 200
            :headers {"Content-Type" "text/html"}
            :body (str "You asked " query)})
    {:status 401
     :headers {"Content-Type" "text/html"}
     :body "Forbidden"}))

(defn -main
  [& _]
  (jetty/run-jetty emilio {:port 3000}))
