(ns com.tylerkindy.jeopardy.routes
  (:require [compojure.core :refer [defroutes GET]]
            [compojure.route :refer [not-found resources]]
            [com.tylerkindy.jeopardy.home :refer [home]]
            [com.tylerkindy.jeopardy.test :refer [test-routes]]
            [com.tylerkindy.jeopardy.games :refer [game-routes]]))

(defroutes routes
  (GET "/" [] {:status 200
               :headers {"Content-Type" "text/html"}
               :body (home)})
  test-routes
  game-routes
  (resources "/public")
  (not-found "Not found"))
