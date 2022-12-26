(ns com.tylerkindy.jeopardy.games
  (:require [compojure.core :refer [defroutes context POST GET]]
            [com.tylerkindy.jeopardy.db.core :refer [ds]]
            [com.tylerkindy.jeopardy.db.games :refer [insert-game get-game]]
            [hiccup.core :refer [html]]
            [ring.util.anti-forgery :refer [anti-forgery-field]]
            [com.tylerkindy.jeopardy.players :refer [player-routes]]
            [com.tylerkindy.jeopardy.db.players :refer [get-player list-players]]
            [com.tylerkindy.jeopardy.db.endless-clues :refer [insert-clue get-current-clue]]
            [org.httpkit.server :refer [as-channel send!]]
            [com.tylerkindy.jeopardy.common :refer [scripts page]]
            [cheshire.core :as json]
            [com.tylerkindy.jeopardy.jservice :refer [random-clue]]))


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

(defonce game-states (atom {}))

(defn who-view [game-id]
  (let [player-ids (or (->> (get-in @game-states [game-id :players])
                            keys
                            set)
                       #{})
        player-names (->> (list-players ds {:game-id game-id})
                          (filter (fn [{:keys [id]}] (player-ids id)))
                          (map :name)
                          sort)]
    [:div#who
     [:p "Live players"]
     [:ul
      (map (fn [name] [:li name]) player-names)]]))

(defn send-all! [game-id message]
  (let [channels (-> (get-in @game-states [game-id :players])
                     vals)]
    (doseq [channel channels]
      (send! channel message))))

(defn connect-player [game-id player-id ch]
  (swap! game-states assoc-in [game-id :players player-id] ch)
  (send-all! game-id (html (who-view game-id))))

(defn disconnect-player [game-id player-id]
  (swap! game-states update-in [game-id :players] dissoc player-id)
  (send-all! game-id (html (who-view game-id))))

(defn render-clue [{:keys [question]}]
  [:p question])

(defn render-no-clue []
  [:i "No question yet"])

(defn clue-view [clue]
  [:div#clue
   (if clue
     (render-clue clue)
     (render-no-clue))])

(defn new-clue [game-id]
  (let [clue (-> (random-clue)
                 (select-keys [:question :answer :value])
                 (assoc :game-id game-id))]
    (insert-clue ds clue)
    (send-all! game-id (html (clue-view clue)))))

(defn receive-message [game-id player-id ch message]
  (let [{:keys [type] :as message} (json/parse-string message keyword)]
    (case (keyword type)
      :new-question (new-clue game-id))))

(defn game-websocket [req]
  (let [{:keys [game-id]} (:params req)
        {player-id :id} (:session req)]
    (if player-id
      (as-channel req
                  {:on-open (fn [ch] (connect-player game-id player-id ch))
                   :on-close (fn [_ _] (disconnect-player game-id player-id))
                   :on-receive (fn [ch message] (receive-message game-id player-id ch message))})
      {:status 400
       :headers {"Content-Type" "text/html"}
       :body "<p>You're not logged in</p>"})))

(defn endless-logged-in-page [{game-id :id} req]
  (let [clue (get-current-clue ds {:game-id game-id})]
    (page
     [:body {:hx-ext "ws", :ws-connect (str "/games/" game-id)}
      (who-view game-id)
      (clue-view clue)
      [:form {:ws-send ""}
       [:input {:name :type, :value :new-question, :hidden ""}]
       [:button "New question"]]

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
     [:label {:for "name"} "Set your username"]
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
