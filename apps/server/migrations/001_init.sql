-- Sword of Knowledge — PostgreSQL schema (shared DB: isolated namespace)
-- Keep schema name in sync with APP_DB_SCHEMA in server/pgData.ts

CREATE SCHEMA IF NOT EXISTS sword_of_knowledge;

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
  category TEXT NOT NULL DEFAULT 'عام',
  difficulty TEXT,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index names are global per database; prefix avoids clashes on shared hosts
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
