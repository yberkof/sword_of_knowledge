package com.sok.backend.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Maps external identity subjects (Firebase UID, future OIDC {@code sub}, etc.) to canonical {@code
 * users.id}. One row per (provider, subject); {@code user_id} may be re-pointed when linking a
 * Google-only account to an email/password user.
 */
@Repository
public class UserIdentityLinkRepository {
  private static final String SCH = "sword_of_knowledge";
  private final JdbcTemplate jdbcTemplate;

  public UserIdentityLinkRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<String> findUserIdByProviderAndSubject(String provider, String subject) {
    if (provider == null
        || provider.trim().isEmpty()
        || subject == null
        || subject.trim().isEmpty()) {
      return Optional.empty();
    }
    List<String> ids =
        jdbcTemplate.query(
            "SELECT user_id FROM "
                + SCH
                + ".user_identity_links WHERE provider = ? AND subject = ? LIMIT 1",
            (rs, rowNum) -> rs.getString("user_id"),
            provider.trim(),
            subject.trim());
    return ids.stream().findFirst();
  }

  /**
   * Sets canonical {@code user_id} for (provider, subject): updates legacy self-links ({@code
   * user_id = subject}) to {@code canonicalUserId}, otherwise inserts. Fails if another canonical
   * mapping already exists for this subject.
   */
  public void setCanonicalUser(String canonicalUserId, String provider, String subject) {
    if (canonicalUserId == null
        || canonicalUserId.trim().isEmpty()
        || provider == null
        || provider.trim().isEmpty()
        || subject == null
        || subject.trim().isEmpty()) {
      return;
    }
    String c = canonicalUserId.trim();
    String p = provider.trim();
    String s = subject.trim();
    int updated =
        jdbcTemplate.update(
            "UPDATE "
                + SCH
                + ".user_identity_links SET user_id = ? WHERE provider = ? AND subject = ? AND user_id = ?",
            c,
            p,
            s,
            s);
    if (updated > 0) {
      return;
    }
    try {
      jdbcTemplate.update(
          "INSERT INTO " + SCH + ".user_identity_links (user_id, provider, subject) VALUES (?, ?, ?)",
          c,
          p,
          s);
    } catch (DuplicateKeyException ex) {
      throw new IllegalStateException("provider_subject_taken");
    }
  }

}
