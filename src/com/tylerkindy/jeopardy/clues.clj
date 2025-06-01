(ns com.tylerkindy.jeopardy.clues
  (:require [clojure.set :refer [rename-keys]]
            [clojure.string :as str]
            [com.tylerkindy.jeopardy.answer :refer [char-pairs normalize-answer]]
            [next.jdbc :refer [get-datasource]]
            [com.tylerkindy.jeopardy.db.library :refer [get-random-clues
                                                        clue-category-info
                                                        get-next-category-clue
                                                        get-random-category
                                                        get-random-game-with-category]]
            [com.tylerkindy.jeopardy.config :refer [config]])
  (:import [java.time LocalDate]))

(def ds (get-datasource {:dbtype "sqlite"
                         :dbname (str (:temp-dir @config)
                                      "/jeopardy.db")}))

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

(defn pick-from-random-category []
  (let [{category-id :id} (get-random-category ds)
        {:keys [game-id]} (get-random-game-with-category ds {:category-id category-id})]
    (get-next-category-clue ds {:game-id game-id
                                :category-id category-id
                                :last-value -1})))

(defn pick-next-category-clue [clue-id]
  (if clue-id
    (let [category-info (-> (clue-category-info ds {:clue-id clue-id})
                            (rename-keys {:value :last-value}))
          next-clue (get-next-category-clue ds category-info)]
      (if next-clue
        next-clue
        (pick-from-random-category)))
    (pick-from-random-category)))

(defn next-category-clue [{:keys [lib-clue-id]}]
  (loop [clue (pick-next-category-clue lib-clue-id)]
    (if (valid-clue? clue)
      (clean-clue clue)
      (recur (pick-next-category-clue (:lib-clue-id clue))))))
