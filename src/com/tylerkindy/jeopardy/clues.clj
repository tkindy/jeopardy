(ns com.tylerkindy.jeopardy.clues
  (:require [clojure.string :as str]
            [com.tylerkindy.jeopardy.answer :refer [char-pairs normalize-answer]]
            [next.jdbc :refer [execute! get-datasource]]
            [next.jdbc.result-set :refer [as-unqualified-kebab-maps]])
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

(def random-clues-q
  (str/join "\n" ["select"
                  "  ca.name as category,"
                  "  g.airdate,"
                  "  cl.question,"
                  "  cl.answer,"
                  "  cl.value"
                  "from clues as cl"
                  "join categories as ca on cl.category_id = ca.id"
                  "join games as g on cl.game_id = g.id"
                  "order by random()"
                  "limit ?"]))

(defn random-clues [n]
  (->> (execute! ds [random-clues-q n]
                 {:builder-fn as-unqualified-kebab-maps})
       (filter valid-clue?)
       (map clean-clue)))

(defn random-clue []
  (first (random-clues 10)))
