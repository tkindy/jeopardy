(ns build
  (:require [clojure.tools.build.api :as b]))

(def output-dir "target")
(def class-dir (str output-dir "/classes"))
(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean [_]
  (b/delete {:path "target"}))

(defn uber [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis @basis
                  :src-dirs ["src"]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file (str output-dir "/jeopardy.jar")
           :basis @basis
           :main 'com.tylerkindy.jeopardy.main}))
