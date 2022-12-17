(ns com.tylerkindy.jeopardy.main
  (:require [mount.core :refer [defstate] :as mount]
            [org.httpkit.server :refer [run-server as-channel send! server-stop!]]
            [com.tylerkindy.jeopardy.routes :refer [routes]]
            [hiccup.core :refer [html]]
            [com.tylerkindy.jeopardy.jservice :refer [random-clue]]
            [cheshire.core :as json]
            [clojure.string :as str]
            [com.tylerkindy.jeopardy.home :refer [answer-form]]))

(defonce clue (atom nil))

(defn render-question [{:keys [category question]}]
  (html
   [:div#clue
    [:p.category (:title category)]
    [:p.question question]]))

(defn send-clue [ch]
  (send! ch (render-question @clue)))

(defn new-clue [ch]
  (let [new (random-clue)]
    (reset! clue new)
    (send-clue ch)))

(defn check-answer [{:keys [answer]}]
  (let [clue @clue
        response (if (= (str/lower-case (:answer clue))
                        (str/lower-case answer))
                   "That's right!"
                   "Incorrect")]
    (html (answer-form response))))

(defn receive-message [ch message]
  (let [{:keys [type] :as message} (json/parse-string message keyword)]
    (case (keyword type)
      :answer (send! ch (check-answer message))
      :new-question (new-clue ch))))

(defn app [req]
  (if-not (:websocket? req)
    (routes req)
    (as-channel req
                {:on-open new-clue
                 :on-receive receive-message})))

(defn start-server []
  (run-server app {:port 8080
                   :legacy-return-value? false}))

(defstate server
  :start (start-server)
  :stop (server-stop! server))

(defn -main []
  (mount/start))
