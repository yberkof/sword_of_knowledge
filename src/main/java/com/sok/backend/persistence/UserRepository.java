package com.sok.backend.persistence;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
  private static final String SCH = "sword_of_knowledge.users";
  private static final String COLS = "id, display_name, username, avatar_url, country_code, title, level, xp, gold, gems, trophies, rank, inventory";
  private final JdbcTemplate jdbc;
  private final UserRowMapper mapper;

  public UserRepository(JdbcTemplate jdbc, UserRowMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

  public Optional<UserRecord> findById(String uid) {
    return jdbc.query("SELECT " + COLS + " FROM " + SCH + " WHERE id = ?", mapper, uid).stream().findFirst();
  }

  public Optional<PasswordCredential> findPasswordCredentialByEmail(String e) {
    return jdbc.query("SELECT id, password_hash FROM " + SCH + " WHERE lower(trim(email)) = ? AND password_hash IS NOT NULL",
        (rs, n) -> new PasswordCredential(rs.getString("id"), rs.getString("password_hash")), e).stream().findFirst();
  }

  public boolean existsEmail(String e) {
    return Optional.ofNullable(jdbc.queryForObject("SELECT COUNT(*)::int FROM " + SCH + " WHERE lower(trim(email)) = ?", Integer.class, e)).orElse(0) > 0;
  }

  public void insertPasswordUser(String id, String d, String u, String e, String h) {
    jdbc.update("INSERT INTO " + SCH + " (" + COLS + ", email, password_hash) VALUES (?, ?, ?, ?, ?, 'Knowledge Knight', 1, 0, 1000, 50, 0, 'Bronze', '[]'::jsonb, ?, ?)", id, d, u, "", "SA", e, h);
  }

  public UserRecord create(String uid, String d, String u, String a) {
    return jdbc.queryForObject("INSERT INTO " + SCH + " (" + COLS + ") VALUES (?, ?, ?, ?, 'SA', 'Knowledge Knight', 1, 0, 1000, 50, 0, 'Bronze', '[]'::jsonb) RETURNING " + COLS, mapper, uid, d, u, a);
  }

  public UserRecord upsertIdentity(String uid, String d, String u, String a) {
    return jdbc.queryForObject("INSERT INTO " + SCH + " (" + COLS + ") VALUES (?, ?, ?, ?, 'SA', 'Knowledge Knight', 1, 0, 1000, 50, 0, 'Bronze', '[]'::jsonb) ON CONFLICT (id) DO UPDATE SET display_name = COALESCE(NULLIF(EXCLUDED.display_name, ''), users.display_name), username = COALESCE(NULLIF(EXCLUDED.username, ''), users.username), avatar_url = COALESCE(NULLIF(EXCLUDED.avatar_url, ''), users.avatar_url), updated_at = NOW() RETURNING " + COLS, mapper, uid, d, u, a);
  }

  public void touchLogin(String uid) { jdbc.update("UPDATE " + SCH + " SET last_login_at = NOW(), active_session_id = ?, updated_at = NOW() WHERE id = ?", UUID.randomUUID().toString(), uid); }
  public int updateProfileFields(String id, String d, String u, String c, String a) { return jdbc.update("UPDATE " + SCH + " SET display_name = ?, username = ?, country_code = ?, avatar_url = ?, updated_at = NOW() WHERE id = ?", d, u, c, a == null ? "" : a, id); }
  public int updateLevelIfHigher(String uid, int l) { return jdbc.update("UPDATE " + SCH + " SET level = GREATEST(level, ?), updated_at = NOW() WHERE id = ?", l, uid); }

  public int purchaseItem(String uid, String itemId, int costGold) {
    return Optional.ofNullable(jdbc.queryForObject("WITH locked AS (SELECT gold FROM " + SCH + " WHERE id = ? FOR UPDATE), updated AS (UPDATE " + SCH + " u SET gold = u.gold - ?, inventory = CASE WHEN u.inventory @> to_jsonb(ARRAY[?]::text[]) THEN u.inventory ELSE u.inventory || to_jsonb(ARRAY[?]::text[]) END, updated_at = NOW() FROM locked l WHERE u.id = ? AND l.gold >= ? RETURNING 1) SELECT COALESCE((SELECT 1 FROM updated LIMIT 1), 0)", Integer.class, uid, costGold, itemId, itemId, uid, costGold)).orElse(0);
  }
}
