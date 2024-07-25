(ns com.tylerkindy.jeopardy.clues
  (:require [clojure.string :as str]
            [com.tylerkindy.jeopardy.answer :refer [char-pairs normalize-answer]]
            [next.jdbc :refer [get-datasource]]
            [com.tylerkindy.jeopardy.db.library :refer [get-random-clues]])
  (:import [java.time LocalDate]))

(def ds (get-datasource {:dbtype "sqlite"
                         :dbname "jeopardy.db"}))

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
      (update :airdate #(LocalDate/parse %))))

(defn random-clues [n]
  (->> (get-random-clues ds {:limit n})
       (filter valid-clue?)
       (map clean-clue)))

(defn random-clue []
  (first (random-clues 10)))
