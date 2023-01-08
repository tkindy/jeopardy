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
  (when-let [{:keys [guess correct?]} (get-in live-game [:state :attempted player-id])]
    (when guess
      (let [class (if correct? "right-guess" "wrong-guess")]
        [:span {:class class} " (" guess ")"]))))

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
        button-attrs (if (or (#{:no-clue :showing-answer} state)
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

(defn buzz-time-left [buzz-deadline]
  (let [time-left (-> (max 0 (- buzz-deadline (System/nanoTime)))
                      Duration/ofNanos
                      (.truncatedTo ChronoUnit/SECONDS)
                      (.plusSeconds 1))]
    (str (.toSeconds time-left) "s remaining")))

(defn buzz-time-left-view [game-id]
  (let [{:keys [buzz-deadline]} (get-in @live-games [game-id :state])]
    (when buzz-deadline
      [:p#buzz-time-left [:i (buzz-time-left buzz-deadline)]])))

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
      (last-answer-view (:answer clue))])))

(defn question-view [game-id]
  (let [clue (get-current-clue ds {:game-id game-id})]
    (list
     (when (not (:answered clue))
       [:div#question
        (clue-view clue)])
     (buzzing-view game-id))))

(defn state-view [game-id]
  (case (get-in @live-games [game-id :state :name])
    :showing-answer (answer-view game-id)
    (question-view game-id)))

(defn endless-container [game-id player-id]
  [:div#endless
   (who-view game-id)
   (state-view game-id)
   (buzzing-form game-id player-id)
   [:form {:ws-send "", :hx-trigger "click, keyup[key=='n'] from:body"}
    [:input {:name :type, :value :new-clue, :hidden ""}]
    [:button "New question (n)"]]])
