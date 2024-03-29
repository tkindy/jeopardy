(ns com.tylerkindy.jeopardy.jservice
  (:require [clj-http.client :as http]
            [clojure.string :as str]
            [com.tylerkindy.jeopardy.answer :refer [char-pairs normalize-answer]])
  (:import [java.time ZonedDateTime]))

(defn valid-answer? [answer]
  (seq (char-pairs answer)))

(defn valid-clue? [{:keys [question value answer]}]
  (let [question (str/lower-case question)]
    (and value
         (not (str/includes? question "seen here"))
         (not (str/includes? question "shown here"))
         (not (str/includes? question "heard here"))
         (valid-answer? (normalize-answer answer)))))

(defn clean-clue [clue]
  (-> clue
      (update :airdate #(-> %
                            ZonedDateTime/parse
                            .toLocalDate))))

(defn random-clues [n]
  (->> (http/get (str "https://jservice.io/api/random?count=" n)
                 {:accept :json, :as :json})
       :body
       (filter valid-clue?)
       (map clean-clue)))

(defn random-clue []
  (first (random-clues 10)))
