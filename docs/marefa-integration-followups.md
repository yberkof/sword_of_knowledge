# Marefa ↔ srf integration — follow-ups

Main integration checklist and **basic** port/name/env decisions live in the Cursor plan: *Marefa–SOK integration* (`marefa–sok_integration_7baff44b.plan.md` in `.cursor/plans/`).

This file holds **deeper contract discussion** (payload shapes, phases, missing handlers) and **non-basic** work items so the plan stays short.

---

## HTTP REST (srf) recap

- **HTTP:** Spring default port **8080** (`PORT` in `application.yml`).
- **Socket.IO:** separate server **8081** (`SOCKET_PORT`), path `/socket.io`.
- **Auth:** JWT on protected routes; public list in `PublicApiPaths.java` (`/api/auth/**`, `/api/health`, …).
- **marefa-game:** no SOK REST client yet; optional `NEXT_PUBLIC_API_BASE_URL` + Bearer after `/api/auth` login/refresh.

See also [spring/02-rest-api.md](spring/02-rest-api.md).

---

## Best practice: front vs back (sole client)

1. **Protocol:** srf uses **netty-socketio 2.0.13+** (Engine.IO v4 capable). Marefa may use **socket.io-client v4**; keep server on **≥2.0.13**. Load scripts may still use **socket.io-client v2**—both negotiate with the server.
2. **Naming / JSON drift:** smallest change wins—TS adapter or Java alias/rename; update `scripts/load/socket-soak.js` if Java names change.
3. **Policy / security:** CORS, JWT, `ALLOW_INSECURE_SOCKET` — backend or env only.
4. **Missing game features:** e.g. `use_powerup` — implement on server or hide in UI; do not fake success in prod.
5. **Parallel API versions:** optional until a second client exists.

---

## Marefa vs srf payload reference

Sources: `SocketGateway.roomToClient` / `mcqDuelPayload` / `QuestionEngineService.toClient`, marefa `sok.types.ts`, `sokAdapter.ts`, `WebSocketBackend.ts`.

### `room_update` (`roomToClient` vs `SOKMatchState` / adapter)

| Topic | srf | Marefa |
|--------|-----|--------|
| Scores | `scoreByUid` | Adapter reads `matchScore?.[uid]` only → **0** until mapped. |
| Round | `round` | Adapter uses `battleRound` → **1** until mapped. |
| Phase | `castle_placement`, `claiming_question`, `claiming_pick`, `battle`, `duel`, `battle_tiebreaker`, `ended`, … | `PHASE_MAP` lacks those keys → UI phase **`MATCH_INIT`** for most states. |
| `mapId` | omitted | Defaults `basic_1v1_map`. |
| Players | same core fields | OK; adapter ignores trophies / online / `castleRegionId`. |
| `mapState` | includes `points` | `HexData` has no `points` — ignored. `type` union may need normalization. |
| `activeDuel` | string `question` + ids | Types expect full `SOKQuestion`, `questionId`, `startTime`, `answers` — not on snapshot. |
| Claiming | `claimTurnUid`, `claimPicksLeftByUid` | Not in Marefa types — ignore until UI supports claiming. |
| Neighbors | not serialized in `regionsToClient` | `adjacentRegionIds: []` always. |

### Estimation event

| | srf | Marefa |
|---|-----|--------|
| Event | `estimation_question` | Listens `expansion_round_start` |
| Body | `id`, `text`, `serverNowMs`, `phaseEndsAt`, `durationMs` | Expects `questionText`, `durationMs`, `round` |

### `duel_start`

Mostly aligned: `payload.question` has `id`, `text`, `options`, `category`, timing fields. Marefa hard-codes **15s** timeout instead of `durationMs`.

### `duel_resolved`

- `result` includes booleans, `correctIndex`, and **`targetHexId`** (server adds region id for clients).
- Marefa falls back to `payload.room.activeDuel.targetHexId` if needed.

### `game_ended`

Uses `payload.room` through `translateMatchState` — OK once `room_update` mapping is fixed.

### Inbound events Marefa does not handle yet

Examples: `phase_changed`, `claim_rankings`, `battle_tiebreaker_start`, `attack_invalid`, `join_rejected`, `room_chat`.

### Outbound name gaps

- `expansion_submit_number` vs `submit_estimation`
- `use_powerup` — no server handler
- `start_match` — server has it; Marefa `startGame()` commented out

---

## Gameplay / contract backlog (discussion)

- Wire `start_match`, `place_castle`, `claim_region` from UI when you want full match flow beyond duel listeners.
- Powerups: server contract TBD or hide shields in live mode.
- Optional: add `targetHexId` to `duel_resolved` `result` for simpler Marefa combat UI.
