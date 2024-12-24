(ns com.tylerkindy.jeopardy.endless.live
  (:require [clojure.string :as str]
            [com.tylerkindy.jeopardy.db.core :refer [ds]]
            [com.tylerkindy.jeopardy.db.endless-clues :refer [get-current-clue]]
            [com.tylerkindy.jeopardy.db.guesses :refer [get-current-guesses]]
            [hiccup.core :refer [html]]
            [org.httpkit.server :refer [send!]])
  (:import [java.util.concurrent CyclicBarrier]
           [java.util.concurrent Executors]))

(defonce live-games (atom {}))

(defn derive-state [game-id]
  (let [clue (get-current-clue ds {:game-id game-id})
        guesses (get-current-guesses ds {:game-id game-id})]
    (cond
      (not clue)       {:name :no-clue}
      (:answered clue) {:name :showing-answer
                        :attempted (reduce (fn [attempted {:keys [player-id guess correct]}]
                                             (assoc attempted player-id {:guess guess
                                                                         :correct? correct}))
                                           {}
                                           guesses)}
      :else            {:name :open-for-answers
                        :attempted {}})))

(defn build-game [game-id]
  {:id game-id
   :state (derive-state game-id)})

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
                                    (assoc-in live-games [game-id :state]
                                              (to-state (get live-games game-id)))
                                    (assoc-in live-games [game-id :state] to-state))
                                  live-games)))]
    (and (not= old new) (get new game-id))))

(defn send-all! [game-id message]
  (let [players (get-in @live-games [game-id :players])
        barrier (CyclicBarrier. (count players))
        tasks (map (fn [[player-id channel]]
                     (fn []
                       (let [to-send (if (fn? message)
                                       (message player-id)
                                       message)
                             to-send (if (and (sequential? to-send)
                                              (vector? (first to-send)))
                                       (->> to-send
                                            (map (fn [v] (html v)))
                                            (str/join "\n"))
                                       (html to-send))]

                         (.await barrier)
                         (send! channel to-send))))
                   players)
        executor (Executors/newVirtualThreadPerTaskExecutor)]
    (doseq [future (.invokeAll executor tasks)]
      (.get future))
    (.shutdown executor)))
