(ns com.tylerkindy.jeopardy.endless.live
  (:require [com.tylerkindy.jeopardy.db.core :refer [ds]]
            [com.tylerkindy.jeopardy.db.endless-clues :refer [get-current-clue]]
            [org.httpkit.server :refer [send!]]))

(defonce live-games (atom {}))

(defn derive-state [game-id]
  (if (get-current-clue ds {:game-id game-id})
    {:name :open-for-answers
     :attempted {}}
    {:name :no-clue}))

(defn build-game [game-id]
  {:state (derive-state game-id)})

(defn setup-game-state! [game-id]
  (swap! live-games
         (fn [live-games]
           (if (get live-games game-id)
             live-games
             (assoc live-games game-id (build-game game-id))))))

(defn transition! [game-id from-pred to-state]
  (let [[old new] (swap-vals! live-games
                              (fn [live-games]
                                (if (from-pred (get-in live-games [game-id :state]))
                                  (if (fn? to-state)
                                    (update-in live-games [game-id :state] to-state)
                                    (assoc-in live-games [game-id :state] to-state))
                                  live-games)))]
    (not= old new)))

(defn send-all! [game-id message]
  (let [players (get-in @live-games [game-id :players])]
    (doseq [[player-id channel] players]
      (if (fn? message)
        (send! channel (message player-id))
        (send! channel message)))))
