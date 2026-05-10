CREATE TABLE IF NOT EXISTS sword_of_knowledge.user_question_history (
  user_id TEXT NOT NULL REFERENCES sword_of_knowledge.users(id) ON DELETE CASCADE,
  question_id TEXT NOT NULL,
  question_type TEXT NOT NULL,
  answered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (user_id, question_id, question_type)
);

CREATE TABLE IF NOT EXISTS sword_of_knowledge.matches (
  id TEXT PRIMARY KEY,
  map_id TEXT,
  mode TEXT,
  status TEXT,
  start_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  end_time TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS sword_of_knowledge.match_participants (
  match_id TEXT NOT NULL REFERENCES sword_of_knowledge.matches(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL REFERENCES sword_of_knowledge.users(id) ON DELETE CASCADE,
  place INT,
  score INT,
  xp_earned INT,
  trophies_delta INT,
  PRIMARY KEY (match_id, user_id)
);

CREATE TABLE IF NOT EXISTS sword_of_knowledge.user_sessions (
  user_id TEXT NOT NULL REFERENCES sword_of_knowledge.users(id) ON DELETE CASCADE,
  session_date DATE NOT NULL DEFAULT CURRENT_DATE,
  summary_text TEXT,
  PRIMARY KEY (user_id, session_date)
);
