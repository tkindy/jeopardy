(ns com.tylerkindy.jeopardy.players
  (:require [compojure.core :refer [defroutes POST]]
            [com.tylerkindy.jeopardy.config :refer [config]]
            [com.tylerkindy.jeopardy.db.players :refer [insert-player]]
            [com.tylerkindy.jeopardy.db.core :refer [ds]]
            [hiccup.util :refer [escape-html]]))

(defn create-player [{:keys [params session]}]
  (let [{:keys [game-id name]} params
        result (insert-player ds {:game-id game-id, :name (escape-html name)})
        id (get-in result [0 :id])
        url (str "/games/" game-id)
        [session url] (if (= (:player-id-spot config) :query-param)
                        [session (str url "?playerId=" id)]
                        [(assoc session :id id) url])]
    {:status 303
     :session session
     :headers {"Location" url}}))

(defroutes player-routes
  (POST "/players" req (create-player req)))
