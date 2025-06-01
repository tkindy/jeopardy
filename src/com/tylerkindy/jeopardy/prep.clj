(ns com.tylerkindy.jeopardy.prep
  (:require [com.tylerkindy.jeopardy.config :refer [config]]
            [clojure.java.shell :refer [sh]])
  (:import [java.io FileInputStream FileOutputStream BufferedOutputStream]
           [java.util.zip GZIPInputStream]))

(defn- gunzip
  [input-path output-path]
  (with-open [in (-> input-path FileInputStream. GZIPInputStream.)
              out (-> output-path FileOutputStream. BufferedOutputStream.)]
    (let [buffer (byte-array 4096)]
      (loop []
        (let [n (.read in buffer)]
          (when (pos? n)
            (.write out buffer 0 n)
            (recur)))))))

(defn prep-question-db []
  (println "Preparing question DB")

  (let [temp-dir (:temp-dir @config)
        key-file (str temp-dir "/key.txt")
        gzip-file (str temp-dir "/jeopardy.db.gz")
        db-file (str temp-dir "/jeopardy.db")]
    (spit key-file (get-in @config [:question-db :key]))

    (let [{:keys [exit err]}
          (sh "age" "--decrypt"
              "-o" gzip-file
              "-i" key-file
              "jeopardy.db.gz.age")]
      (when (not= exit 0)
        (throw (RuntimeException. (str "Error decrypting question DB: " err)))))

    (gunzip gzip-file db-file)

    (println "Prepared question DB to" db-file)))
