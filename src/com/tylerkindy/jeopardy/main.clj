(ns com.tylerkindy.jeopardy.main
  (:require [mount.core :refer [defstate] :as mount]
            [org.httpkit.server :refer [run-server server-stop!]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.session.cookie :refer [cookie-store]]
            [com.tylerkindy.jeopardy.routes :refer [routes]]
            [com.tylerkindy.jeopardy.config :refer [config]]
            [com.tylerkindy.jeopardy.db.migrations :refer [migrate]]
            [com.tylerkindy.jeopardy.prep :refer [prep-question-db]])
  (:gen-class))

(defn parse-session-secret [secret]
  (-> (java.util.HexFormat/of)
      (.parseHex secret)))

(defstate app-settings
  :start (-> site-defaults
             (assoc-in [:session :store]
                       (cookie-store {:key (parse-session-secret
                                            (get-in @config [:http :session-secret]))}))
             (assoc-in [:session :cookie-name] "jeopardy-session")
             (assoc-in [:session :cookie-attrs :max-age] (* 10 365 24 60 60))))

(defn start-server [migrate?]
  (when migrate?
    (migrate))

  (run-server (wrap-defaults routes app-settings)
              {:port (get-in @config [:http :port])
               :legacy-return-value? false}))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defstate server
  :start (start-server (get-in @config [:db :migrate-on-startup?]))
  :stop (server-stop! server))

(defn -main [& args]
  (prep-question-db)
  (mount/start-with-args {:cli-args args}))
