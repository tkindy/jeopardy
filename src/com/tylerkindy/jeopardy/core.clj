(ns com.tylerkindy.jeopardy.core
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
            [reitit.ring :as rr]))

(def router
  (rr/router
   ["/" {:get (fn [_] {:status 200 :body "Hello, world!"})}]))

(def app
  (-> router
      rr/ring-handler
      (wrap-json-body {:keywords? true})
      wrap-json-response
      (wrap-defaults api-defaults)))

(defn start-server [join?]

  (run-jetty app {:port (Integer/parseInt (or (get (System/getenv) "PORT")
                                              "3000"))
                  :join? join?}))

(defonce server (atom nil))
(defn reload []
  (let [server @server]
    (when server (.stop server)))
  (reset! server (start-server false)))

(defn -main []
  (start-server true))
