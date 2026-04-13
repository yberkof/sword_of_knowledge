CREATE TABLE IF NOT EXISTS sword_of_knowledge.catalog_items (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code TEXT NOT NULL UNIQUE,
  name TEXT NOT NULL,
  item_type TEXT NOT NULL,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  version INT NOT NULL DEFAULT 1,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS sword_of_knowledge.catalog_item_prices (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  item_id UUID NOT NULL REFERENCES sword_of_knowledge.catalog_items(id) ON DELETE CASCADE,
  provider TEXT NOT NULL DEFAULT 'internal',
  region TEXT NOT NULL DEFAULT 'global',
  currency TEXT NOT NULL,
  amount_minor BIGINT NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (item_id, provider, region, currency)
);

CREATE TABLE IF NOT EXISTS sword_of_knowledge.user_entitlements (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id TEXT NOT NULL REFERENCES sword_of_knowledge.users(id) ON DELETE CASCADE,
  item_id UUID NOT NULL REFERENCES sword_of_knowledge.catalog_items(id) ON DELETE CASCADE,
  source TEXT NOT NULL,
  granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (user_id, item_id)
);

CREATE TABLE IF NOT EXISTS sword_of_knowledge.economy_transactions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id TEXT NOT NULL REFERENCES sword_of_knowledge.users(id) ON DELETE CASCADE,
  idempotency_key TEXT NOT NULL,
  tx_type TEXT NOT NULL,
  reason TEXT NOT NULL,
  gold_delta INT NOT NULL DEFAULT 0,
  gems_delta INT NOT NULL DEFAULT 0,
  xp_delta INT NOT NULL DEFAULT 0,
  trophies_delta INT NOT NULL DEFAULT 0,
  ref_id TEXT,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (user_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS sword_knowledge_econ_user_idx
  ON sword_of_knowledge.economy_transactions(user_id);

CREATE TABLE IF NOT EXISTS sword_of_knowledge.payment_transactions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id TEXT NOT NULL REFERENCES sword_of_knowledge.users(id) ON DELETE CASCADE,
  provider TEXT NOT NULL,
  external_txn_id TEXT NOT NULL,
  event_id TEXT,
  product_code TEXT NOT NULL,
  product_type TEXT NOT NULL,
  currency TEXT NOT NULL,
  amount_minor BIGINT NOT NULL,
  state TEXT NOT NULL,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (provider, external_txn_id),
  UNIQUE (provider, event_id)
);

CREATE TABLE IF NOT EXISTS sword_of_knowledge.admin_accounts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  must_change_password BOOLEAN NOT NULL DEFAULT TRUE,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS sword_of_knowledge.admin_sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  admin_id UUID NOT NULL REFERENCES sword_of_knowledge.admin_accounts(id) ON DELETE CASCADE,
  token_hash TEXT NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_used_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS sword_knowledge_admin_sessions_admin_idx
  ON sword_of_knowledge.admin_sessions(admin_id);

INSERT INTO sword_of_knowledge.catalog_items(code, name, item_type, metadata)
VALUES
  ('hammer', 'Hammer', 'consumable', '{}'::jsonb),
  ('shield', 'Shield', 'consumable', '{}'::jsonb),
  ('spyglass', 'Spyglass', 'consumable', '{}'::jsonb),
  ('sword', 'Sword', 'consumable', '{}'::jsonb),
  ('skin_knight_gold', 'Knight Gold Skin', 'skin', '{}'::jsonb)
ON CONFLICT (code) DO NOTHING;

INSERT INTO sword_of_knowledge.catalog_item_prices(item_id, provider, region, currency, amount_minor)
SELECT id, 'internal', 'global', 'GOLD', 500 FROM sword_of_knowledge.catalog_items WHERE code = 'hammer'
ON CONFLICT (item_id, provider, region, currency) DO NOTHING;

INSERT INTO sword_of_knowledge.catalog_item_prices(item_id, provider, region, currency, amount_minor)
SELECT id, 'internal', 'global', 'GOLD', 1200 FROM sword_of_knowledge.catalog_items WHERE code = 'shield'
ON CONFLICT (item_id, provider, region, currency) DO NOTHING;

INSERT INTO sword_of_knowledge.catalog_item_prices(item_id, provider, region, currency, amount_minor)
SELECT id, 'internal', 'global', 'GOLD', 300 FROM sword_of_knowledge.catalog_items WHERE code = 'spyglass'
ON CONFLICT (item_id, provider, region, currency) DO NOTHING;

INSERT INTO sword_of_knowledge.catalog_item_prices(item_id, provider, region, currency, amount_minor)
SELECT id, 'internal', 'global', 'GOLD', 2500 FROM sword_of_knowledge.catalog_items WHERE code = 'sword'
ON CONFLICT (item_id, provider, region, currency) DO NOTHING;

INSERT INTO sword_of_knowledge.catalog_item_prices(item_id, provider, region, currency, amount_minor)
SELECT id, 'stripe', 'global', 'USD', 499 FROM sword_of_knowledge.catalog_items WHERE code = 'skin_knight_gold'
ON CONFLICT (item_id, provider, region, currency) DO NOTHING;
