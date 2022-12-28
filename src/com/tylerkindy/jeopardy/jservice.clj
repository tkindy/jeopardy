(ns com.tylerkindy.jeopardy.jservice
  (:require [clj-http.client :as http]
            [clojure.string :as str]
            [com.tylerkindy.jeopardy.answer :refer [normalize-answer]]))

(defn valid-clue? [{:keys [question value]}]
  (let [question (str/lower-case question)]
    (and value
         (not (str/includes? question "seen here"))
         (not (str/includes? question "heard here")))))

(defn random-clues [n]
  (->> (http/get (str "https://jservice.io/api/random?count=" n)
                 {:accept :json, :as :json})
       :body
       (filter valid-clue?)
       (map (fn [clue] (update clue :answer normalize-answer)))))

(defn random-clue []
  (first (random-clues 10)))
