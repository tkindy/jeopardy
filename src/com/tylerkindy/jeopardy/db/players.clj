(ns com.tylerkindy.jeopardy.db.players
  (:require [hugsql.core :refer [def-db-fns]]))

(def-db-fns "com/tylerkindy/jeopardy/db/sql/players.sql")
