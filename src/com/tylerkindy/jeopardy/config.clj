(ns com.tylerkindy.jeopardy.config
  (:require [mount.core :refer [defstate] :as mount]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.tools.cli :refer [parse-opts]]))

(defstate file-config
  :start (edn/read (java.io.PushbackReader.
                    (io/reader "config.edn"))))

(def cli-options
  [["-p" "--port PORT" "Port number"
    :default 8080
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 65536) "Must be a number between 0 and 65536"]]])

(defstate cli-args
  :start (let [parsed (parse-opts (:cli-args (mount/args)) cli-options)]
           (when (:errors parsed)
             (throw (RuntimeException. (str (:errors parsed)))))
           (:options parsed)))

(defstate config
  :start (assoc-in file-config [:http :port] (:port cli-args)))
