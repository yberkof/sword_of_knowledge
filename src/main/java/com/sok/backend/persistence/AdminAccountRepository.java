package com.sok.backend.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminAccountRepository {
  private static final String SCH = "sword_of_knowledge";
  private final JdbcTemplate jdbcTemplate;

  public AdminAccountRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<AdminAccountRecord> findByEmail(String email) {
    List<AdminAccountRecord> rows =
        jdbcTemplate.query(
            "SELECT id, email, password_hash, must_change_password, is_active FROM "
                + SCH
                + ".admin_accounts WHERE email = ?",
            (rs, n) -> mapRow(rs),
            email.toLowerCase());
    return rows.stream().findFirst();
  }

  public Optional<AdminAccountRecord> findById(UUID id) {
    List<AdminAccountRecord> rows =
        jdbcTemplate.query(
            "SELECT id, email, password_hash, must_change_password, is_active FROM "
                + SCH
                + ".admin_accounts WHERE id = ?",
            (rs, n) -> mapRow(rs),
            id);
    return rows.stream().findFirst();
  }

  public AdminAccountRecord upsertBootstrap(String email, String passwordHash) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO "
            + SCH
            + ".admin_accounts(email, password_hash, must_change_password, is_active)"
            + " VALUES (?, ?, TRUE, TRUE)"
            + " ON CONFLICT (email) DO UPDATE SET password_hash = EXCLUDED.password_hash,"
            + " must_change_password = TRUE, is_active = TRUE, updated_at = NOW()"
            + " RETURNING id, email, password_hash, must_change_password, is_active",
        (rs, n) -> mapRow(rs),
        email.toLowerCase(),
        passwordHash);
  }

  public int updatePassword(UUID adminId, String hash) {
    return jdbcTemplate.update(
        "UPDATE "
            + SCH
            + ".admin_accounts SET password_hash = ?, must_change_password = FALSE, updated_at = NOW() WHERE id = ?",
        hash,
        adminId);
  }

  private AdminAccountRecord mapRow(ResultSet rs) throws SQLException {
    return new AdminAccountRecord(
        UUID.fromString(rs.getString("id")),
        rs.getString("email"),
        rs.getString("password_hash"),
        rs.getBoolean("must_change_password"),
        rs.getBoolean("is_active"));
  }
}
