(ns com.tylerkindy.jeopardy.db.games
  (:require [hugsql.core :refer [def-db-fns]]))

(def-db-fns "com/tylerkindy/jeopardy/db/sql/games.sql")
