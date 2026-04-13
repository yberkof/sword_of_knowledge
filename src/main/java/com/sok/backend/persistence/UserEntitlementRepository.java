package com.sok.backend.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserEntitlementRepository {
  private static final String SCH = "sword_of_knowledge";
  private final JdbcTemplate jdbcTemplate;

  public UserEntitlementRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public int grant(String userId, String itemCode, String source) {
    return jdbcTemplate.update(
        "INSERT INTO "
            + SCH
            + ".user_entitlements(user_id, item_id, source)"
            + " SELECT ?, ci.id, ? FROM "
            + SCH
            + ".catalog_items ci WHERE ci.code = ?"
            + " ON CONFLICT (user_id, item_id) DO NOTHING",
        userId,
        source,
        itemCode);
  }
}
