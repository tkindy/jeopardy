(ns com.tylerkindy.jeopardy.games
  (:require [compojure.core :refer [defroutes context POST GET]]
            [com.tylerkindy.jeopardy.db.core :refer [ds]]
            [com.tylerkindy.jeopardy.db.games :refer [insert-game get-game]]
            [hiccup.page :refer [html5]]))


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

(defn create-game []
  (let [id (generate-game-id)]
    (insert-game ds {:id id, :mode 0})
    {:status 303
     :headers {"Location" (str "/games/" id)}}))

(defn endless-page [game]
  (html5
   {:lang :en}
   [:body
    [:p "Hi!"]]))

(defn game-page [{:keys [mode] :as game}]
  (case mode
    0 (endless-page game)
    (throw (RuntimeException. (str "Unknown mode: " mode)))))

(defn game-page-response [game-id]
  (let [game (get-game ds {:id game-id})]
    (if game
      {:status 200
       :headers {"Content-Type" "text/html"}
       :body (game-page game)}
      {:status 404
       :headers {"Content-Type" "text/html"}
       :body "<p>Game not found</p>"})))

(defroutes game-routes
  (context "/games" []
    (POST "/" [] (create-game))
    (GET "/:game-id" [game-id] (game-page-response game-id))))
