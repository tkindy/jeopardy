(ns com.tylerkindy.jeopardy.games
  (:require [com.tylerkindy.jeopardy.common :refer [scripts page]]
            [com.tylerkindy.jeopardy.db.core :refer [ds]]
            [com.tylerkindy.jeopardy.db.games :refer [insert-game get-game]]
            [com.tylerkindy.jeopardy.db.players :refer [get-player]]
            [com.tylerkindy.jeopardy.endless.incoming :refer [receive-message vote-for-new-clue vote-to-skip]]
            [com.tylerkindy.jeopardy.endless.live :refer [live-games send-all! setup-game-state!]]
            [com.tylerkindy.jeopardy.endless.views :refer [endless-container]]
            [com.tylerkindy.jeopardy.mode :as mode]
            [com.tylerkindy.jeopardy.players :refer [player-routes]]
            [com.tylerkindy.jeopardy.time :refer [now]]
            [compojure.core :refer [defroutes context POST GET]]
            [garden.core :refer [css]]
            [garden.stylesheet :refer [at-media]]
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

(defn create-game [req]
  (let [id (generate-game-id)
        mode (condp = (keyword (get-in req [:params :mode]))
               :endless mode/endless
               :endless-categories mode/endless-categories)]
    (insert-game ds {:id id, :mode mode, :created-at (now)})
    {:status 303
     :headers {"Location" (str "/games/" id)}}))

(defn connect-player [game-id player-id ch]
  (setup-game-state! game-id)
  (swap! live-games assoc-in [game-id :players player-id] ch)
  (send-all! game-id
             (fn [player-id]
               (endless-container game-id player-id))))

(defn disconnect-player [game-id player-id]
  (swap! live-games
         (fn [live-games]
           (let [live-games (update-in live-games [game-id :players] dissoc player-id)]
             (if (empty? (get-in live-games [game-id :players]))
               (dissoc live-games game-id)
               live-games))))
  (send-all! game-id
             (fn [player-id]
               (endless-container game-id player-id)))
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
                   ["button" {:border "2px solid black"
                              :border-radius "5px"}
                    [:&:disabled {:border-color :gray}]]
                   ["#endless" {:display :grid
                                :grid-template-areas
                                (grid-template-areas [[:category :players]
                                                      [:clue     :players]
                                                      [:clue     :status]
                                                      [:button1  :button2]])
                                :grid-template-rows "1fr 2fr 1fr 1fr"
                                :grid-template-columns "3fr 2fr"
                                :max-width "1000px"
                                :margin "1vh auto"
                                :gap "10px"
                                :height "98vh"}]
                   [".card" {:background-color "#00c"
                             :border "3px solid black"
                             :color :white
                             :font-family "serif"
                             :text-align :center}]
                   [".countdown" {:display :grid
                                  :align-items :center
                                  :width "100%"
                                  :height "100%"}]
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
                                       :transition "2s"}
                      [".question" {:scale 0.75
                                    :transition "2s"}]
                      [".answer" {:opacity "1"
                                  :font-size "inherit"
                                  :transition "2s"}]]
                     [:p {:margin "0"}]]]
                   ["#category-card" {:grid-area :category
                                      :font-size "1.5rem"}]
                   ["#players-card" {:grid-area :players
                                     :display :grid
                                     :grid-template-columns "1fr 1fr"
                                     :grid-auto-rows "10vh"
                                     :padding "10px"
                                     :gap "10px"}
                    [".player" {:background-color :white
                                :border "2px solid black"
                                :display :grid
                                :grid-template-rows "1fr 1fr 1fr"
                                :align-items :center}
                     [".guess.skipped" {:font-style :italic}]
                     ["&.buzzed-in" {:background-color :orange}
                      [:p {:color :white}]]
                     ["&.right-guess" {:background-color "#090"}
                      [:p {:color :white}]]
                     ["&.wrong-guess" {:background-color "#c00"}
                      [:p {:color :white}]]
                     ["&.skipped" {:background-color "#60c"
                                   :color :white}
                      [:p {:color :white}]]
                     ["&.vote-new-clue" {:background-color :orange}
                      [:p {:color :white}]]
                     [:p {:margin 0
                          :color :black}]]]
                   ["#status-card" {:grid-area :status
                                    :display :flex
                                    :justify-content :center
                                    :align-items :center
                                    :font-size "1.5rem"}]
                   ["#buttons" {:grid-column "button1 / button2"
                                :display :grid
                                :grid-template-areas (grid-template-areas [[:button1 :button2]])
                                :grid-template-columns "3fr 2fr"
                                :grid-template-rows "1fr"
                                :gap "10px"}]
                   ["#buzz-in-form" {:grid-area "button1"
                                     :display :grid
                                     :grid-template-columns "1fr"
                                     :grid-template-rows "1fr"
                                     :align-items :stretch
                                     :justify-items :stretch}
                    [:input.answer {:text-align :center
                                    :font-size "1.5rem"}]]
                   ["#skip-form" {:grid-area "button2"
                                  :display :grid
                                  :grid-template-columns "1fr"
                                  :grid-template-rows "1fr"
                                  :align-items :stretch
                                  :justify-items :stretch}]
                   ["#new-question-form" {:grid-area "button1"
                                          :display :grid
                                          :grid-template-columns "1fr"
                                          :grid-template-rows "1fr"
                                          :align-items :stretch
                                          :justify-items :stretch}]
                   ["#propose-correction-form" {:grid-area "button2"
                                                :display :grid
                                                :grid-template-columns "1fr"
                                                :grid-template-rows "1fr"
                                                :align-items :stretch
                                                :justify-items :stretch}]
                   ["#overlay-container" {:position :fixed
                                          :width "100%"
                                          :height "100%"
                                          :top 0
                                          :left 0
                                          :background-color "rgba(0,0,0,0.5)"
                                          :z-index 2}]
                   ["#overlay" {:height "90vh"
                                :margin "5vh auto"
                                :max-width "800px"
                                :display :grid}])])
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
  (condp = mode
    mode/endless (endless-response game req)
    mode/endless-categories (endless-response game req)
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
    (POST "/" req (create-game req))
    (context "/:game-id" []
      (GET "/" req (handle-game-request req))
      player-routes)))
