-- Prefer multi-game vote lobby over legacy single-mode avoid-bombs when unset or explicitly avoid-bombs.
UPDATE sword_of_knowledge.game_runtime_config
SET
  payload =
    CASE
      WHEN payload->>'tieBreakerMode' IS NULL THEN
        payload || jsonb_build_object('tieBreakerMode', 'minigame_collection')
      WHEN payload->>'tieBreakerMode' = 'minigame_avoid_bombs' THEN
        payload || jsonb_build_object('tieBreakerMode', 'minigame_collection')
      ELSE payload
    END,
  updated_at = NOW()
WHERE id = 1;
