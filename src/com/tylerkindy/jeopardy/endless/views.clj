(ns com.tylerkindy.jeopardy.endless.views
  (:require [clojure.core.match :refer [match]]
            [com.tylerkindy.jeopardy.db.core :refer [ds]]
            [com.tylerkindy.jeopardy.db.endless-clues :refer [get-current-clue]]
            [com.tylerkindy.jeopardy.db.players :refer [get-player list-players]]
            [com.tylerkindy.jeopardy.endless.live :refer [live-games]]))

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

(defn buzzing-form [game-id player-id]
  (let [live-game (get @live-games game-id)
        state (get-in live-game [:state :name])
        buzzed-in-id (get-in live-game [:state :buzzed-in])
        [type button-text form-attrs button-attrs]
        (match [state buzzed-in-id]
          [:answering player-id] [:answer "Submit" nil nil]
          [:answering _] [:buzz-in
                          "Buzz in (spacebar)"
                          {:hx-trigger "click, keyup[key==' '] from:body"}
                          {:disabled ""}]
          :else           [:buzz-in
                           "Buzz in (spacebar)"
                           {:hx-trigger "click, keyup[key==' '] from:body"}
                           nil])]
    [:form (merge {:ws-send ""} form-attrs)
     [:input {:name :type, :value type, :hidden ""}]
     (when (= type :answer)
       [:input {:type :text, :name :answer, :autofocus "", :autocomplete :off}])
     [:button (merge {:style "width: 100%; height: 100px;"}
                     button-attrs)
      button-text]]))

(defn buzzing-view [game-id player-id]
  (let [buzzed-in-id (get-in @live-games [game-id :state :buzzed-in])
        buzzed-in-player (get-player ds {:game-id game-id, :id buzzed-in-id})
        message (if buzzed-in-player
                  (str (:name buzzed-in-player) " buzzed in")
                  "No one is buzzed in")]
    [:div#buzzing
     [:p [:i message]]
     (buzzing-form game-id player-id)]))

(defn endless-container [game-id player-id]
  (let [clue (get-current-clue ds {:game-id game-id})]
    [:div#endless
     (who-view game-id)
     (clue-view clue)
     (buzzing-view game-id player-id)
     [:form {:ws-send ""}
      [:input {:name :type, :value :new-clue, :hidden ""}]
      [:button "New question"]]]))