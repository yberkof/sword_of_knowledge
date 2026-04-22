-- Durable active match snapshots + metadata for reconnect / restart rehydration.

ALTER TABLE sword_of_knowledge.active_rooms
  ADD COLUMN IF NOT EXISTS snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  ADD COLUMN IF NOT EXISTS host_uid TEXT,
  ADD COLUMN IF NOT EXISTS match_mode TEXT,
  ADD COLUMN IF NOT EXISTS ruleset_id TEXT,
  ADD COLUMN IF NOT EXISTS match_started_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS map_id TEXT,
  ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS sword_knowledge_active_rooms_player_uids_idx
  ON sword_of_knowledge.active_rooms USING GIN (player_uids);

CREATE INDEX IF NOT EXISTS sword_knowledge_active_rooms_updated_at_idx
  ON sword_of_knowledge.active_rooms (updated_at);
