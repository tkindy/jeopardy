(ns com.tylerkindy.jeopardy.endless.incoming
  (:require [cheshire.core :as json]
            [clojure.set :as set]
            [com.tylerkindy.jeopardy.answer :refer [normalize-answer correct?]]
            [com.tylerkindy.jeopardy.constants :refer [category-reveal-duration max-buzz-duration]]
            [com.tylerkindy.jeopardy.db.core :refer [ds]]
            [com.tylerkindy.jeopardy.db.endless-clues :refer [get-current-clue insert-clue mark-answered]]
            [com.tylerkindy.jeopardy.db.games :refer [get-game]]
            [com.tylerkindy.jeopardy.db.guesses :refer [insert-guess]]
            [com.tylerkindy.jeopardy.db.players :refer [get-player update-score]]
            [com.tylerkindy.jeopardy.endless.live :refer [live-games send-all! transition!]]
            [com.tylerkindy.jeopardy.endless.views :refer [answer-card buttons buzz-time-left-view
                                                           category-reveal-time-left-view
                                                           endless-container new-question-form
                                                           players-view status-view overlay]]
            [com.tylerkindy.jeopardy.clues :refer [random-clue next-category-clue]]
            [com.tylerkindy.jeopardy.mode :as mode]
            [com.tylerkindy.jeopardy.time :refer [now]]
            [hiccup.util :refer [escape-html]])
  (:import [java.util Timer TimerTask]))

(defn should-show-answer? [players attempted skip-votes]
  (set/subset? (set (keys players))
               (set/union skip-votes
                          (set (keys (or attempted {}))))))

(defn category-reveal-timer-update-task [game-id]
  (let [last-view (atom nil)]
    (proxy [TimerTask] []
      (run []
        (let [deadline (get-in @live-games [game-id :state :reveal-deadline])]
          (if (> (- deadline (System/nanoTime)) 0)
            (let [view (category-reveal-time-left-view game-id)]
              (when (not= view @last-view)
                (send-all! game-id view)
                (reset! last-view view)))
            (do
              (.cancel this)
              (swap! live-games assoc-in [game-id :state]
                     {:name :open-for-answers, :attempted {}})
              (send-all! game-id
                         (fn [player-id]
                           (endless-container game-id player-id))))))))))

(defn reveal-category [game-id]
  (swap! live-games assoc-in [game-id :state]
         {:name :revealing-category,
          :reveal-deadline (+ (System/nanoTime) (.toNanos category-reveal-duration))})
  (.schedule (Timer.) (category-reveal-timer-update-task game-id) 0 50))

(defn pick-clue [game-id]
  (let [{:keys [mode]} (get-game ds {:id game-id})]
    (condp = mode
      mode/endless            (random-clue)
      mode/endless-categories (next-category-clue (get-current-clue ds {:game-id game-id})))))

(defn new-clue! [game-id]
  (let [clue (-> (pick-clue game-id)
                 (select-keys [:lib-clue-id :category :airdate :question :answer :value])
                 (assoc :game-id game-id))]
    (insert-clue ds clue)
    (reveal-category game-id)
    (send-all! game-id
               (fn [player-id]
                 (endless-container game-id player-id)))))

(defn vote-for-new-clue [game-id player-id]
  (when-let [live-game
             (transition! game-id
                          (fn [{:keys [name]}] (#{:no-clue :showing-answer} name))
                          (fn [{{:keys [name attempted skip-votes new-clue-votes]} :state,
                                :keys [players]}]
                            (let [new-clue-votes (or new-clue-votes #{})
                                  new-clue-votes (if player-id
                                                   (conj new-clue-votes player-id)
                                                   new-clue-votes)]
                              (if (set/subset? (set (keys players)) new-clue-votes)
                                {:name :drawing-clue}
                                {:name name
                                 :attempted attempted
                                 :skip-votes skip-votes
                                 :new-clue-votes new-clue-votes}))))]
    (if (= (get-in live-game [:state :name]) :drawing-clue)
      (do
        (send-all! game-id
                   (fn [player-id]
                     (endless-container game-id player-id)))
        (new-clue! game-id))
      (send-all! game-id
                 (fn [player-id]
                   [(players-view game-id)
                    (buttons game-id player-id)])))))

(defn propose-correction [game-id player-id]
  (when (transition! game-id
                     (fn [{:keys [name]}] (= name :showing-answer))
                     (fn [{{:keys [attempted skip-votes]} :state}]
                       {:name :proposing-correction
                        :proposer player-id
                        :attempted attempted
                        :skip-votes skip-votes}))
    (send-all! game-id
               (fn [player-id]
                 [(status-view game-id)
                  (buttons game-id player-id)
                  (overlay game-id player-id)]))))

(defn cancel-correction [game-id player-id]
  (when (transition! game-id
                     (fn [{:keys [name proposer]}]
                       (and (= name :proposing-correction)
                            (= proposer player-id)))
                     (fn [{{:keys [attempted skip-votes]} :state}]
                       {:name :showing-answer
                        :attempted attempted
                        :skip-votes skip-votes}))
    (send-all! game-id
               (fn [player-id]
                 [(status-view game-id)
                  (buttons game-id player-id)
                  (overlay game-id player-id)]))))

(defn show-answer [game-id]
  (send-all! game-id
             (fn [player-id]
               [(answer-card game-id)
                (players-view game-id)
                (buttons game-id player-id)])))

(defn right-answer [game-id player-id value]
  (let [{:keys [score]} (get-player ds {:id player-id, :game-id game-id})]
    (update-score ds {:id player-id, :score (+ score value)}))
  (mark-answered ds {:game-id game-id})
  (swap! live-games
         (fn [live-games]
           (update-in live-games [game-id :state]
                      (fn [{:keys [attempted skip-votes]}]
                        {:name :showing-answer
                         :attempted (assoc-in attempted [player-id :correct?] true)
                         :skip-votes skip-votes}))))
  (show-answer game-id))

(defn wrong-answer [game-id player-id value]
  (let [{:keys [score]} (get-player ds {:id player-id, :game-id game-id})]
    (update-score ds {:id player-id, :score (- score value)}))
  (let [live-games
        (swap! live-games
               (fn [live-games]
                 (update-in live-games [game-id :state]
                            (fn [{:keys [attempted skip-votes]}]
                              (let [attempted (assoc-in attempted [player-id :correct?] false)
                                    state (if (should-show-answer? (get-in live-games [game-id :players])
                                                                   attempted
                                                                   skip-votes)
                                            :showing-answer
                                            :open-for-answers)]
                                {:name state
                                 :attempted attempted
                                 :skip-votes skip-votes})))))]
    (if (= (get-in live-games [game-id :state :name])
           :showing-answer)
      (do
        (mark-answered ds {:game-id game-id})
        (show-answer game-id))
      (send-all! game-id
                 (fn [player-id]
                   (endless-container game-id player-id))))))

(defn buzz-timer-update-task [game-id]
  (let [last-view (atom nil)]
    (proxy [TimerTask] []
      (run []
        (let [view (buzz-time-left-view game-id)]
          (if view
            (when (not= view @last-view)
              (send-all! game-id view)
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
                           (fn [{{:keys [attempted skip-votes]} :state}]
                             {:name :timing-out
                              :skip-votes skip-votes
                              :attempted (assoc-in attempted [player-id :guess] "")}))
          (wrong-answer game-id player-id value))))))

(defn start-buzzed-countdown [game-id player-id]
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
                     (fn [{{:keys [attempted skip-votes]} :state}]
                       {:name :answering
                        :attempted (assoc attempted player-id {})
                        :skip-votes skip-votes
                        :buzzed-in player-id
                        :buzz-deadline (+ (System/nanoTime) (.toNanos max-buzz-duration))}))
    (start-buzzed-countdown game-id player-id)
    (send-all! game-id
               (fn [player-id]
                 (endless-container game-id player-id)))))

(defn vote-to-skip [game-id player-id]
  (when-let [game
             (transition! game-id
                          (fn [{:keys [name attempted]}]
                            (and (= name :open-for-answers)
                                 (not (contains? (or attempted {})
                                                 player-id))))
                          (fn [{:keys [state]}]
                            (-> state
                                (select-keys [:attempted :skip-votes])
                                (assoc :name :voting-to-skip))))]
    (let [{:keys [state players]} game
          {:keys [attempted skip-votes]} state
          skip-votes (or skip-votes #{})
          skip-votes (if player-id
                       (conj skip-votes player-id)
                       skip-votes)
          new-state (if (should-show-answer? players attempted skip-votes)
                      :showing-answer
                      :open-for-answers)]
      (when (= new-state :showing-answer)
        (mark-answered ds {:game-id game-id}))
      (swap! live-games
             (fn [live-games]
               (assoc-in live-games
                         [game-id :state]
                         {:name new-state
                          :attempted attempted
                          :skip-votes skip-votes})))
      (if (= new-state :showing-answer)
        (show-answer game-id)
        (send-all! game-id
                   (fn [player-id]
                     [(players-view game-id)
                      (buttons game-id player-id)]))))))

(defn check-answer [game-id player-id {guess :answer}]
  (let [guess (escape-html guess)]
    (when (transition! game-id
                       (fn [{:keys [name buzzed-in]}]
                         (and (= name :answering)
                              (= player-id buzzed-in)))
                       (fn [{{:keys [attempted skip-votes]} :state}]
                         {:name :checking-answer
                          :attempted (assoc-in attempted [player-id :guess] guess)
                          :skip-votes skip-votes}))
      (let [{clue-id :id, :keys [answer value]} (get-current-clue ds {:game-id game-id})
            correct (correct? answer (normalize-answer guess))]
        (insert-guess ds {:clue-id clue-id, :player-id player-id,
                          :guess guess, :correct correct, :guessed-at (now)})
        (if correct
          (right-answer game-id player-id value)
          (wrong-answer game-id player-id value))))))

(defn receive-message [game-id player-id message]
  (let [message (-> message
                    (json/parse-string keyword)
                    (update :type keyword))]
    (case (:type message)
      :new-clue  (vote-for-new-clue game-id player-id)
      :propose-correction (propose-correction game-id player-id)
      :cancel-correction (cancel-correction game-id player-id)
      :buzz-in   (buzz-in game-id player-id)
      :skip-clue (vote-to-skip game-id player-id)
      :answer    (check-answer game-id player-id message))))
