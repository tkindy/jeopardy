(ns com.tylerkindy.jeopardy.time
  (:import [java.time LocalDateTime ZoneOffset]))

(defn now []
  (LocalDateTime/now ZoneOffset/UTC))
