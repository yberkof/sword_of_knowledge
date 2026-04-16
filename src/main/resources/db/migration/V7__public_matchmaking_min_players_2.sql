-- Align public matchmaking with client default (2-player mode). V3 seeded minPlayers 3, which
-- left pairs stuck in waiting even after the realtime pairing fix.
UPDATE sword_of_knowledge.game_runtime_config
SET payload = jsonb_set(payload, '{minPlayers}', '2'::jsonb, true),
    updated_at = NOW()
WHERE id = 1;
