(ns dev
  (:require [mount.core :as mount]
            [clojure.tools.namespace.repl :as tn]
            [clojure.java.io :as io]
            [juxt.dirwatch :refer [watch-dir close-watcher]]
            [nrepl.core :as nrepl]))

(defn start []
  (mount/start))

(defn stop []
  (mount/stop))

(defn refresh []
  (mount/stop)
  (tn/refresh :after 'dev/start))

(comment (refresh))

(def paths ["src" "resources"])
(def files (map io/file paths))

(defonce watcher (atom nil))

(defn refresh-over-nrepl []
  (let [port (-> (slurp ".nrepl-port")
                 parse-long)]
    (with-open [conn (nrepl/connect {:port port})]
      (-> (nrepl/client conn 1000)
          (nrepl/message {:op "eval" :code "(dev/refresh)"})))))

(defn auto-refresh []
  (refresh)
  (when-let [w @watcher] (close-watcher w))
  (reset! watcher
          (apply watch-dir (fn [_] (refresh-over-nrepl)) files)))

(comment (auto-refresh))
