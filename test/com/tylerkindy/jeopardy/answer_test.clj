(ns com.tylerkindy.jeopardy.answer-test
  (:require [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [com.tylerkindy.jeopardy.answer :as a]))

(defspec similarity-is-reflexive
  (prop/for-all [s gen/string-alphanumeric]
                (= (a/similarity s s) 1)))

(defspec different-answers-are-not-exactly-similar
  (prop/for-all [[s1 s2] (->> (gen/vector gen/string-alphanumeric 2)
                              (gen/such-that (fn [[s1 s2]] (not= s1 s2))))]
                (< (a/similarity s1 s2) 1)))
