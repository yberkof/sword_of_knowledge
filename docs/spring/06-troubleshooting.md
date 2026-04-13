# Spring Backend Troubleshooting

Version stamp: 2026-04-11

## Auth failures

### Symptom
- REST returns `401 Unauthorized` or `401 Invalid token`.

### Checks
- Ensure bearer token present.
- Ensure token is Firebase ID token, not refresh token.
- Ensure Firebase Admin credentials loaded on server.

## Socket connect rejected

### Symptom
- Socket cannot connect or disconnects instantly.

### Checks
- Verify query `token` exists.
- Verify `CORS_ORIGIN` contains client origin.
- Verify `SOCKET_MAX_CONN_PER_MIN` not exceeded.
- Confirm `ALLOW_INSECURE_SOCKET` expectations.

## Join room rejected

### Symptom
- `join_rejected` event with `reason=room_full`.

### Checks
- Inspect runtime config `maxPlayers`.
- Check stale clients still attached to same room.

## Attack invalid reasons

### Symptom
- `attack_invalid` returned.

### Reason mapping
- `no_room`: room not found.
- `bad_phase`: attack sent outside battle phase.
- `not_your_turn`: attacker uid not current turn player.
- `bad_hex`: target region invalid.
- `own_territory`: attacker already owns region.
- `not_adjacent`: no neighboring owned region to target.

## Reconnect desync

### Symptom
- Player sees stale state after reconnect.

### Checks
- Rejoin with same authenticated uid.
- Consume newest `room_update` as canonical.
- Ensure reconnect delay is below `reconnectGraceSeconds`.

## Timeout race behavior

### Symptom
- Duel/claim resolves before client answer appears.

### Checks
- Compare client send time against `phaseEndsAt`.
- Account for network latency and clock skew.
- Server applies timeout defaults once timer fires.

## DB/migration issues

### Symptom
- Startup fails near Flyway or SQL table access.

### Checks
- Confirm `V1__baseline_schema.sql` and `V2__core_tables_and_runtime_config.sql` applied.
- Confirm DB user can `CREATE EXTENSION pgcrypto`.

## Last verified commands
- `mvn clean test` (from `apps/spring-backend`)
