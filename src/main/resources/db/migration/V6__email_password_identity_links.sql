ALTER TABLE sword_of_knowledge.users
  ADD COLUMN IF NOT EXISTS email TEXT,
  ADD COLUMN IF NOT EXISTS password_hash TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS sword_knowledge_users_email_lower_uidx
  ON sword_of_knowledge.users (lower(trim(email)))
  WHERE email IS NOT NULL AND trim(email) <> '';

CREATE TABLE IF NOT EXISTS sword_of_knowledge.user_identity_links (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id TEXT NOT NULL REFERENCES sword_of_knowledge.users(id) ON DELETE CASCADE,
  provider TEXT NOT NULL,
  subject TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT sword_knowledge_identity_provider_subject_uidx UNIQUE (provider, subject)
);

CREATE INDEX IF NOT EXISTS sword_knowledge_identity_links_user_idx
  ON sword_of_knowledge.user_identity_links (user_id);
