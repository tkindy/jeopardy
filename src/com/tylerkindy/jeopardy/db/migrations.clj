(ns com.tylerkindy.jeopardy.db.migrations
  (:require [mount.core :refer [defstate]]
            [migratus.core :as migratus]
            [com.tylerkindy.jeopardy.config :refer [config]]))

(defstate migration-config
  :start {:store :database
          :db (-> (:db config)
                  (assoc :dbtype "postgresql"))})

(defn migrate []
  (migratus/migrate migration-config))

(comment (migrate))

(defn reset []
  (migratus/reset migration-config))

(comment (reset))

(defn create [name]
  (migratus/create migration-config name))
