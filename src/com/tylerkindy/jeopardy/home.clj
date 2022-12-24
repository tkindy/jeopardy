(ns com.tylerkindy.jeopardy.home
  (:require [hiccup.page :refer [html5]]))

(defn home []
  (html5
   {:lang :en}
   [:body
    [:h1 "Jeopardy"]

    [:p "Home"]

    [:script {:src "/public/htmx@1.8.4/htmx.min.js"}]
    [:script {:src "/public/htmx@1.8.4/ext/ws.min.js"}]
    [:script {:src "/public/idiomorph@0.0.8/idiomorph-ext.min.js"}]]))
