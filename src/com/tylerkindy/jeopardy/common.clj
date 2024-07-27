(ns com.tylerkindy.jeopardy.common
  (:require [hiccup.page :refer [html5]]))

(def htmx-script [:script {:src "/public/htmx@2.0.1/htmx.min.js"}])
(def htmx-ws-script [:script {:src "/public/htmx-ext-ws@2.0.0/ws.min.js"}])
(def idiomorph-ext-script [:script {:src "/public/idiomorph@0.3.0/idiomorph-ext.min.js"}])

(def scripts (list htmx-script htmx-ws-script idiomorph-ext-script))

(defn page
  ([body] (page nil body))
  ([head-elems body]
   (html5
    {:lang :en}
    [:head
     [:title "Jeopardy"]
     [:meta {:name :viewport, :content "width=device-width, initial-scale-1"}]
     head-elems]
    body)))
