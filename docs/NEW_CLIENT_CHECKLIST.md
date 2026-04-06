# New client integration checklist

1. **Match protocol versions** — Use Socket.IO **v4**-compatible client against the game server. Event names and semantics: [docs/SOCKET_PROTOCOL.md](./SOCKET_PROTOCOL.md). For phase-by-phase interaction, read [docs/GAME_FLOW.md](./GAME_FLOW.md).
2. **Share types (optional)** — TypeScript clients can depend on workspace package `@sok/game-contract` (`MatchState`, socket event name lists) from [packages/game-contract](../packages/game-contract).
3. **REST auth** — Obtain a Firebase ID token (same project as `firebase-applet-config.json`) for `/api/profile` and shop routes: [docs/REST_API.md](./REST_API.md).
4. **Connect socket** — After sign-in, open socket to server origin (or proxied origin in dev). Subscribe to **`room_update`** as the authoritative state snapshot.
5. **Join flow** — Emit **`join_matchmaking`** with `{ uid, name, privateCode? }`. Wait for **`game_start`** and/or **`room_update`** with `phase !== 'waiting'`.
6. **Gameplay** — Issue **`attack`**, answer **`submit_answer`** during duels, handle **`duel_resolved`** and **`tiebreaker_*`** branches per [SOCKET_PROTOCOL.md](./SOCKET_PROTOCOL.md).
7. **Rhythm tiebreaker** — Honor **`tiebreaker_rhythm_next`** after **`room_update`**; submit **`tiebreaker_rhythm_submit`** with integer array in `0..3` matching pattern length.
8. **Environment** — For a separate static front-end, set `VITE_API_BASE_URL` / `VITE_SOCKET_URL` (or equivalent) to the public game server URL and enable CORS on the server (`CORS_ORIGIN`).
