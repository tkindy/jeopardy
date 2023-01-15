(ns com.tylerkindy.jeopardy.constants
  (:import [java.time Duration]))

(def category-reveal-duration (Duration/ofSeconds 3))
(def max-buzz-duration (Duration/ofSeconds 10))
