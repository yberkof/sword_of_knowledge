# Socket.IO protocol (default namespace `/`)

**Match lifecycle (lobby → expansion → conquest → duel → tiebreaker → end):** [GAME_FLOW.md](./GAME_FLOW.md).

- **Library:** Socket.IO v4 (same major as `socket.io` / `socket.io-client` in this repo).
- **URL:** Same host as the HTTP game server (port **3000** by default). Clients can use a reverse proxy path `/socket.io` (see Vite dev proxy in `apps/web/vite.config.ts`).
- **Auth:** No Socket.IO auth middleware today. Clients send `uid` (and related fields) inside payloads; REST uses Firebase Bearer tokens (`docs/REST_API.md`).

Shared event name constants (partial list): [packages/game-contract/src/socketEvents.ts](../packages/game-contract/src/socketEvents.ts).

## Client → server

- **`leave_matchmaking`** — `{ uid?: string }` removes the player from a **waiting** room.
- **`join_matchmaking`** — `{ uid?: string; name?: string; privateCode?: string | null }` joins or creates a lobby; see implementation in `apps/server/src/game/attachGameServer.ts`.
- **`start_match`** — `{ roomId?: string; uid?: string }` private-room host starts the match when `2–4` players ready.
- **`expansion_submit_number`** — `{ roomId?: string; uid?: string; value?: number }` during expansion question phase.
- **`expansion_pick_hex`** — `{ roomId?: string; uid?: string; hexId?: number }` during expansion pick phase.
- **`room_chat`** — `{ roomId?: string; uid?: string; name?: string; message?: string }`.
- **`attack`** — `{ roomId?: string; attackerUid?: string; targetHexId?: number; category?: string }`.
- **`submit_answer`** — `{ roomId?: string; uid?: string; answerIndex?: number }` during duel.
- **`tiebreaker_vote`** — `{ roomId?: string; uid?: string; gameId?: string }`.
- **`tiebreaker_minefield_place`** — `{ roomId?: string; uid?: string; cells?: number[] }` (exactly three distinct cells).
- **`tiebreaker_minefield_step`** — `{ roomId?: string; uid?: string; cell?: number }`.
- **`tiebreaker_rhythm_submit`** — `{ roomId?: string; uid?: string; sequence?: number[] }` pad indices `0–3`, length must equal server pattern length.
- **`tiebreaker_rps_submit`** — `{ roomId?: string; uid?: string; pick?: number }` with `pick` in `0|1|2`.
- **`tiebreaker_closest_submit`** — `{ roomId?: string; uid?: string; value?: number }`.
- **`use_powerup`** — `{ roomId?: string; uid?: string; powerupType?: string; targetHexId?: number }`.

## Server → client

- **`room_update`** — Full sanitized room snapshot (see below). Emitted very often.
- **`game_start`** — Room enters playable state (`expansion` or `conquest`).
- **`join_rejected`** — e.g. `{ reason: "room_full" }`.
- **`expansion_pick_phase`** — `{ room, pickQueue, rankings }`.
- **`expansion_pick_invalid`** — `{ reason: string }` to requesting socket.
- **`expansion_round_start`** — question timing and copy for expansion round.
- **`expansion_complete`** — `{ room }` before transition to conquest.
- **`room_chat`** — broadcast chat payload with sanitized text.
- **`attack_blocked`** / **`attack_invalid`** — attack failed; invalid is to the socket.
- **`duel_start`** — duel payload (question without revealed answer during duel per server rules).
- **`duel_resolved`** — `{ room, result }` with reveal and outcome.
- **`duel_options_update`**, **`duel_audience_hint`**, **`duel_safety_locked`**, **`duel_spyglass_hint`** — power-up / hint events.
- **`tiebreaker_started`** — `{ games, tieBreaker }` UI meta + server tiebreaker payload.
- **`tiebreaker_game_start`** — selected minigame id + `tieBreaker` snapshot.
- **`tiebreaker_pick_result`** — vote outcome flash for clients.
- **`tiebreaker_minefield_ready`** — mine placement done, play begins.
- **`tiebreaker_rhythm_error`** — `{ code: string }` to submitting socket (`bad_seq_len`, `bad_seq`, etc.).
- **`tiebreaker_rhythm_next`** — `{ pattern: number[] }` new rhythm round; **ordering:** server emits `room_update` **before** this event on `next_round`.
- **`tiebreaker_rps_round`** — round outcome; may include `matchComplete: true` on final round.
- **`tiebreaker_closest_reveal`** — numeric reveal and winner.
- **`game_ended`** — `{ winnerUid, rankings, room }`.

## `room_update` / `MatchState`

- **`phase`:** `waiting` | `expansion` | `conquest` | `duel` | `tiebreaker` | `resolution` | `ended`.
- **`tieBreaker`:** When present, shapes are **filtered** for clients via `tiebreakerClientPayload` in [apps/server/server/tieBreaker.ts](../apps/server/server/tieBreaker.ts) (no internal-only fields).
- **Timers:** Server strips `duelTimerId`, `expansionTimerId`, `tiebreakerPickTimerId` before emit so JSON stays finite (see `roomToClient` in [apps/server/src/game/roomSerialize.ts](../apps/server/src/game/roomSerialize.ts)).

Types: [packages/game-contract/src/types.ts](../packages/game-contract/src/types.ts) (`MatchState` is a best-effort mirror; extend as the server evolves).

## Error / edge behavior

- Invalid moves usually get a socket-specific error event or silent ignore; always keep **`room_update`** subscription as source of truth.
- Reconnect: `join_matchmaking` with same `uid` reattaches to an existing in-memory room if the player record still exists.
