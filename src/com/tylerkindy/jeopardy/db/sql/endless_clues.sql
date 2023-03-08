-- :name insert-clue :! :n
INSERT INTO endless_clues (game_id, category, airdate, question, answer, value)
VALUES (:game-id, :category, :airdate, :question, :answer, :value);

-- :name get-current-clue :? :1
SELECT * FROM endless_clues
WHERE game_id = :game-id
ORDER BY id DESC
LIMIT 1;

-- :name mark-answered :! :n
UPDATE endless_clues
SET answered = true
WHERE id = (
  SELECT id FROM endless_clues
  WHERE game_id = :game-id
  ORDER BY id DESC
  LIMIT 1
);
