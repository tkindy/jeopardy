(ns com.tylerkindy.jeopardy.endless.live
  (:require [org.httpkit.server :refer [send!]]))

(defonce live-games (atom {}))

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
