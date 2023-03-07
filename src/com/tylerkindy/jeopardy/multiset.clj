(ns com.tylerkindy.jeopardy.multiset
  (:refer-clojure :exclude [conj count])
  (:require [clojure.core :as c]))

(defn multiset
  ([]     (multiset nil))
  ([coll] (-> (group-by identity coll)
              (update-vals c/count))))

(defn conj [ms elem]
  (update ms
          elem
          (fn [old] (inc (or old 0)))))

(defn intersection [ms1 ms2]
  (reduce (fn [acc [k ms1-count]]
            (let [i-count (min ms1-count (get ms2 k 0))]
              (if (zero? i-count)
                acc
                (assoc acc k i-count))))
          {}
          ms1))

(defn count [ms]
  (->> ms
       vals
       (apply +)))
