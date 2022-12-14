(ns com.tylerkindy.jeopardy.main
  (:require [org.httpkit.server :refer [run-server as-channel send!]]))

(defn app [req]
  (if-not (:websocket? req)
    {:status 200, :body "Hello, World!"}
    (as-channel req
                {:on-receive (fn [ch message] (send! ch message))})))

(defn -main []
  (run-server app {:port 8080}))
