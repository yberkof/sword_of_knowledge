# Client Integration Guide (Spring Backend)

Version stamp: 2026-04-11

## 1) Bootstrapping
- Set API base: `http://<host>:8080`.
- Set socket base: `http://<host>:8081`.
- Ensure app origin listed in `CORS_ORIGIN`.

## 2) Auth flow
- Sign in user with Firebase client SDK.
- Fetch fresh ID token before API/socket connect.
- REST: send `Authorization: Bearer <token>`.
- Socket: send `token` in query/auth payload.

## 3) Minimal REST sequence
- Call `GET /api/profile`.
- If `404`, call `POST /api/profile`.
- Load optional public data (`/api/clans`, `/api/cms/questions-stats`).

## 4) Minimal matchmaking flow
- Emit `join_matchmaking` with authenticated `uid`.
- Listen for `room_update`.
- Host emits `start_match` when ready.
- Handle phase transitions from `phase_changed`.

## 5) Per-phase responsibilities
- `castle_placement`: emit `place_castle`.
- `claiming_question`: emit `submit_estimation`.
- `claiming_pick`: emit `claim_region` only if `claimTurnUid` is user.
- `battle`: current turn emits `attack`.
- `duel`: duel participants emit `submit_answer`.
- `ended`: display `game_ended` payload, stop room actions.

## 6) Reconnect behavior
- On disconnect, reconnect with fresh Firebase token.
- Re-emit `join_matchmaking` with same uid and room code.
- Backend reattaches player by uid-room mapping.
- Grace eviction window controlled by `reconnectGraceSeconds`.

## 7) Example payloads
```json
{ "uid": "firebase_uid", "name": "Warrior", "privateCode": "ABCD" }
```

```json
{ "roomId": "room_1", "uid": "firebase_uid", "regionId": 6 }
```

```json
{ "roomId": "room_1", "attackerUid": "firebase_uid", "targetHexId": 8 }
```

## 8) Integration cautions
- Never trust cached uid; always derive from active token user.
- Treat `room_update` as source of truth for room state.
- Avoid optimistic local state for turn ownership.

## Unsupported or roadmap
- No versioned socket namespace yet.
- No backward-compat shim for legacy Node-only payload variants.

## Last verified commands
- `mvn clean test` (from `apps/spring-backend`)
