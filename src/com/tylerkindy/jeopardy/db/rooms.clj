(ns com.tylerkindy.jeopardy.db.rooms
  (:require [hugsql.core :refer [def-db-fns]]))

(def-db-fns "com/tylerkindy/jeopardy/db/sql/rooms.sql")
