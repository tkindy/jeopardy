(ns com.tylerkindy.jeopardy.games
  (:require [com.tylerkindy.jeopardy.common :refer [scripts page]]
            [com.tylerkindy.jeopardy.db.core :refer [ds]]
            [com.tylerkindy.jeopardy.db.games :refer [insert-game get-game]]
            [com.tylerkindy.jeopardy.db.players :refer [get-player]]
            [com.tylerkindy.jeopardy.endless.incoming :refer [receive-message]]
            [com.tylerkindy.jeopardy.endless.live :refer [live-games send-all! setup-game-state!]]
            [com.tylerkindy.jeopardy.endless.views :refer [endless-container who-view]]
            [com.tylerkindy.jeopardy.players :refer [player-routes]]
            [compojure.core :refer [defroutes context POST GET]]
            [garden.core :refer [css]]
            [hiccup.core :refer [html]]
            [org.httpkit.server :refer [as-channel]]
            [ring.util.anti-forgery :refer [anti-forgery-field]]))

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

(defn connect-player [game-id player-id ch]
  (setup-game-state! game-id)
  (swap! live-games assoc-in [game-id :players player-id] ch)
  (send-all! game-id (html (who-view game-id))))

(defn disconnect-player [game-id player-id]
  (swap! live-games
         (fn [live-games]
           (let [live-games (update-in live-games [game-id :players] dissoc player-id)]
             (if (empty? (get-in live-games [game-id :players]))
               (dissoc live-games game-id)
               live-games))))
  (send-all! game-id (html (who-view game-id))))

(defn game-websocket [req]
  (let [{:keys [game-id]} (:params req)
        {player-id :id} (:session req)]
    (if player-id
      (as-channel req
                  {:on-open (fn [ch] (connect-player game-id player-id ch))
                   :on-close (fn [_ _] (disconnect-player game-id player-id))
                   :on-receive (fn [_ message] (receive-message game-id player-id message))})
      {:status 400
       :headers {"Content-Type" "text/html"}
       :body "<p>You're not logged in</p>"})))

(defn endless-logged-in-page [{game-id :id} req]
  (let [player-id (get-in req [:session :id])]
    (page
     (list
      [:style (css {:pretty-print? false}
                   [".wrong-guess" {:color :red}])])
     [:body {:hx-ext "ws", :ws-connect (str "/games/" game-id)}
      (endless-container game-id player-id)

      scripts])))

(defn endless-logged-in [game req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (endless-logged-in-page game req)})

(defn endless-anon-page [{game-id :id}]
  (page
   [:body
    [:form {:method :post
            :action (str "/games/" game-id "/players")}
     [:label {:for "name"} "Pick a username"]
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

(defn handle-game-request [req]
  (if (:websocket? req)
    (game-websocket req)
    (game-page-response req)))

(defroutes game-routes
  (context "/games" []
    (POST "/" [] (create-game))
    (context "/:game-id" []
      (GET "/" req (handle-game-request req))
      player-routes)))
