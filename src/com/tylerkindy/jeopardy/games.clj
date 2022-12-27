(ns com.tylerkindy.jeopardy.games
  (:require [compojure.core :refer [defroutes context POST GET]]
            [com.tylerkindy.jeopardy.db.core :refer [ds]]
            [com.tylerkindy.jeopardy.db.games :refer [insert-game get-game]]
            [hiccup.core :refer [html]]
            [ring.util.anti-forgery :refer [anti-forgery-field]]
            [com.tylerkindy.jeopardy.players :refer [player-routes]]
            [com.tylerkindy.jeopardy.db.players :refer [get-player list-players update-score]]
            [com.tylerkindy.jeopardy.db.endless-clues :refer [insert-clue get-current-clue]]
            [org.httpkit.server :refer [as-channel send!]]
            [com.tylerkindy.jeopardy.common :refer [scripts page]]
            [cheshire.core :as json]
            [com.tylerkindy.jeopardy.jservice :refer [random-clue]]
            [clojure.core.match :refer [match]]))

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

(defonce live-games (atom {}))

(defn who-view [game-id]
  (let [player-ids (or (->> (get-in @live-games [game-id :players])
                            keys
                            set)
                       #{})
        players (->> (list-players ds {:game-id game-id})
                     (filter (fn [{:keys [id]}] (player-ids id)))
                     (sort-by :score)
                     reverse)]
    [:div#who
     [:p "Players"]
     [:ul
      (map (fn [{:keys [name score]}]
             [:li (format "%s: $%,d" name score)])
           players)]]))

(defn send-all! [game-id message]
  (let [players (-> (get-in @live-games [game-id :players]))]
    (doseq [[player-id channel] players]
      (if (fn? message)
        (send! channel (message player-id))
        (send! channel message)))))

(defn connect-player [game-id player-id ch]
  (swap! live-games assoc-in [game-id :players player-id] ch)
  (send-all! game-id (html (who-view game-id))))

(defn disconnect-player [game-id player-id]
  (swap! live-games update-in [game-id :players] dissoc player-id)
  (send-all! game-id (html (who-view game-id))))

(defn render-clue [{:keys [category question value]}]
  (list
   [:p [:i (str category ", $" value)]]
   [:p question]))

(defn render-no-clue []
  [:i "No question yet"])

(defn clue-view [clue]
  [:div#clue
   (if clue
     (render-clue clue)
     (render-no-clue))])

(defn new-clue [game-id]
  (let [clue (-> (random-clue)
                 (select-keys [:category :question :answer :value])
                 (update :category :title)
                 (assoc :game-id game-id))]
    (insert-clue ds clue)
    (swap! live-games assoc-in [game-id :state] {:name :open-for-answers})
    (send-all! game-id (html (clue-view clue)))))

(defn buzzing-form [game-id player-id]
  (let [live-game (get @live-games game-id)
        state (get-in live-game [:state :name])
        buzzed-in-id (get-in live-game [:state :buzzed-in])
        [type button-text] (match [state buzzed-in-id]
                             [:answering player-id] [:answer "Submit"]
                             :else                  [:buzz-in "Buzz in"])]
    [:form {:ws-send ""}
     [:input {:name :type, :value type, :hidden ""}]
     (when (= type :answer)
       [:input {:type :text, :name :answer, :autofocus ""}])
     [:button button-text]]))

(defn buzzing-view [game-id player-id]
  (let [buzzed-in-id (get-in @live-games [game-id :state :buzzed-in])
        buzzed-in-player (get-player ds {:game-id game-id, :id buzzed-in-id})
        message (if buzzed-in-player
                  (str (:name buzzed-in-player) " buzzed in")
                  "No one is buzzed in")]
    [:div#buzzing
     [:p [:i message]]
     (buzzing-form game-id player-id)]))

(defn buzz-in [game-id player-id]
  (swap! live-games
         (fn [live-games]
           (let [state (get-in live-games [game-id :state])]
             (match [(:name state) (:buzzed-in state)]
               [:open-for-answers nil] (assoc-in live-games
                                                 [game-id :state]
                                                 {:name :answering
                                                  :buzzed-in player-id})
               :else live-games))))
  (send-all! game-id
             (fn [player-id]
               (html (buzzing-view game-id player-id)))))

(defn endless-container [game-id player-id]
  (let [clue (get-current-clue ds {:game-id game-id})]
    [:div#endless
     (who-view game-id)
     (clue-view clue)
     (buzzing-view game-id player-id)
     [:form {:ws-send ""}
      [:input {:name :type, :value :new-clue, :hidden ""}]
      [:button "New question"]]]))

(defn right-answer [game-id player-id value]
  (let [{:keys [score]} (get-player ds {:id player-id, :game-id game-id})]
    (update-score ds {:id player-id, :score (+ score value)})))

(defn check-answer [game-id player-id {guess :answer}]
  (let [buzzed-in-id (get-in @live-games [game-id :state :buzzed-in])]
    (when (= buzzed-in-id player-id)
      (let [{:keys [answer value]} (get-current-clue ds {:game-id game-id})]
        (when (= answer guess)
          (right-answer game-id player-id value)))
      (swap! live-games assoc-in [game-id :state] {:name :open-for-answers})
      (send-all! game-id
                 (fn [player-id]
                   (html (endless-container game-id player-id)))))))

(defn receive-message [game-id player-id message]
  (let [{:keys [type] :as message} (json/parse-string message keyword)]
    (case (keyword type)
      :new-clue (new-clue game-id)
      :buzz-in (buzz-in game-id player-id)
      :answer (check-answer game-id player-id message))))

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
