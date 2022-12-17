(ns com.tylerkindy.jeopardy.home
  (:require [hiccup.page :refer [html5]]))

(defn home []
  (html5
   {:lang :en}
   [:body
    [:h1 "Jeopardy"]

    [:div {:hx-ext :ws, :ws-connect "/"}
     [:div#question "Loading..."]
     [:form#answer {:ws-send ""}
      [:input {:name "answer"}]]]

    [:script {:src "/public/htmx@1.8.4/htmx.min.js"}]
    [:script {:src "/public/htmx@1.8.4/ext/ws.min.js"}]]))
