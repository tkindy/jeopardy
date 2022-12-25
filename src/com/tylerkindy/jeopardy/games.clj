(ns com.tylerkindy.jeopardy.games
  (:require [compojure.core :refer [defroutes context POST GET]]
            [com.tylerkindy.jeopardy.db.core :refer [ds]]
            [com.tylerkindy.jeopardy.db.games :refer [insert-game get-game]]
            [hiccup.page :refer [html5]]
            [ring.util.anti-forgery :refer [anti-forgery-field]]
            [com.tylerkindy.jeopardy.players :refer [player-routes]]
            [com.tylerkindy.jeopardy.db.players :refer [get-player]]
            [com.tylerkindy.jeopardy.db.endless-clues :refer [insert-clue get-current-clue]]))


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

(defn render-clue [{:keys [question]}]
  [:p question])

(defn render-no-clue []
  [:i "No question yet"])

(defn endless-logged-in-page [{game-id :id} req]
  (let [clue (get-current-clue ds {:game-id game-id})]
    (html5
     {:lang :en}
     [:body
      [:div.clue
       (if clue
         (render-clue clue)
         (render-no-clue))]])))

(defn endless-logged-in [game req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (endless-logged-in-page game req)})

(defn endless-anon-page [{game-id :id}]
  (html5
   {:lang :en}
   [:body
    [:form {:method :post
            :action (str "/games/" game-id "/players")}
     [:label {:for "name"} "Username"]
     [:input {:id "name" :name "name" :type :text
              :minlength 1 :maxlength 24}]
     (anti-forgery-field)
     [:button "Join game"]]]))

(defn endless-anon [game]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (endless-anon-page game)})

(defn endless-response [game req]
  (let [player-id (get-in req [:session :id])]
    (if (get-player ds {:id player-id, :game-id (:id game)})
      (endless-logged-in game req)
      (endless-anon game))))

(defn found-game-response [{:keys [mode] :as game} req]
  (case mode
    0 (endless-response game req)
    (throw (RuntimeException. (str "Unknown mode: " mode)))))

(defn game-page-response [req]
  (let [id (get-in req [:params :game-id])
        game (get-game ds {:id id})]
    (if game
      (found-game-response game req)
      {:status 404
       :headers {"Content-Type" "text/html"}
       :body "<p>Game not found</p>"})))

(defroutes game-routes
  (context "/games" []
    (POST "/" [] (create-game))
    (context "/:game-id" []
      (GET "/" req (game-page-response req))
      player-routes)))
