(ns com.tylerkindy.jeopardy.answer
  (:require [clojure.string :as str]
            [com.tylerkindy.jeopardy.multiset :as ms])
  (:import [java.text Normalizer Normalizer$Form]
           [org.jsoup Jsoup]
           [org.jsoup.safety Safelist]))

; Lots of ideas here from https://github.com/gesteves/trebekbot

(defn strip-html [answer]
  (Jsoup/clean answer (Safelist/none)))

; Ported from https://stackoverflow.com/a/16179878/5812203
(defn transliterate [answer]
  (-> answer
      (Normalizer/normalize Normalizer$Form/NFKD)
      ; https://www.unicode.org/versions/Unicode14.0.0/ch04.pdf
      ; Section 4.5, page 173
      (str/replace #"(\p{Lu}|\p{Ll}|\p{Lt}|\p{Lm}|\p{Lo})(?:\p{Mn}|\p{Mc}|\p{Me})+"
                   (fn [[_ letter]] letter))))

(defn strip-symbols [answer]
  (str/replace answer #"[\"'\\]" ""))

(defn convert-entities [answer]
  (str/replace answer #"&amp;|&" "and"))

(defn normalize-answer [answer]
  (-> answer
      strip-html
      transliterate
      strip-symbols
      str/lower-case
      convert-entities))

; White Similarity algorithm
; http://www.catalysoft.com/articles/StrikeAMatch.html
; Adapted to use a multiset to handle for repeated letter pairs
(defn char-pairs [s]
  (->> (str/split s #"[\s-]+")
       (mapcat #(partition 2 1 %))
       ms/multiset))

(defn similarity [s1 s2]
  (if (or (< (count s1) 2)
          (< (count s2) 2))
    (if (= s1 s2) 1 0)
    (let [s1-pairs (char-pairs s1)
          s2-pairs (char-pairs s2)]
      (/ (* 2 (ms/count (ms/intersection s1-pairs s2-pairs)))
         (+ (ms/count s1-pairs) (ms/count s2-pairs))))))

(defn similar? [l r]
  (>= (similarity l r) 0.75))

(defn accepted-answers [answer]
  (->> [answer
        (str/replace answer #"\(.*?\)" "")
        (str/split answer #"\s+or\s+|/")]
       flatten
       set))

(defn correct? [answer guess]
  (let [answer (normalize-answer answer)
        guess (normalize-answer guess)]
    (some #(similar? % guess)
          (accepted-answers answer))))
