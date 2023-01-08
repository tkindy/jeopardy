(ns com.tylerkindy.jeopardy.endless.incoming
  (:require [cheshire.core :as json]
            [clojure.set :as set]
            [com.tylerkindy.jeopardy.answer :refer [normalize-answer correct?]]
            [com.tylerkindy.jeopardy.constants :refer [max-buzz-duration]]
            [com.tylerkindy.jeopardy.db.core :refer [ds]]
            [com.tylerkindy.jeopardy.db.endless-clues :refer [get-current-clue insert-clue mark-answered]]
            [com.tylerkindy.jeopardy.db.players :refer [get-player update-score]]
            [com.tylerkindy.jeopardy.endless.live :refer [live-games send-all! transition!]]
            [com.tylerkindy.jeopardy.endless.views :refer [buzz-time-left-view buzzing-view endless-container]]
            [com.tylerkindy.jeopardy.jservice :refer [random-clue]]
            [hiccup.core :refer [html]]
            [hiccup.util :refer [escape-html]])
  (:import [java.util Timer TimerTask]))

(defn new-clue! [game-id]
  (mark-answered ds {:game-id game-id})
  (let [clue (-> (random-clue)
                 (select-keys [:category :question :answer :value])
                 (update :category :title)
                 (assoc :game-id game-id))]
    (insert-clue ds clue)
    (swap! live-games assoc-in [game-id :state] {:name :open-for-answers
                                                 :attempted {}})
    (send-all! game-id
               (fn [player-id]
                 (html (endless-container game-id player-id))))))

(defn vote-for-new-clue [game-id player-id]
  (when-let [live-game
             (transition! game-id
                          (fn [{:keys [name]}] (#{:no-clue :open-for-answers} name))
                          (fn [{{:keys [name attempted new-clue-votes]} :state,
                                :keys [players]}]
                            (let [new-clue-votes (or new-clue-votes #{})
                                  new-clue-votes (if player-id
                                                   (conj new-clue-votes player-id)
                                                   new-clue-votes)]
                              (if (set/subset? (set (keys players)) new-clue-votes)
                                {:name :drawing-clue}
                                {:name name
                                 :attempted attempted
                                 :new-clue-votes new-clue-votes}))))]
    (send-all! game-id
               (fn [player-id]
                 (html (endless-container game-id player-id))))
    (when (= (get-in live-game [:state :name]) :drawing-clue)
      (new-clue! game-id))))

(defn right-answer [game-id player-id value]
  (let [{:keys [score]} (get-player ds {:id player-id, :game-id game-id})]
    (update-score ds {:id player-id, :score (+ score value)}))
  (swap! live-games
         (fn [live-games]
           (update-in live-games [game-id :state]
                      (fn [{:keys [attempted]}]
                        {:name :no-clue
                         :attempted (assoc-in attempted [player-id :correct?] true)})))))

(defn wrong-answer [game-id player-id value]
  (let [{:keys [score]} (get-player ds {:id player-id, :game-id game-id})]
    (update-score ds {:id player-id, :score (- score value)}))
  (swap! live-games
         (fn [live-games]
           (update-in live-games [game-id :state]
                      (fn [{:keys [attempted]}]
                        {:name :open-for-answers
                         :attempted (assoc-in attempted [player-id :correct?] false)})))))

(defn buzz-timer-update-task [game-id]
  (let [last-view (atom nil)]
    (proxy [TimerTask] []
      (run []
        (let [view (buzz-time-left-view game-id)]
          (if view
            (when (not= view @last-view)
              (send-all! game-id (html view))
              (reset! last-view view))
            (.cancel this)))))))

(defn buzz-timeout-task [game-id player-id current-clue-id update-task]
  (proxy [TimerTask] []
    (run []
      (.cancel update-task)
      (let [{clue-id :id, value :value} (get-current-clue ds {:game-id game-id})]
        (when (transition! game-id
                           (fn [{:keys [name buzzed-in]}]
                             (and (= name :answering)
                                  (= buzzed-in player-id)
                                  (= clue-id current-clue-id)))
                           (fn [{{:keys [attempted]} :state}]
                             {:name :timing-out
                              :attempted attempted}))
          (wrong-answer game-id player-id value)
          (send-all! game-id
                     (fn [player-id]
                       (html (endless-container game-id player-id)))))))))

(defn start-countdown [game-id player-id]
  (let [current-clue-id (:id (get-current-clue ds {:game-id game-id}))
        update-task (buzz-timer-update-task game-id)]
    (doto (Timer.)
      (.schedule update-task 0 50)
      (.schedule (buzz-timeout-task game-id player-id current-clue-id update-task)
                 (.toMillis max-buzz-duration)))))

(defn buzz-in [game-id player-id]
  (when (transition! game-id
                     (fn [{:keys [name attempted]}] (and (= name :open-for-answers)
                                                         (not (attempted player-id))))
                     (fn [{{:keys [attempted]} :state}]
                       {:name :answering
                        :attempted (assoc attempted player-id {})
                        :buzzed-in player-id
                        :buzz-deadline (+ (System/nanoTime) (.toNanos max-buzz-duration))}))
    (start-countdown game-id player-id)
    (send-all! game-id
               (fn [player-id]
                 (html (buzzing-view game-id player-id))))))

(defn check-answer [game-id player-id {guess :answer}]
  (let [guess (escape-html guess)]
    (when (transition! game-id
                       (fn [{:keys [name buzzed-in]}]
                         (and (= name :answering)
                              (= player-id buzzed-in)))
                       (fn [{{:keys [attempted]} :state}]
                         {:name :checking-answer
                          :attempted (assoc-in attempted [player-id :guess] guess)}))
      (let [{:keys [answer value]} (get-current-clue ds {:game-id game-id})]
        (if (correct? answer (normalize-answer guess))
          (right-answer game-id player-id value)
          (wrong-answer game-id player-id value)))
      (send-all! game-id
                 (fn [player-id]
                   (html (endless-container game-id player-id)))))))

(defn receive-message [game-id player-id message]
  (let [message (json/parse-string message keyword)]
    (case (keyword (:type message))
      :new-clue (vote-for-new-clue game-id player-id)
      :buzz-in  (buzz-in game-id player-id)
      :answer   (check-answer game-id player-id message))))
