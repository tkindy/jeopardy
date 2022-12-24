-- :name insert-game :! :n
INSERT INTO games (id, mode)
VALUES (:id, :mode);

-- :name get-game :? :1
SELECT * FROM games
WHERE id = :id;
