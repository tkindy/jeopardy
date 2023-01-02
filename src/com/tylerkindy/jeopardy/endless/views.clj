(ns com.tylerkindy.jeopardy.endless.views
  (:require [com.tylerkindy.jeopardy.db.core :refer [ds]]
            [com.tylerkindy.jeopardy.db.endless-clues :refer [get-current-clue get-last-answer]]
            [com.tylerkindy.jeopardy.db.players :refer [get-player list-players]]
            [com.tylerkindy.jeopardy.endless.live :refer [live-games]])
  (:import [java.text NumberFormat]
           [java.time Duration]
           [java.time.temporal ChronoUnit]))

(def score-format (doto (NumberFormat/getCurrencyInstance)
                    (.setMaximumFractionDigits 0)))
(defn format-score [score]
  (.format score-format score))

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
     [:ul
      (map (fn [{:keys [name score]}]
             [:li (format "%s: %s" name (format-score score))])
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
     [:i (str "The last answer was: " last-answer)])])

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
        button-attrs (if (or (and (= state :answering)
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

(defn buzzing-view [game-id player-id]
  (let [{buzzed-in-id :buzzed-in} (get-in @live-games [game-id :state])
        buzzed-in-player (get-player ds {:game-id game-id, :id buzzed-in-id})
        message (if buzzed-in-player
                  (str (:name buzzed-in-player) " is buzzed in")
                  "No one is buzzed in")]
    [:div#buzzing
     [:p [:i message]]
     (buzz-time-left-view game-id)
     (buzzing-form game-id player-id)]))

(defn endless-container [game-id player-id]
  (let [clue (get-current-clue ds {:game-id game-id})
        {last-answer :answer} (get-last-answer ds {:game-id game-id})]
    [:div#endless
     (who-view game-id)
     (clue-view clue)
     (last-answer-view last-answer)
     (buzzing-view game-id player-id)
     [:form {:ws-send ""}
      [:input {:name :type, :value :new-clue, :hidden ""}]
      [:button "New question"]]]))
