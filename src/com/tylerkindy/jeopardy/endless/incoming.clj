(ns com.tylerkindy.jeopardy.endless.incoming
  (:require [cheshire.core :as json]
            [com.tylerkindy.jeopardy.answer :refer [normalize-answer correct?]]
            [com.tylerkindy.jeopardy.db.core :refer [ds]]
            [com.tylerkindy.jeopardy.db.endless-clues :refer [get-current-clue insert-clue]]
            [com.tylerkindy.jeopardy.db.players :refer [get-player update-score]]
            [com.tylerkindy.jeopardy.endless.live :refer [live-games send-all! transition!]]
            [com.tylerkindy.jeopardy.endless.views :refer [buzzing-view clue-view endless-container]]
            [com.tylerkindy.jeopardy.jservice :refer [random-clue]]
            [hiccup.core :refer [html]]))

(defn new-clue! [game-id]
  (let [clue (-> (random-clue)
                 (select-keys [:category :question :answer :value])
                 (update :category :title)
                 (assoc :game-id game-id))]
    (insert-clue ds clue)
    (swap! live-games assoc-in [game-id :state] {:name :open-for-answers
                                                 :attempted #{}})
    (send-all! game-id (html (clue-view clue)))))

(defn request-new-clue [game-id]
  (when (transition! game-id
                     (fn [{:keys [name]}] (#{:idle :open-for-answers} name))
                     {:name :drawing-clue})
    (new-clue! game-id)))

(defn buzz-in [game-id player-id]
  (when (transition! game-id
                     (fn [{:keys [name attempted]}] (and (= name :open-for-answers)
                                                         (not (attempted player-id))))
                     (fn [{:keys [attempted]}]
                       {:name :answering
                        :attempted (conj attempted player-id)
                        :buzzed-in player-id}))
    (send-all! game-id
               (fn [player-id]
                 (html (buzzing-view game-id player-id))))))

(defn right-answer [game-id player-id value]
  (let [{:keys [score]} (get-player ds {:id player-id, :game-id game-id})]
    (update-score ds {:id player-id, :score (+ score value)}))
  (send-all! game-id
             (fn [player-id]
               (html (endless-container game-id player-id))))
  (new-clue! game-id))

(defn wrong-answer [game-id player-id value]
  (let [{:keys [score]} (get-player ds {:id player-id, :game-id game-id})]
    (update-score ds {:id player-id, :score (- score value)}))
  (swap! live-games
         (fn [live-games]
           (update-in live-games [game-id :state]
                      (fn [{:keys [attempted]}]
                        {:name :open-for-answers
                         :attempted attempted})))))

(defn check-answer [game-id player-id {guess :answer}]
  (when (transition! game-id
                     (fn [{:keys [name buzzed-in]}]
                       (and (= name :answering)
                            (= player-id buzzed-in)))
                     (fn [{:keys [attempted]}]
                       {:name :checking-answer
                        :attempted attempted}))
    (let [{:keys [answer value]} (get-current-clue ds {:game-id game-id})]
      (if (correct? answer (normalize-answer guess))
        (right-answer game-id player-id value)
        (wrong-answer game-id player-id value)))
    (send-all! game-id
               (fn [player-id]
                 (html (endless-container game-id player-id))))))

(defn receive-message [game-id player-id message]
  (let [message (json/parse-string message keyword)]
    (case (keyword (:type message))
      :new-clue (request-new-clue game-id)
      :buzz-in  (buzz-in game-id player-id)
      :answer   (check-answer game-id player-id message))))
