CREATE TABLE IF NOT EXISTS sword_of_knowledge.auth_sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id TEXT NOT NULL REFERENCES sword_of_knowledge.users(id) ON DELETE CASCADE,
  refresh_hash TEXT NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_used_at TIMESTAMPTZ,
  device_info TEXT,
  ip TEXT
);

CREATE UNIQUE INDEX IF NOT EXISTS sword_knowledge_auth_sessions_refresh_hash_uidx
  ON sword_of_knowledge.auth_sessions(refresh_hash);

CREATE INDEX IF NOT EXISTS sword_knowledge_auth_sessions_user_idx
  ON sword_of_knowledge.auth_sessions(user_id);

CREATE INDEX IF NOT EXISTS sword_knowledge_auth_sessions_expiry_idx
  ON sword_of_knowledge.auth_sessions(expires_at);
