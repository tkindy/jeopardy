(ns com.tylerkindy.jeopardy.endless.views
  (:require [com.tylerkindy.jeopardy.db.core :refer [ds]]
            [com.tylerkindy.jeopardy.db.endless-clues :refer [get-current-clue]]
            [com.tylerkindy.jeopardy.db.players :refer [get-player list-players]]
            [com.tylerkindy.jeopardy.endless.live :refer [live-games]])
  (:import [java.text NumberFormat]
           [java.time Duration]
           [java.time.temporal ChronoUnit]))

(def score-format (doto (NumberFormat/getCurrencyInstance)
                    (.setMaximumFractionDigits 0)))
(defn format-score [score]
  (.format score-format score))

(defn guess-line [live-game player-id]
  (let [{:keys [guess correct?]} (get-in live-game [:state :attempted player-id])
        skip-votes (or (get-in live-game [:state :skip-votes]) #{})]
    (cond
      guess (let [class (if correct? "right-guess" "wrong-guess")]
              [:span {:class class} " (" guess ")"])
      (skip-votes player-id) [:span {:style "color: purple;"} " [skipped]"]
      :else nil)))

(defn new-clue-vote [live-game player-id]
  (when-let [votes (get-in live-game [:state :new-clue-votes])]
    (when (votes player-id)
      [:span.vote-new-clue " New clue"])))

(defn who-view [game-id]
  (let [live-game (get @live-games game-id)
        player-ids (or (->> live-game
                            :players
                            keys
                            set)
                       #{})
        players (->> (list-players ds {:game-id game-id})
                     (filter (fn [{:keys [id]}] (player-ids id)))
                     (sort-by :score)
                     reverse)]
    [:div#who
     [:ul
      (map (fn [{:keys [id name score]}]
             [:li (format "%s: %s"
                          name
                          (format-score score))
              (guess-line live-game id)
              (new-clue-vote live-game id)])
           players)]]))

(defn render-category [{:keys [category value]}]
  [:p#category [:i (str category ", $" value)]])

(defn render-clue [{:keys [question] :as clue}]
  (list
   (render-category clue)
   [:p question]))

(defn render-no-clue []
  [:i "No question yet"])

(defn clue-view [clue]
  [:div#clue
   (if clue
     (render-clue clue)
     (render-no-clue))])

(defn last-answer-view [last-answer]
  [:div#last-answer
   (when last-answer
     [:p {:style "color: green;"} last-answer])])

(defn buzzing-form [game-id player-id]
  (let [{state :name
         buzzed-in-id :buzzed-in
         attempted :attempted}
        (get-in @live-games [game-id :state])

        [type button-text] (if (and (= state :answering)
                                    (= buzzed-in-id player-id))
                             [:answer "Submit"]
                             [:buzz-in "Buzz in (spacebar)"])
        form-attrs (if (= type :buzz-in)
                     {:hx-trigger "click, keyup[key==' '] from:body"}
                     nil)
        button-attrs (if (or (#{:no-clue :drawing-clue :revealing-category :showing-answer}
                              state)
                             (and (= state :answering)
                                  (not= buzzed-in-id player-id))
                             (and (= state :open-for-answers)
                                  (attempted player-id)))
                       {:disabled ""}
                       nil)]
    [:form (merge {:ws-send ""} form-attrs)
     [:input {:name :type, :value type, :hidden ""}]
     (when (= type :answer)
       [:input {:type :text, :name :answer, :autofocus "", :autocomplete :off}])
     [:button (merge {:style "width: 100%; height: 100px;"}
                     button-attrs)
      button-text]]))

(defn seconds-left [deadline]
  (-> (- deadline (System/nanoTime))
      Duration/ofNanos
      (.truncatedTo ChronoUnit/SECONDS)
      (.plusSeconds 1)
      .toSeconds))

(defn buzz-time-left [buzz-deadline]
  (let [left (seconds-left buzz-deadline)]
    (str left "s remaining")))

(defn buzz-time-left-view [game-id]
  (let [{:keys [buzz-deadline]} (get-in @live-games [game-id :state])]
    (when buzz-deadline
      [:p#buzz-time-left [:i (buzz-time-left buzz-deadline)]])))

(defn category-reveal-time-left [left]
  [:p#category-reveal-time-left left])

(defn category-reveal-time-left-view [game-id]
  (let [{:keys [reveal-deadline]} (get-in @live-games [game-id :state])]
    (when reveal-deadline
      (category-reveal-time-left (seconds-left reveal-deadline)))))

(defn buzzing-view [game-id]
  (let [buzzed-in-id (get-in @live-games [game-id :state :buzzed-in])
        buzzed-in-player (get-player ds {:game-id game-id, :id buzzed-in-id})
        message (if buzzed-in-player
                  (str (:name buzzed-in-player) " is buzzed in")
                  "No one is buzzed in")]
    [:div#buzzing
     [:p [:i message]]
     (buzz-time-left-view game-id)]))

(defn answer-view [game-id]
  (let [clue (get-current-clue ds {:game-id game-id})]
    (list
     (clue-view clue)
     [:div#answer
      (last-answer-view (:answer clue))]
     [:form {:ws-send "", :hx-trigger "click, keyup[key=='n'] from:body"}
      [:input {:name :type, :value :new-clue, :hidden ""}]
      [:button "New question (n)"]])))

(defn skip-form [game-id player-id]
  [:form {:ws-send "", :hx-trigger "click, keyup[key=='s'] from:body"}
   [:input {:name :type, :value :skip-clue, :hidden ""}]
   [:button "Skip question (s)"]])

(defn question-view [game-id player-id]
  (let [clue (get-current-clue ds {:game-id game-id})]
    (list
     (when (not (:answered clue))
       [:div#question
        (clue-view clue)])
     (buzzing-view game-id)
     (skip-form game-id player-id))))

(defn drawing-view []
  (list
   [:p "Loading..."]))

(defn category-view [game-id]
  (let [clue (get-current-clue ds {:game-id game-id})]
    (list
     (render-category clue)
     (category-reveal-time-left-view game-id))))

(defn no-clue-card []
  [:div {:style "display: flex; width: 100%; height: 100%; flex-direction: column; justify-content: space-around;"}
   [:p {:style "padding: 0 20%;"} (.toUpperCase "No clue")]])

(defn drawing-card []
  [:div {:style "display: flex; width: 100%; height: 100%; flex-direction: column; justify-content: space-around;"}
   [:p {:style "padding: 0 20%;"} (.toUpperCase "Loading...")]])

(defn question-card [game-id]
  (let [clue (get-current-clue ds {:game-id game-id})]
    [:div.clue
     [:p.question {:style "padding: 0 20%;"} (.toUpperCase (:question clue))]
     [:p.answer {:style "padding: 0 20%;"}]]))

(defn answer-card [game-id]
  (let [clue (get-current-clue ds {:game-id game-id})]
    [:div.clue.show-answer
     [:p.question {:style "padding: 0 20%;"} (.toUpperCase (:question clue))]
     [:p.answer {:style "padding: 0 20%;"} (:answer clue)]]))

(defn card-view [game-id]
  [:div#card
   (case (get-in @live-games [game-id :state :name])
     :no-clue (no-clue-card)
     :drawing-clue (drawing-card)
     ;:revealing-category (category-card game-id)
     :showing-answer (answer-card game-id)
     (question-card game-id))])

(defn state-view [game-id player-id]
  (case (get-in @live-games [game-id :state :name])
    :no-clue (answer-view game-id)
    :drawing-clue (drawing-view)
    :revealing-category (category-view game-id)
    :showing-answer (answer-view game-id)
    (question-view game-id player-id)))

(defn endless-container [game-id player-id]
  [:div#endless {:hx-swap-oob :morph}
   (who-view game-id)
   (card-view game-id)
   (state-view game-id player-id)
   (buzzing-form game-id player-id)])
