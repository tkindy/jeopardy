(ns com.tylerkindy.jeopardy.main
  (:require [mount.core :refer [defstate] :as mount]
            [org.httpkit.server :refer [run-server as-channel send! server-stop!]]
            [com.tylerkindy.jeopardy.routes :refer [routes]]
            [hiccup.core :refer [html]]))

(defn app [req]
  (if-not (:websocket? req)
    (routes req)
    (as-channel req
                {:on-open (fn [ch]
                            (send! ch (html [:div#question "Loaded over WS"])))})))

(defn start-server []
  (run-server app {:port 8080
                   :legacy-return-value? false}))

(defstate server
  :start (start-server)
  :stop (server-stop! server))

(defn -main []
  (mount/start))
