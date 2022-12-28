(ns com.tylerkindy.jeopardy.endless.live
  (:require [org.httpkit.server :refer [send!]]))

(defonce live-games (atom {}))

(defn transition! [game-id from-pred to-state]
  (let [new-val (swap! live-games
                       (fn [live-games]
                         (if (from-pred (get-in live-games [game-id :state]))
                           (assoc-in live-games [game-id :state] to-state)
                           live-games)))]
    (= (get-in new-val [game-id :state])
       to-state)))

(defn send-all! [game-id message]
  (let [players (get-in @live-games [game-id :players])]
    (doseq [[player-id channel] players]
      (if (fn? message)
        (send! channel (message player-id))
        (send! channel message)))))
