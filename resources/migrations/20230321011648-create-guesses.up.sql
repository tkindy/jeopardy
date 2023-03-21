CREATE TABLE guesses (
  id SERIAL PRIMARY KEY,
  clue_id INTEGER NOT NULL,
  player_id INTEGER NOT NULL,
  guess TEXT NOT NULL,
  correct BOOLEAN NOT NULL,
  guessed_at TIMESTAMP NOT NULL
);
