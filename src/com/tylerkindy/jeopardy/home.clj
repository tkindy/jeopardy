(ns com.tylerkindy.jeopardy.home
  (:require [hiccup.page :refer [html5]]))

(defn home []
  (html5
   {:lang :en}
   [:body
    [:h1 "Jeopardy"]

    [:script {:src "/public/htmx.min.js"}]]))
