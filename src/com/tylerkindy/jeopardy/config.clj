(ns com.tylerkindy.jeopardy.config
  (:require [clojure.string :as str]
            [dotenv :refer [env]]
            [babashka.fs :as fs])
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

(defn- my-env
  ([key]
   (my-env key (fn [] (throw (RuntimeException. (str "No or blank value for environment variable " key))))))
  ([key default]
   (let [value (env key)]
     (if (or (nil? value) (str/blank? value))
       (if (fn? default)
         (default)
         default)
       value))))

(def config
  (delay
    (let [temp-dir (Files/createTempDirectory "jeopardy"
                                              (make-array FileAttribute 0))]
      (.addShutdownHook (Runtime/getRuntime)
                        (Thread. ^Runnable
                         (fn []
                           (println "Shutting down")
                           (fs/delete-tree temp-dir))))

      {:http {:session-secret (my-env "HTTP_SESSION_SECRET")
              :port (parse-long (my-env "HTTP_PORT" "80"))}
       :db {:host (my-env "DB_HOST" "localhost")
            :port (parse-long (my-env "DB_PORT" "5432"))
            :dbname (my-env "DB_NAME")
            :user (my-env "DB_USER")
            :password (my-env "DB_PASSWORD")
            :migrate-on-startup? (parse-boolean (my-env "DB_MIGRATE_ON_STARTUP" "true"))}
       :player-id-spot (some-> (my-env "PLAYER_ID_SPOT" nil)
                               keyword)
       :question-db {:key (my-env "QUESTION_DB_KEY" nil)}
       :temp-dir (.toString temp-dir)})))
