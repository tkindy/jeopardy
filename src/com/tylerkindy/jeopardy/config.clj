(ns com.tylerkindy.jeopardy.config
  (:require [clojure.string :as str]
            [dotenv :refer [env]]))

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
    {:http {:session-secret (my-env "HTTP_SESSION_SECRET")
            :port (parse-long (my-env "HTTP_PORT" "80"))}
     :db {:host (my-env "DB_HOST" "localhost")
          :dbname (my-env "DB_NAME")
          :user (my-env "DB_USER")
          :password (my-env "DB_PASSWORD")
          :migrate-on-startup? (parse-boolean (my-env "DB_MIGRATE_ON_STARTUP" "true"))}
     :player-id-spot (some-> (my-env "PLAYER_ID_SPOT" nil)
                             keyword)}))
