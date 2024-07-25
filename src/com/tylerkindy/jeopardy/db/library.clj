(ns com.tylerkindy.jeopardy.db.library
  (:require [hugsql.core :refer [def-db-fns]]))

(def-db-fns "com/tylerkindy/jeopardy/db/sql/library.sql")
