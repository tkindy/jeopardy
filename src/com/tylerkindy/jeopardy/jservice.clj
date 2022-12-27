(ns com.tylerkindy.jeopardy.jservice
  (:require [clj-http.client :as http]
            [clojure.string :as str]))

(defn valid-clue? [{:keys [question value]}]
  (let [question (str/lower-case question)]
    (and value
         (not (str/includes? question "seen here"))
         (not (str/includes? question "heard here")))))

(defn random-clue []
  (->> (http/get "https://jservice.io/api/random?count=10"
                 {:accept :json, :as :json})
       :body
       (filter valid-clue?)
       first))
