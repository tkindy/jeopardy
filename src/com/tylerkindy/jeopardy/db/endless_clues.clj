(ns com.tylerkindy.jeopardy.db.endless-clues
  (:require [hugsql.core :refer [def-db-fns]]))

(def-db-fns "com/tylerkindy/jeopardy/db/sql/endless_clues.sql")
