(ns com.tylerkindy.jeopardy.games
  (:require [com.tylerkindy.jeopardy.common :refer [scripts page]]
            [com.tylerkindy.jeopardy.db.core :refer [ds]]
            [com.tylerkindy.jeopardy.db.games :refer [insert-game get-game]]
            [com.tylerkindy.jeopardy.db.players :refer [get-player]]
            [com.tylerkindy.jeopardy.endless.incoming :refer [receive-message vote-for-new-clue vote-to-skip]]
            [com.tylerkindy.jeopardy.endless.live :refer [live-games send-all! setup-game-state!]]
            [com.tylerkindy.jeopardy.endless.views :refer [endless-container who-view]]
            [com.tylerkindy.jeopardy.players :refer [player-routes]]
            [compojure.core :refer [defroutes context POST GET]]
            [garden.core :refer [css]]
            [garden.stylesheet :refer [at-media]]
            [hiccup.core :refer [html]]
            [org.httpkit.server :refer [as-channel]]
            [ring.util.anti-forgery :refer [anti-forgery-field]]
            [clojure.string :as str]))

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
  (send-all! game-id (html (who-view game-id)))
  (vote-for-new-clue game-id nil)
  (vote-to-skip game-id nil))

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

(defn grid-template-areas-row [row]
  (let [row (->> row
                 (map name)
                 (str/join " "))]
    (str "\"" row "\"")))

(defn grid-template-areas [rows]
  (->> rows
       (map grid-template-areas-row)
       (str/join "\n")))

(defn endless-logged-in-page [{game-id :id} req]
  (let [player-id (get-in req [:session :id])]
    (page
     (list
      [:style (css {:pretty-print? false}
                   [:body {:margin "0"}]
                   [".right-guess" {:color :green}]
                   [".wrong-guess" {:color :red}]
                   [".vote-new-clue" {:color :orange, :font-style :italic}]
                   ["#endless" {:display :grid
                                :grid-template-areas
                                (grid-template-areas [[:category :players]
                                                      [:clue     :players]
                                                      [:button1  :button2]])
                                :grid-template-rows "1fr 3fr 1fr"
                                :grid-template-columns "3fr 2fr"
                                :max-width "1000px"
                                :margin "1vh auto"
                                :gap "10px"
                                :height "98vh"}]
                   [".card" {:background-color "rgb(0, 0, 175)"
                             :color :white
                             :font-family "serif"
                             :text-align :center}]
                   ["#clue-card" {:grid-area :clue}
                    (at-media {:max-width "599px"}
                              [:& {:font-size "1rem"}])
                    (at-media {:min-width "600px"}
                              [:& {:font-size "1.5rem"}])
                    [".clue" {:display :grid
                              :width "96%"
                              :height "96%"
                              :padding "2%"
                              :grid-template-rows "1fr 0fr"
                              :align-items :center}
                     [".answer" {:opacity "0", :font-size "0"}]
                     ["&.show-answer" {:grid-template-rows "1fr 1fr"
                                       :transition "grid-template-rows 2s"}
                      [".answer" {:opacity "1"
                                  :font-size "inherit"
                                  :transition "opacity 2s, font-size 0.5s"}]]
                     [:p {:margin "0"}]]]
                   ["#category-card" {:grid-area :category
                                      :font-size "1.5rem"}]
                   ["#players-card" {:grid-area :players}]
                   [".buzz-in" {:grid-area "button1"}
                    [:button {:width "100%", :height "100%"}]]
                   [".skip" {:grid-area "button2"}
                    [:button {:width "100%", :height "100%"}]]
                   [".new-question" {:grid-row "button1"
                                     :grid-column "button1 / button2"}
                    [:button {:width "100%", :height "100%"}]])])
     [:body {:hx-ext "ws,morph", :ws-connect (str "/games/" game-id)}
      (endless-container game-id player-id)

      scripts])))

(defn endless-logged-in [{game-id :id :as game} req]
  (setup-game-state! game-id)
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
