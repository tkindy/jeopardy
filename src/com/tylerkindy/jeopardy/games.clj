(ns com.tylerkindy.jeopardy.games
  (:require [compojure.core :refer [defroutes context POST]]))

(defn create-game [{:keys [params]}]
  (println params)
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body "<p>Thanks!</p>"})

(defroutes game-routes
  (context "/games" []
    (POST "/" request (create-game request))))
