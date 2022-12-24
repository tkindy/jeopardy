(ns com.tylerkindy.jeopardy.home
  (:require [hiccup.page :refer [html5]]
            [ring.util.anti-forgery :refer [anti-forgery-field]]))

(defn home []
  (html5
   {:lang :en}
   [:body
    [:h1 "Jeopardy"]

    [:form {:action "/games"
            :method :post}
     (anti-forgery-field)
     [:button "New game"]]]))
