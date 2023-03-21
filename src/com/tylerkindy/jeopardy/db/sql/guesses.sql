-- :name insert-guess :! :n
INSERT INTO guesses (clue_id, player_id, guess, correct, guessed_at)
VALUES (:clue-id, :player-id, :guess, :correct, :guessed-at);
