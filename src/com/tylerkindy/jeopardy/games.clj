(ns com.tylerkindy.jeopardy.games
  (:require [compojure.core :refer [defroutes context POST GET]]))

(defn create-game [{:keys [params]}]
  (println params)
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body "<p>Thanks!</p>"})

(defn game-page-response [game-id]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (str "<p>Game: " game-id "</p>")})

(defroutes game-routes
  (context "/games" []
    (POST "/" request (create-game request))
    (GET "/:game-id" [game-id] (game-page-response game-id))))
