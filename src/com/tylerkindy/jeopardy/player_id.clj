(ns com.tylerkindy.jeopardy.player-id
  (:require [com.tylerkindy.jeopardy.config :refer [config]]))

(defn get-player-id [req]
  (if (= (:player-id-spot config) :query-param)
    (some-> req
            (get-in [:query-params "playerId"])
            Integer/parseInt)
    (get-in req [:session :id])))
