# Operations and Production Checklist

Version stamp: 2026-04-11

## Required environment
- `DATABASE_URL`
- `DB_USER`
- `DB_PASSWORD`
- `PORT`
- `SOCKET_HOST`
- `SOCKET_PORT`
- `CORS_ORIGIN`
- `ALLOW_INSECURE_SOCKET=false`
- `SOCKET_MAX_CONN_PER_MIN`
- `ADMIN_UIDS`
- `GAME_CONFIG_REFRESH_MS`

## Build and run
- Build/test: `mvn clean test`
- Run local: `mvn spring-boot:run`

## Health checks
- API health: `GET /api/health`
- Actuator: `GET /actuator/health`
- Metrics: `GET /actuator/metrics`
- Prometheus: `GET /actuator/prometheus`

## Runtime controls
- Update runtime game settings via:
  - `GET /api/admin/game-config`
  - `PUT /api/admin/game-config`
- Restrict admin uids in `ADMIN_UIDS`.

## Scaling boundaries
- Current room state is in-process memory.
- Horizontal scaling requires sticky sessions or shared state layer.
- Safe default: single Spring instance for realtime traffic.

## Incident checklist
- Verify DB reachable and Flyway succeeded.
- Verify `ALLOW_INSECURE_SOCKET` still `false`.
- Verify origin list matches deployed frontend origins.
- Check `sok.realtime.rejected_events` for event drops.
- Check executor count vs room count in realtime health details.

## Unsupported or roadmap
- Distributed room ownership not implemented.
- Automated DDoS/abuse mitigation beyond per-IP handshake cap not implemented.

## Last verified commands
- `mvn clean test` (from `apps/spring-backend`)
