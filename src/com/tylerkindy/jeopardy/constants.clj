(ns com.tylerkindy.jeopardy.constants
  (:import [java.time Duration]))

(def category-reveal-duration (Duration/ofSeconds 3))
(def max-buzz-duration (Duration/ofSeconds 10))

; Seconds per word
(def reading-speed-wps 4)

(def lock-out-duration (Duration/ofMillis 500))
