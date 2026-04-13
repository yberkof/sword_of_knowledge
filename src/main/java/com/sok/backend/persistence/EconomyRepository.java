package com.sok.backend.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class EconomyRepository {
  private static final String SCH = "sword_of_knowledge";
  private final JdbcTemplate jdbcTemplate;

  public EconomyRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional
  public boolean applyTransaction(
      String userId,
      String idempotencyKey,
      String txType,
      String reason,
      int goldDelta,
      int gemsDelta,
      int xpDelta,
      int trophiesDelta,
      String refId,
      String metadataJson) {
    Integer exists =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM "
                + SCH
                + ".economy_transactions WHERE user_id = ? AND idempotency_key = ?",
            Integer.class,
            userId,
            idempotencyKey);
    if (exists != null && exists > 0) {
      return true;
    }

    Integer updated =
        jdbcTemplate.queryForObject(
            "WITH locked AS (SELECT id, gold, gems FROM "
                + SCH
                + ".users WHERE id = ? FOR UPDATE),"
                + " changed AS (UPDATE "
                + SCH
                + ".users u SET gold = u.gold + ?, gems = u.gems + ?, xp = u.xp + ?, trophies = u.trophies + ?,"
                + " updated_at = NOW() FROM locked l"
                + " WHERE u.id = l.id AND (u.gold + ?) >= 0 AND (u.gems + ?) >= 0 RETURNING u.id)"
                + " INSERT INTO "
                + SCH
                + ".economy_transactions(user_id, idempotency_key, tx_type, reason, gold_delta, gems_delta, xp_delta, trophies_delta, ref_id, metadata)"
                + " SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb FROM changed RETURNING 1",
            Integer.class,
            userId,
            goldDelta,
            gemsDelta,
            xpDelta,
            trophiesDelta,
            goldDelta,
            gemsDelta,
            userId,
            idempotencyKey,
            txType,
            reason,
            goldDelta,
            gemsDelta,
            xpDelta,
            trophiesDelta,
            refId,
            metadataJson == null || metadataJson.trim().isEmpty() ? "{}" : metadataJson);
    return updated != null && updated == 1;
  }
}
