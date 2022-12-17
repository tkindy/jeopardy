(ns com.tylerkindy.jeopardy.home
  (:require [hiccup.page :refer [html5]]))

(defn answer-form [response]
  [:form#answer-form {:ws-send "", :hx-swap-oob "morph"}
   [:input#answer {:name "answer", :autocomplete :off}]
   [:p response]])

(defn home []
  (html5
   {:lang :en}
   [:body
    [:h1 "Jeopardy"]

    [:div {:hx-ext "ws,morph", :ws-connect "/"}
     [:div#clue "Loading..."]
     (answer-form "")]

    [:script {:src "/public/htmx@1.8.4/htmx.min.js"}]
    [:script {:src "/public/htmx@1.8.4/ext/ws.min.js"}]
    [:script {:src "/public/idiomorph@0.0.8/idiomorph-ext.min.js"}]]))
