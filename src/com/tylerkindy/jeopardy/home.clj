(ns com.tylerkindy.jeopardy.home
  (:require [hiccup.page :refer [html5]]
            [com.tylerkindy.jeopardy.common :refer [scripts]]))

(defn home []
  (html5
   {:lang :en}
   [:body
    [:h1 "Jeopardy"]

    [:p "Home"]

    scripts]))
