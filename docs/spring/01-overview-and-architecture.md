# Spring Backend Overview and Architecture

Version stamp: 2026-04-11  
Backend target: `apps/spring-backend`

## Topology
- HTTP API server runs on `PORT` (default `8080`).
- Socket.IO-compatible Netty server runs on `SOCKET_PORT` (default `8081`).
- PostgreSQL stores users, questions, active rooms, runtime config.
- Flyway migrations run at boot from `db/migration`.
- Actuator exposed on same HTTP port (`/actuator/*`).

## Auth model
- REST auth uses `Authorization: Bearer <firebase_id_token>`.
- Unauthenticated REST allowlist:
  - `GET /api/health`
  - `GET /api/cms/questions-stats`
  - `POST /api/cms/report`
  - `GET /api/clans`
  - `POST /api/iap/validate`
- Socket handshake requires `token` query param by default.
- `ALLOW_INSECURE_SOCKET=false` by default; insecure guest mode only when enabled.

## Realtime engine model
- Room state kept in memory per JVM process.
- Each room gets single-thread executor to serialize room events.
- Scheduler handles phase timeouts, disconnect eviction, end-condition checks.
- Player reconnect maps by uid (`uidToRoom`) to restore session quickly.

## Canonical phase flow
- `waiting`
- `castle_placement`
- `claiming_question`
- `claiming_pick`
- `battle`
- `duel`
- `battle_tiebreaker`
- `ended`

## Observability
- Metrics:
  - `sok.realtime.rejected_events`
  - `sok.realtime.rooms`
  - `sok.realtime.players_online`
- Health:
  - `/api/health` lightweight app health
  - `/actuator/health` full actuator health including `RealtimeHealthIndicator`

## Unsupported or roadmap
- Cross-instance room state sharing (Redis/Kafka) is not implemented.
- Durable replay/event-sourcing not implemented.
- MCQ/estimation banks are in-memory fallback data, not DB-driven yet.

## Last verified commands
- `mvn clean test` (from `apps/spring-backend`)
