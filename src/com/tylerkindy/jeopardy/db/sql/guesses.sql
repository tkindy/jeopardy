-- :name insert-guess :! :n
INSERT INTO guesses (clue_id, player_id, guess, correct, guessed_at)
VALUES (:clue-id, :player-id, :guess, :correct, :guessed-at);

-- :name get-guess :? :1
SELECT
  p.name as player,
  g.correct
FROM guesses AS g
JOIN players AS p ON g.player_id = p.id
WHERE g.id = :id;

-- :name get-current-guesses :?
SELECT
  g.id,
  g.guess,
  p.id as player_id,
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

-- :name already-corrected? :? :1
SELECT EXISTS (
  SELECT 1
  FROM guesses
  WHERE clue_id = (
    SELECT id FROM endless_clues
    WHERE game_id = :game-id
    ORDER BY id DESC
    LIMIT 1
  )
    AND overridden
);
