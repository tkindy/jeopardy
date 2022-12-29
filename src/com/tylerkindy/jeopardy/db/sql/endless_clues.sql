-- :name insert-clue :! :n
INSERT INTO endless_clues (game_id, category, question, answer, value)
VALUES (:game-id, :category, :question, :answer, :value);

-- :name get-current-clue :? :1
SELECT * FROM endless_clues
WHERE game_id = :game-id
ORDER BY id DESC
LIMIT 1;

-- :name get-last-answer :? :1
SELECT answer FROM endless_clues
WHERE game_id = :game-id
ORDER BY id DESC
LIMIT 1
OFFSET 1;
