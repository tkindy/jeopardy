(ns com.tylerkindy.jeopardy.answer
  (:import [org.jsoup Jsoup]
           [org.jsoup.safety Safelist]))

(defn strip-html [answer]
  (Jsoup/clean answer (Safelist/none)))

; TODO: implement edit distance
(defn normalize-answer [answer]
  (-> answer
      strip-html))
