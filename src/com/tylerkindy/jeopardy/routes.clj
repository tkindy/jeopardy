(ns com.tylerkindy.jeopardy.routes
  (:require [compojure.core :refer [defroutes GET]]
            [compojure.route :refer [not-found resources]]
            [com.tylerkindy.jeopardy.home :refer [home]]))

(defroutes routes
  (GET "/" [] {:status 200
               :headers {"Content-Type" "text/html"}
               :body (home)})
  (resources "/public")
  (not-found "Not found"))