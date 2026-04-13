CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS sword_of_knowledge.users (
  id TEXT PRIMARY KEY,
  display_name TEXT NOT NULL DEFAULT 'Warrior',
  username TEXT,
  avatar_url TEXT NOT NULL DEFAULT '',
  country_flag TEXT NOT NULL DEFAULT '🇸🇦',
  title TEXT NOT NULL DEFAULT 'Knowledge Knight',
  level INT NOT NULL DEFAULT 1,
  xp INT NOT NULL DEFAULT 0,
  gold INT NOT NULL DEFAULT 1000,
  gems INT NOT NULL DEFAULT 50,
  trophies INT NOT NULL DEFAULT 0,
  rank TEXT NOT NULL DEFAULT 'Bronze',
  matches_played INT NOT NULL DEFAULT 0,
  wins INT NOT NULL DEFAULT 0,
  losses INT NOT NULL DEFAULT 0,
  inventory JSONB NOT NULL DEFAULT '[]'::jsonb,
  last_login_at TIMESTAMPTZ,
  active_session_id TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS sword_of_knowledge.questions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  text TEXT NOT NULL,
  options JSONB NOT NULL,
  correct_index INT NOT NULL,
  category TEXT NOT NULL DEFAULT 'general',
  difficulty TEXT,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS sword_knowledge_questions_category_active_idx
  ON sword_of_knowledge.questions (category) WHERE is_active = TRUE;
CREATE INDEX IF NOT EXISTS sword_knowledge_questions_is_active_idx
  ON sword_of_knowledge.questions (is_active);

CREATE TABLE IF NOT EXISTS sword_of_knowledge.active_rooms (
  room_id TEXT PRIMARY KEY,
  phase TEXT,
  player_uids JSONB,
  invite_code TEXT,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS sword_of_knowledge.game_runtime_config (
  id SMALLINT PRIMARY KEY,
  payload JSONB NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO sword_of_knowledge.game_runtime_config (id, payload)
VALUES (
  1,
  '{
    "minPlayers": 3,
    "maxPlayers": 6,
    "initialCastleHp": 3,
    "claimFirstPicks": 2,
    "claimSecondPicks": 1,
    "duelDurationMs": 10000,
    "claimDurationMs": 18000,
    "tiebreakDurationMs": 12000,
    "maxRounds": 30,
    "maxMatchDurationSeconds": 1800,
    "reconnectGraceSeconds": 90,
    "defaultQuestionCategory": "general",
    "regionPoints": {"0":1,"1":1,"2":1,"3":1,"4":1,"5":1,"6":2,"7":2,"8":2,"9":2,"10":3,"11":3,"12":3},
    "neighbors": {
      "0":[1,5],"1":[0,2,5,6],"2":[1,3,6,7],"3":[2,4,7,8],"4":[3,8,9],
      "5":[0,1,6],"6":[1,2,5,7,10],"7":[2,3,6,8,10,11],"8":[3,4,7,9,11,12],
      "9":[4,8,12],"10":[6,7,11],"11":[7,8,10,12],"12":[8,9,11]
    }
  }'::jsonb
)
ON CONFLICT (id) DO NOTHING;
