package com.sok.backend.persistence;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminSessionRepository {
  private static final String SCH = "sword_of_knowledge";
  private final JdbcTemplate jdbcTemplate;

  public AdminSessionRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public AdminSessionRecord create(UUID adminId, String tokenHash, OffsetDateTime expiresAt) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO "
            + SCH
            + ".admin_sessions(admin_id, token_hash, expires_at, last_used_at) VALUES (?, ?, ?, NOW())"
            + " RETURNING id, admin_id, token_hash, expires_at, revoked_at",
        (rs, n) ->
            new AdminSessionRecord(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("admin_id")),
                rs.getString("token_hash"),
                rs.getTimestamp("expires_at").toInstant().atOffset(ZoneOffset.UTC),
                rs.getTimestamp("revoked_at") == null
                    ? null
                    : rs.getTimestamp("revoked_at").toInstant().atOffset(ZoneOffset.UTC)),
        adminId,
        tokenHash,
        Timestamp.from(expiresAt.toInstant()));
  }

  public Optional<AdminSessionRecord> findActiveByHash(String tokenHash) {
    List<AdminSessionRecord> rows =
        jdbcTemplate.query(
            "SELECT id, admin_id, token_hash, expires_at, revoked_at FROM "
                + SCH
                + ".admin_sessions WHERE token_hash = ? AND revoked_at IS NULL AND expires_at > NOW()",
            (rs, n) ->
                new AdminSessionRecord(
                    UUID.fromString(rs.getString("id")),
                    UUID.fromString(rs.getString("admin_id")),
                    rs.getString("token_hash"),
                    rs.getTimestamp("expires_at").toInstant().atOffset(ZoneOffset.UTC),
                    rs.getTimestamp("revoked_at") == null
                        ? null
                        : rs.getTimestamp("revoked_at").toInstant().atOffset(ZoneOffset.UTC)),
            tokenHash);
    return rows.stream().findFirst();
  }

  public int revoke(UUID sessionId) {
    return jdbcTemplate.update(
        "UPDATE " + SCH + ".admin_sessions SET revoked_at = NOW() WHERE id = ? AND revoked_at IS NULL", sessionId);
  }
}
