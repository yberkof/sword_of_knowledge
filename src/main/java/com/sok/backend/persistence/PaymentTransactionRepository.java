package com.sok.backend.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentTransactionRepository {
  private static final String SCH = "sword_of_knowledge";
  private final JdbcTemplate jdbcTemplate;

  public PaymentTransactionRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<PaymentTransactionRecord> findByProviderExternal(String provider, String externalTxnId) {
    List<PaymentTransactionRecord> rows =
        jdbcTemplate.query(
            "SELECT id, user_id, provider, external_txn_id, product_code, product_type, currency, amount_minor, state "
                + "FROM "
                + SCH
                + ".payment_transactions WHERE provider = ? AND external_txn_id = ?",
            (rs, n) -> mapRow(rs),
            provider,
            externalTxnId);
    return rows.stream().findFirst();
  }

  public PaymentTransactionRecord upsert(
      String userId,
      String provider,
      String externalTxnId,
      String eventId,
      String productCode,
      String productType,
      String currency,
      long amountMinor,
      String state,
      String metadataJson) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO "
            + SCH
            + ".payment_transactions(user_id, provider, external_txn_id, event_id, product_code, product_type, currency, amount_minor, state, metadata)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)"
            + " ON CONFLICT (provider, external_txn_id) DO UPDATE SET"
            + " event_id = EXCLUDED.event_id,"
            + " state = EXCLUDED.state,"
            + " metadata = EXCLUDED.metadata,"
            + " updated_at = NOW()"
            + " RETURNING id, user_id, provider, external_txn_id, product_code, product_type, currency, amount_minor, state",
        (rs, n) -> mapRow(rs),
        userId,
        provider,
        externalTxnId,
        eventId,
        productCode,
        productType,
        currency,
        amountMinor,
        state,
        metadataJson == null || metadataJson.trim().isEmpty() ? "{}" : metadataJson);
  }

  private PaymentTransactionRecord mapRow(ResultSet rs) throws SQLException {
    return new PaymentTransactionRecord(
        UUID.fromString(rs.getString("id")),
        rs.getString("user_id"),
        rs.getString("provider"),
        rs.getString("external_txn_id"),
        rs.getString("product_code"),
        rs.getString("product_type"),
        rs.getString("currency"),
        rs.getLong("amount_minor"),
        rs.getString("state"));
  }
}
