package com.sok.backend.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuthSessionRepository {
  private static final String SCH = "sword_of_knowledge";
  private final JdbcTemplate jdbcTemplate;

  public AuthSessionRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public AuthSessionRecord create(
      UUID id,
      String userId,
      String refreshHash,
      OffsetDateTime expiresAt,
      String deviceInfo,
      String ip) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO "
            + SCH
            + ".auth_sessions (id, user_id, refresh_hash, expires_at, device_info, ip, last_used_at)"
            + " VALUES (?, ?, ?, ?, ?, ?, NOW())"
            + " RETURNING id, user_id, refresh_hash, expires_at, revoked_at, created_at, last_used_at, device_info, ip",
        (rs, n) -> mapRow(rs),
        id,
        userId,
        refreshHash,
        Timestamp.from(expiresAt.toInstant()),
        deviceInfo,
        ip);
  }

  public Optional<AuthSessionRecord> findById(UUID id) {
    List<AuthSessionRecord> rows =
        jdbcTemplate.query(
            "SELECT id, user_id, refresh_hash, expires_at, revoked_at, created_at, last_used_at, device_info, ip "
                + "FROM "
                + SCH
                + ".auth_sessions WHERE id = ?",
            (rs, n) -> mapRow(rs),
            id);
    return rows.stream().findFirst();
  }

  public int rotate(UUID id, String newRefreshHash, OffsetDateTime newExpiresAt) {
    return jdbcTemplate.update(
        "UPDATE "
            + SCH
            + ".auth_sessions SET refresh_hash = ?, expires_at = ?, last_used_at = NOW() "
            + "WHERE id = ? AND revoked_at IS NULL",
        newRefreshHash,
        Timestamp.from(newExpiresAt.toInstant()),
        id);
  }

  public int revokeById(UUID id) {
    return jdbcTemplate.update(
        "UPDATE " + SCH + ".auth_sessions SET revoked_at = NOW() WHERE id = ? AND revoked_at IS NULL", id);
  }

  public int revokeAllByUser(String userId) {
    return jdbcTemplate.update(
        "UPDATE " + SCH + ".auth_sessions SET revoked_at = NOW() WHERE user_id = ? AND revoked_at IS NULL",
        userId);
  }

  private AuthSessionRecord mapRow(ResultSet rs) throws SQLException {
    Timestamp expires = rs.getTimestamp("expires_at");
    Timestamp revoked = rs.getTimestamp("revoked_at");
    Timestamp created = rs.getTimestamp("created_at");
    Timestamp lastUsed = rs.getTimestamp("last_used_at");
    return new AuthSessionRecord(
        UUID.fromString(rs.getString("id")),
        rs.getString("user_id"),
        rs.getString("refresh_hash"),
        expires == null ? null : expires.toInstant().atOffset(ZoneOffset.UTC),
        revoked == null ? null : revoked.toInstant().atOffset(ZoneOffset.UTC),
        created == null ? null : created.toInstant().atOffset(ZoneOffset.UTC),
        lastUsed == null ? null : lastUsed.toInstant().atOffset(ZoneOffset.UTC),
        rs.getString("device_info"),
        rs.getString("ip"));
  }
}
