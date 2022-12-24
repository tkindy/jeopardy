-- :name insert-player :<!
INSERT INTO players (gameId, name)
VALUES (:game-id, :name)
RETURNING id;

-- :name list-players :? :*
SELECT * FROM players
WHERE gameId = :game-id;
