(ns com.tylerkindy.jeopardy.common)

(def htmx-script [:script {:src "/public/htmx@1.8.4/htmx.min.js"}])
(def htmx-ws-script [:script {:src "/public/htmx@1.8.4/ext/ws.min.js"}])
(def idiomorph-ext-script [:script {:src "/public/idiomorph@0.0.8/idiomorph-ext.min.js"}])

(def scripts (list htmx-script htmx-ws-script idiomorph-ext-script))
