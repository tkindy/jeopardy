-- :name insert-player :<!
INSERT INTO players (game_id, name)
VALUES (:game-id, :name)
RETURNING id;

-- :name list-players :? :*
SELECT * FROM players
WHERE game_id = :game-id;
