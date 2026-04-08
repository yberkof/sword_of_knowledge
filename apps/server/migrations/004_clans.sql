-- Clan system migration

CREATE TABLE IF NOT EXISTS sword_of_knowledge.clans (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL,
  description TEXT,
  leader_id TEXT NOT NULL REFERENCES sword_of_knowledge.users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS sword_of_knowledge.clan_members (
  clan_id UUID NOT NULL REFERENCES sword_of_knowledge.clans(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL REFERENCES sword_of_knowledge.users(id) ON DELETE CASCADE,
  role TEXT NOT NULL DEFAULT 'member',
  joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_kick_at TIMESTAMPTZ,
  last_broadcast_at TIMESTAMPTZ,
  PRIMARY KEY (clan_id, user_id)
);

CREATE INDEX IF NOT EXISTS clan_members_user_id_idx ON sword_of_knowledge.clan_members(user_id);

ALTER TABLE sword_of_knowledge.users ADD COLUMN IF NOT EXISTS clan_id UUID REFERENCES sword_of_knowledge.clans(id) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS sword_of_knowledge.clan_applications (
  clan_id UUID NOT NULL REFERENCES sword_of_knowledge.clans(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL REFERENCES sword_of_knowledge.users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (clan_id, user_id)
);
