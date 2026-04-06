# Web UI components (`apps/web/src/components`)

Maps each screen component to **REST**, **socket** usage, and primary props. Implementation files are under [apps/web/src/components](../apps/web/src/components).

## `WarRoom.tsx`

- **Purpose:** Lobby / shell before entering live match UI.
- **Socket:** None directly (navigation only).
- **REST:** None.

## `GameSession.tsx`

- **Purpose:** Matchmaking, room lobby, hands off live game to `Battlefield`.
- **Props:** `onBack`.
- **Socket:** `io(getSocketUrl())` — emits `join_matchmaking`, `leave_matchmaking`, `start_match`; listens `room_update`, `game_start`.
- **REST:** None.

## `Battlefield.tsx`

- **Purpose:** Main board, expansion, duels, tiebreaker overlay, chat, power-ups.
- **Props:** `gameSocket`, `initialMatch`, `onEnd`.
- **Socket:** Uses prop `gameSocket` — `attack`, `submit_answer`, `use_powerup`, `room_chat`, `expansion_pick_hex`, `expansion_submit_number`; listens `room_update`, `expansion_*`, `duel_*`, `tiebreaker_*`, `game_ended`, etc.
- **REST:** None during battle.

## `TiebreakerOverlay.tsx`

- **Purpose:** Vote + minigame UI when `match.tieBreaker` active.
- **Props:** `socket`, `roomId`, `match`, `myUid`.
- **Socket:** `tiebreaker_vote`, minefield / rhythm / RPS / closest events; listens `tiebreaker_*`, `tiebreaker_rhythm_next`, `tiebreaker_rhythm_error`.
- **REST:** None.
- **Shared util:** `@sok/shared` `coerceRhythmPads` for pattern coercion.

## `ClosestRevealOverlay.tsx`

- **Purpose:** Full-screen reveal after closest-number tiebreaker.
- **Props:** Driven by parent / `closestReveal` state in `Battlefield`.
- **Socket:** Indirect (parent listens `tiebreaker_closest_reveal`).
- **REST:** None.

## `Armory.tsx`

- **Purpose:** Shop UI stub.
- **Props:** `onBack`.
- **REST:** `POST /api/shop/purchase` via `apiUrl()` ([apiConfig.ts](../apps/web/src/apiConfig.ts)).
- **Socket:** None.

## `Records.tsx`

- **Purpose:** Leaderboard / records presentation (static/mock data paths may exist).
- **Socket / REST:** Check file for future API hooks.

## `WorldMapBoard.tsx`

- **Purpose:** Hex/region rendering helpers for the battlefield.
- **Socket / REST:** None (presentational).

## App shell

- **`App.tsx`:** Auth, profile bootstrap — `GET`/`POST` `/api/profile` with Bearer token; navigates between screens.
- **`src/apiConfig.ts`:** `apiUrl`, `getSocketUrl` for split dev/prod.
- **`src/types.ts`:** Re-exports `MatchState` and related types from `@sok/game-contract`.

## Related docs

- [GAME_FLOW.md](./GAME_FLOW.md) — end-to-end socket phases
- [SOCKET_PROTOCOL.md](./SOCKET_PROTOCOL.md)
