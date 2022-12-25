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

(defn endless-logged-in-page [game req]
  (html5
   {:lang :en}
   [:body
    [:p (str "You are user " (get-in req [:params :id]))]]))

(defn endless-logged-in [game req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (endless-logged-in-page game req)})

(defn endless-anon-page [game req]
  (html5
   {:lang :en}
   [:body
    [:p "You are logged out"]]))

(defn endless-anon [game req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (endless-anon-page game req)})

(defn endless-response [game req]
  (let [player-id (get-in req [:session :id])]
    (if player-id
      (endless-logged-in game req)
      (endless-anon game req))))

(defn found-game-response [{:keys [mode] :as game} req]
  (case mode
    0 (endless-response game req)
    (throw (RuntimeException. (str "Unknown mode: " mode)))))

(defn game-page-response [req]
  (let [id (get-in req [:params :id])
        game (get-game ds {:id id})]
    (if game
      (found-game-response game req)
      {:status 404
       :headers {"Content-Type" "text/html"}
       :body "<p>Game not found</p>"})))

(defroutes game-routes
  (context "/games" []
    (POST "/" [] (create-game))
    (GET "/:id" req (game-page-response req))))
