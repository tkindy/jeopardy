-- :name insert-game :! :n
INSERT INTO games (id, mode, created_at)
VALUES (:id, :mode, :created-at);

-- :name get-game :? :1
SELECT * FROM games
WHERE id = :id;
