(ns com.tylerkindy.jeopardy.jservice
  (:require [clj-http.client :as http]
            [clojure.string :as str]))

(defn random-clue []
  (->> (http/get "https://jservice.io/api/random?count=10"
                 {:accept :json, :as :json})
       :body
       (filter #(-> %
                    str/lower-case
                    (str/includes? "seen here")
                    not))
       first))
