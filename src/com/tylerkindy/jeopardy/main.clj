(ns com.tylerkindy.jeopardy.main
  (:require [mount.core :refer [defstate] :as mount]
            [org.httpkit.server :refer [run-server as-channel send! server-stop!]]))

(defn app [req]
  (if-not (:websocket? req)
    {:status 200, :body "Hello, World!"}
    (as-channel req
                {:on-receive (fn [ch message] (send! ch message))})))

(defn start-server []
  (run-server app {:port 8080
                   :legacy-return-value? false}))

(defstate server
  :start (start-server)
  :stop (server-stop! server))

(defn -main []
  (mount/start))
