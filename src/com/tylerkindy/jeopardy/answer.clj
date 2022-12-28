(ns com.tylerkindy.jeopardy.answer
  (:require [clojure.string :as str])
  (:import [java.text Normalizer Normalizer$Form]
           [org.jsoup Jsoup]
           [org.jsoup.safety Safelist]))

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

; TODO: implement edit distance
(defn normalize-answer [answer]
  (-> answer
      strip-html
      transliterate
      strip-symbols
      str/lower-case))
