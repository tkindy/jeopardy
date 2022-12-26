(ns com.tylerkindy.jeopardy.home
  (:require [com.tylerkindy.jeopardy.common :refer [page]]
            [ring.util.anti-forgery :refer [anti-forgery-field]]))

(defn home []
  (page
   [:body
    [:h1 "Jeopardy"]

    [:form {:action "/games"
            :method :post}
     (anti-forgery-field)
     [:button "New game"]]]))
