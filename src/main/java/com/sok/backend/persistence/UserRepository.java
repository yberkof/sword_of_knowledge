package com.sok.backend.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
  private static final String SCH = "sword_of_knowledge";
  private final JdbcTemplate jdbcTemplate;

  public UserRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<UserRecord> findById(String uid) {
    List<UserRecord> rows =
        jdbcTemplate.query(
            "SELECT id, display_name, username, avatar_url, country_code, title, level, xp, gold, gems,"
                + " trophies, rank, inventory FROM "
                + SCH
                + ".users WHERE id = ?",
            userMapper(),
            uid);
    return rows.stream().findFirst();
  }

  public Optional<PasswordCredential> findPasswordCredentialByEmail(String emailNormalizedLower) {
    List<PasswordCredential> rows =
        jdbcTemplate.query(
            "SELECT id, password_hash FROM "
                + SCH
                + ".users WHERE lower(trim(email)) = ? AND password_hash IS NOT NULL",
            (rs, rowNum) ->
                new PasswordCredential(rs.getString("id"), rs.getString("password_hash")),
            emailNormalizedLower);
    return rows.stream().findFirst();
  }

  public boolean existsEmail(String emailNormalizedLower) {
    Integer n =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*)::int FROM " + SCH + ".users WHERE lower(trim(email)) = ?",
            Integer.class,
            emailNormalizedLower);
    return n != null && n > 0;
  }

  public void insertPasswordUser(
      String id, String displayName, String username, String email, String passwordHash) {
    jdbcTemplate.update(
        "INSERT INTO "
            + SCH
            + ".users (id, display_name, username, email, password_hash, avatar_url, country_code, title, level, xp, gold, gems, trophies, rank, inventory)"
            + " VALUES (?, ?, ?, ?, ?, '', 'SA', 'Knowledge Knight', 1, 0, 1000, 50, 0, 'Bronze', '[]'::jsonb)",
        id,
        displayName,
        username,
        email,
        passwordHash);
  }

  public UserRecord create(String uid, String displayName, String username, String avatar) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO "
            + SCH
            + ".users (id, display_name, username, avatar_url, country_code, title, level, xp, gold, gems, trophies, rank, inventory)"
            + " VALUES (?, ?, ?, ?, 'SA', 'Knowledge Knight', 1, 0, 1000, 50, 0, 'Bronze', '[]'::jsonb)"
            + " RETURNING id, display_name, username, avatar_url, country_code, title, level, xp, gold, gems, trophies, rank, inventory",
        userMapper(),
        uid,
        displayName,
        username,
        avatar);
  }

  public UserRecord upsertIdentity(String uid, String displayName, String username, String avatar) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO "
            + SCH
            + ".users (id, display_name, username, avatar_url, country_code, title, level, xp, gold, gems, trophies, rank, inventory)"
            + " VALUES (?, ?, ?, ?, 'SA', 'Knowledge Knight', 1, 0, 1000, 50, 0, 'Bronze', '[]'::jsonb)"
            + " ON CONFLICT (id) DO UPDATE SET "
            + " display_name = COALESCE(NULLIF(EXCLUDED.display_name, ''), "
            + SCH
            + ".users.display_name),"
            + " username = COALESCE(NULLIF(EXCLUDED.username, ''), "
            + SCH
            + ".users.username),"
            + " avatar_url = COALESCE(NULLIF(EXCLUDED.avatar_url, ''), "
            + SCH
            + ".users.avatar_url),"
            + " updated_at = NOW()"
            + " RETURNING id, display_name, username, avatar_url, country_code, title, level, xp, gold, gems, trophies, rank, inventory",
        userMapper(),
        uid,
        displayName,
        username,
        avatar);
  }

  public void touchLogin(String uid) {
    jdbcTemplate.update(
        "UPDATE "
            + SCH
            + ".users SET last_login_at = NOW(), active_session_id = ?, updated_at = NOW() WHERE id = ?",
        UUID.randomUUID().toString(),
        uid);
  }

  public int updateProfileFields(
      String id, String displayName, String username, String countryCode, String avatarUrl) {
    return jdbcTemplate.update(
        "UPDATE "
            + SCH
            + ".users SET display_name = ?, username = ?, country_code = ?, avatar_url = ?,"
            + " updated_at = NOW() WHERE id = ?",
        displayName,
        username,
        countryCode,
        avatarUrl == null ? "" : avatarUrl,
        id);
  }

  public int purchaseItem(String uid, String itemId, int costGold) {
    Integer updated =
        jdbcTemplate.queryForObject(
            "WITH locked AS (SELECT gold, inventory FROM "
                + SCH
                + ".users WHERE id = ? FOR UPDATE), "
                + "updated AS (UPDATE "
                + SCH
                + ".users u "
                + "SET gold = u.gold - ?, inventory = CASE "
                + "  WHEN u.inventory @> to_jsonb(ARRAY[?]::text[]) THEN u.inventory "
                + "  ELSE u.inventory || to_jsonb(ARRAY[?]::text[]) END, "
                + "updated_at = NOW() "
                + "FROM locked l WHERE u.id = ? AND l.gold >= ? RETURNING 1) "
                + "SELECT COALESCE((SELECT 1 FROM updated LIMIT 1), 0)",
            Integer.class,
            uid,
            costGold,
            itemId,
            itemId,
            uid,
            costGold);
    return updated == null ? 0 : updated;
  }

  public int updateLevelIfHigher(String uid, int level) {
    return jdbcTemplate.update(
        "UPDATE " + SCH + ".users SET level = GREATEST(level, ?), updated_at = NOW() WHERE id = ?",
        level,
        uid);
  }

  private RowMapper<UserRecord> userMapper() {
    return (rs, rowNum) -> mapRow(rs);
  }

  private UserRecord mapRow(ResultSet rs) throws SQLException {
    return new UserRecord(
        rs.getString("id"),
        rs.getString("display_name"),
        rs.getString("username"),
        rs.getString("avatar_url"),
        rs.getString("country_code"),
        rs.getString("title"),
        rs.getInt("level"),
        rs.getInt("xp"),
        rs.getInt("gold"),
        rs.getInt("gems"),
        rs.getInt("trophies"),
        rs.getString("rank"),
        rs.getString("inventory"));
  }
}
