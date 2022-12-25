CREATE TABLE endless_clues (
  id SERIAL PRIMARY KEY,
  game_id VARCHAR(6) NOT NULL,
  question TEXT NOT NULL,
  answer TEXT NOT NULL,
  value INTEGER NOT NULL
);
