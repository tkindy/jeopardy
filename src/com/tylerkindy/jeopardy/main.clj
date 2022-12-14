(ns com.tylerkindy.jeopardy.main
  (:require [org.httpkit.server :refer [run-server]]))

(defn app [req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body "Hello, World!"})

(defn -main []
  (run-server app {:port 8080}))
