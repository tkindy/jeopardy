-- :name insert-clue :! :n
INSERT INTO endless_clues (game_id, question, answer, value)
VALUES (:game-id, :question, :answer, :value);

-- :name get-current-clue :? :1
SELECT * FROM endless_clues
WHERE game_id = :game-id
ORDER BY id DESC
LIMIT 1;
