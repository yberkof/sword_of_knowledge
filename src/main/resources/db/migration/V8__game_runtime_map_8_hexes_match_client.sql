-- Marefa `basic1v1` map exposes only regions 01–08 (server hex ids 1–8). The V3 seed used
-- hexes 0–12, so ids 0 and 9–12 stayed owner=null forever → allRegionsClaimed never true →
-- claiming_question repeated after picks with no battle transition.
UPDATE sword_of_knowledge.game_runtime_config
SET
  payload =
    payload
    || '{"neighbors":{"1":[2,5,6],"2":[1,3,6,7],"3":[2,4,7,8],"4":[3,8],"5":[1,6],"6":[1,2,5,7],"7":[2,3,6,8],"8":[3,4,7]},"regionPoints":{"1":1,"2":1,"3":1,"4":1,"5":1,"6":2,"7":2,"8":2}}'::jsonb,
  updated_at = NOW()
WHERE id = 1;
