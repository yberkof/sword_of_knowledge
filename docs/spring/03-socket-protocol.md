# Spring Socket Protocol

Version stamp: 2026-04-12

Socket URL: `ws://<host>:<SOCKET_PORT>/socket.io` (default `8081`)

For **phase order, timers, capture rules, and sample payloads**, see [game-flow-deep-dive.md](./game-flow-deep-dive.md).

## Handshake requirements
- Client sends query `token=<access_jwt>` (same Bearer access token as REST after `/api/auth/exchange` or `/api/auth/refresh`).
- If `ALLOW_INSECURE_SOCKET=true`, optional `uid` query fallback is allowed for local dev only (not production).
- Origin must be in `CORS_ORIGIN` list.
- Per-IP handshake capped by `SOCKET_MAX_CONN_PER_MIN` (default `60`).

## Identity rules
- Server stores authenticated uid in socket session.
- Payload uid fields must match socket uid.
- Mismatch causes immediate disconnect.

## Client -> server events
- `join_matchmaking`
  - fields: `uid`, `name`, `privateCode?`
- `leave_matchmaking`
  - fields: `uid`
- `start_match`
  - fields: `roomId`, `uid` (host only)
- `place_castle`
  - fields: `roomId`, `uid`, `regionId`
- `submit_estimation`
  - fields: `roomId`, `uid`, `value`
- `claim_region`
  - fields: `roomId`, `uid`, `regionId`
- `attack`
  - fields: `roomId`, `attackerUid`, `targetHexId`
- `submit_answer`
  - fields: `roomId`, `uid`, `answerIndex`
- `room_chat`
  - fields: `roomId`, `uid`, `name`, `message`

## Server -> client events
- `room_update`
- `phase_changed`
- `join_rejected` (`reason=room_full`)
- `estimation_question`
- `claim_rankings`
- `duel_start`
- `battle_tiebreaker_start`
- `duel_resolved`
- `attack_invalid` (`reason` codes: `no_room`, `bad_phase`, `not_your_turn`, `bad_hex`, `own_territory`, `not_adjacent`)
- `game_ended`
- `room_chat`

## Room lifecycle
- Room created lazily on matchmaking join.
- Private code room must have invite code length >= 4.
- Room removed when empty.
- After game end, room shutdown scheduled after reconnect grace window.

## Timeout behavior
- Claim question timeout auto-resolves with defaults.
- Duel timeout auto-fills unanswered slots with `answerIndex=-1`.
- Tiebreak timeout auto-fills unanswered numeric values with `0`.

## Unsupported or roadmap
- No cross-node socket session affinity support.
- No built-in spectator channel.

## Last verified commands
- `mvn clean test` (from `apps/spring-backend`)
