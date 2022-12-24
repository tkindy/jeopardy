(ns com.tylerkindy.jeopardy.games
  (:require [compojure.core :refer [defroutes context POST GET]]
            [com.tylerkindy.jeopardy.db.core :refer [ds]]
            [com.tylerkindy.jeopardy.db.games :refer [insert-game]]))


(defn char-range [start end]
  (->> (range (int start) (inc (int end)))
       (map char)))
(def game-id-characters
  (->> [(char-range \A \Z) (char-range \0 \9)]
       (apply concat)
       (into [])))
(defn generate-game-id []
  (->> (range 6)
       (map (fn [_] (rand-nth game-id-characters)))
       (apply str)))

; modes: 0 -> endless

(defn create-game []
  (let [id (generate-game-id)]
    (insert-game ds {:id id, :mode 0})
    {:status 303
     :headers {"Location" (str "/games/" id)}}))

(defn game-page-response [game-id]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (str "<p>Game: " game-id "</p>")})

(defroutes game-routes
  (context "/games" []
    (POST "/" [] (create-game))
    (GET "/:game-id" [game-id] (game-page-response game-id))))
