-- :name insert-guess :! :n
INSERT INTO guesses (clue_id, player_id, guess, correct, guessed_at)
VALUES (:clue-id, :player-id, :guess, :correct, :guessed-at);

-- :name get-current-guesses :?
SELECT
  g.id,
  g.guess,
  p.name as player,
  g.correct,
  g.overridden
FROM guesses AS g
JOIN players AS p ON g.player_id = p.id
WHERE g.clue_id = (
  SELECT id FROM endless_clues
  WHERE game_id = :game-id
  ORDER BY id DESC
  LIMIT 1
)
ORDER BY g.id;

-- :name override-guess :! :n
UPDATE guesses
SET overridden = TRUE
WHERE id = :id;
