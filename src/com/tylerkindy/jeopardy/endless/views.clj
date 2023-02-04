(ns com.tylerkindy.jeopardy.endless.views
  (:require [com.tylerkindy.jeopardy.db.core :refer [ds]]
            [com.tylerkindy.jeopardy.db.endless-clues :refer [get-current-clue]]
            [com.tylerkindy.jeopardy.db.players :refer [list-players]]
            [com.tylerkindy.jeopardy.endless.live :refer [live-games]])
  (:import [java.text NumberFormat]
           [java.time Duration]
           [java.time.temporal ChronoUnit]))

(def score-format (doto (NumberFormat/getCurrencyInstance)
                    (.setMaximumFractionDigits 0)))
(defn format-score [score]
  (.format score-format score))

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
    [:form#buzz-in-form (merge {:ws-send ""} form-attrs)
     [:input {:name :type, :value type, :hidden ""}]
     (if (= type :answer)
       (list
        [:input.answer {:type :text, :name :answer, :autofocus "", :autocomplete :off}]
        [:button {:style "display: none;"} "Submit"])
       [:button button-attrs button-text])]))

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

(defn new-question-form []
  [:form#new-question-form {:ws-send ""
                            :hx-trigger "click, keyup[key=='n'] from:body"}
   [:input {:name :type, :value :new-clue, :hidden ""}]
   [:button "New question (n)"]])

(defn can-skip? [game-id player-id]
  (let [{state :name
         buzzed-in-id :buzzed-in
         attempted :attempted
         skipped :skip-votes}
        (get-in @live-games [game-id :state])]
    (and (= state :open-for-answers)
         (not= player-id buzzed-in-id)
         (not ((or attempted {}) player-id))
         (not ((or skipped #{}) player-id)))))

(defn skip-form [game-id player-id]
  [:form#skip-form {:ws-send "", :hx-trigger "click, keyup[key=='s'] from:body"}
   [:input {:name :type, :value :skip-clue, :hidden ""}]
   [:button {:disabled (if (can-skip? game-id player-id) false "")}
    "Skip question (s)"]])

(defn no-clue-card []
  [:div {:style "display: flex; width: 100%; height: 100%; flex-direction: column; justify-content: space-around;"}
   [:p {:style "padding: 0 20%;"} (.toUpperCase "No clue")]])

(defn drawing-card []
  [:div {:style "display: flex; width: 100%; height: 100%; flex-direction: column; justify-content: space-around;"}
   [:p {:style "padding: 0 20%;"} (.toUpperCase "Loading...")]])

(defn category-card [game-id]
  (let [clue (get-current-clue ds {:game-id game-id})]
    [:div.category-value
     [:p.category (.toUpperCase (:category clue))]
     [:p.value {:style "color: gold;"} "$" (:value clue)]]))

(defn category-card-view [game-id]
  [:div#category-card.card
   (case (get-in @live-games [game-id :state :name])
     :no-clue (no-clue-card)
     :drawing-clue (drawing-card)
     (category-card game-id))])

(defn question-card [game-id]
  (let [clue (get-current-clue ds {:game-id game-id})]
    [:div#inner-clue-card.clue
     [:p.question {:style "padding: 0 20%;"} (.toUpperCase (:question clue))]
     [:p.answer {:style "padding: 0 20%;"}]]))

(defn countdown-card [game-id]
  [:div#inner-clue-card.countdown
   (category-reveal-time-left-view game-id)])

(defn answer-card [game-id]
  (let [clue (get-current-clue ds {:game-id game-id})]
    [:div#inner-clue-card.clue.show-answer {:hx-swap-oob :morph}
     [:p.question {:style "padding: 0 20%;"} (.toUpperCase (:question clue))]
     [:p.answer {:style "padding: 0 20%;"} (:answer clue)]]))

(defn clue-card-view [game-id]
  [:div#clue-card.card
   (case (get-in @live-games [game-id :state :name])
     :no-clue (no-clue-card)
     :drawing-clue (drawing-card)
     :revealing-category (countdown-card game-id)
     :showing-answer (answer-card game-id)
     (question-card game-id))])

(defn player-card [live-game {:keys [id name score]}]
  (let [{:keys [guess correct?]} (get-in live-game [:state :attempted id])
        skip-votes (or (get-in live-game [:state :skip-votes]) #{})
        new-clue-votes (or (get-in live-game [:state :new-clue-votes]) #{})
        buzzed-in-id (get-in live-game [:state :buzzed-in])
        guess-line (cond
                     guess [:p.guess guess]
                     (= buzzed-in-id id) (buzz-time-left-view (:id live-game))
                     (skip-votes id) [:p.guess "[skipped]"]
                     :else [:p.guess nil])
        class (cond
                (new-clue-votes id) "vote-new-clue"
                (= buzzed-in-id id) "buzzed-in"
                guess (if correct? "right-guess" "wrong-guess")
                (skip-votes id) "skipped"
                :else nil)]
    [:div.player {:class class}
     [:p.name name]
     [:p.score (format-score score)]
     guess-line]))

(defn player-cards [game-id]
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
    (map (partial player-card live-game) players)))

(defn players-view [game-id]
  [:div#players-card.card
   (player-cards game-id)])

(defn buttons [game-id player-id]
  (let [state (get-in @live-games [game-id :state :name])]
    [:div#buttons
     (cond
       (#{:drawing-clue :revealing-category :open-for-answers :answering}
        state)
       (list
        (buzzing-form game-id player-id)
        (skip-form game-id player-id))

       (#{:no-clue :showing-answer} state)
       (new-question-form))]))

(defn endless-container [game-id player-id]
  [:div#endless
   (category-card-view game-id)
   (clue-card-view game-id)
   (players-view game-id)
   (buttons game-id player-id)])
