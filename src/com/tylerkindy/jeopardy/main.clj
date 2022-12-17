(ns com.tylerkindy.jeopardy.main
  (:require [mount.core :refer [defstate] :as mount]
            [org.httpkit.server :refer [run-server as-channel send! server-stop!]]
            [com.tylerkindy.jeopardy.routes :refer [routes]]
            [hiccup.core :refer [html]]
            [com.tylerkindy.jeopardy.jservice :refer [random-clue]]))

(defonce clue (atom nil))

(defn render-question [{:keys [question]}]
  (html
   [:div#question question]))

(defn send-clue [ch]
  (send! ch (render-question @clue)))

(defn new-clue [ch]
  (let [new (random-clue)]
    (reset! clue new)
    (send-clue ch)))

(defn app [req]
  (if-not (:websocket? req)
    (routes req)
    (as-channel req
                {:on-open new-clue
                 :on-receive (fn [ch _] (new-clue ch))})))

(defn start-server []
  (run-server app {:port 8080
                   :legacy-return-value? false}))

(defstate server
  :start (start-server)
  :stop (server-stop! server))

(defn -main []
  (mount/start))
