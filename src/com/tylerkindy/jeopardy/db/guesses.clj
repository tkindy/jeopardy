(ns com.tylerkindy.jeopardy.db.guesses
  (:require [hugsql.core :refer [def-db-fns]]))

(def-db-fns "com/tylerkindy/jeopardy/db/sql/guesses.sql")
