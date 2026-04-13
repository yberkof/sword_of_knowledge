package com.sok.backend.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogRepository {
  private static final String SCH = "sword_of_knowledge";
  private final JdbcTemplate jdbcTemplate;

  public CatalogRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<CatalogItemRecord> findItemByCode(String code) {
    List<CatalogItemRecord> rows =
        jdbcTemplate.query(
            "SELECT id, code, name, item_type, metadata::text, is_active, version FROM "
                + SCH
                + ".catalog_items WHERE code = ?",
            (rs, n) -> mapItem(rs),
            code);
    return rows.stream().findFirst();
  }

  public Optional<CatalogPriceRecord> findActivePrice(UUID itemId, String provider, String region, String currency) {
    List<CatalogPriceRecord> rows =
        jdbcTemplate.query(
            "SELECT id, item_id, provider, region, currency, amount_minor, is_active FROM "
                + SCH
                + ".catalog_item_prices WHERE item_id = ? AND provider = ? AND region = ? AND currency = ? AND is_active = TRUE",
            (rs, n) -> mapPrice(rs),
            itemId,
            provider,
            region,
            currency);
    return rows.stream().findFirst();
  }

  public List<CatalogItemRecord> listItems() {
    return jdbcTemplate.query(
        "SELECT id, code, name, item_type, metadata::text, is_active, version FROM "
            + SCH
            + ".catalog_items ORDER BY created_at DESC",
        (rs, n) -> mapItem(rs));
  }

  public CatalogItemRecord upsertItem(String code, String name, String itemType, String metadataJson, boolean active) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO "
            + SCH
            + ".catalog_items(code, name, item_type, metadata, is_active, version)"
            + " VALUES (?, ?, ?, ?::jsonb, ?, 1)"
            + " ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, item_type = EXCLUDED.item_type,"
            + " metadata = EXCLUDED.metadata, is_active = EXCLUDED.is_active, version = "
            + SCH
            + ".catalog_items.version + 1, updated_at = NOW()"
            + " RETURNING id, code, name, item_type, metadata::text, is_active, version",
        (rs, n) -> mapItem(rs),
        code,
        name,
        itemType,
        metadataJson == null || metadataJson.trim().isEmpty() ? "{}" : metadataJson,
        active);
  }

  public CatalogPriceRecord upsertPrice(
      UUID itemId, String provider, String region, String currency, long amountMinor, boolean active) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO "
            + SCH
            + ".catalog_item_prices(item_id, provider, region, currency, amount_minor, is_active)"
            + " VALUES (?, ?, ?, ?, ?, ?)"
            + " ON CONFLICT (item_id, provider, region, currency) DO UPDATE SET"
            + " amount_minor = EXCLUDED.amount_minor, is_active = EXCLUDED.is_active, updated_at = NOW()"
            + " RETURNING id, item_id, provider, region, currency, amount_minor, is_active",
        (rs, n) -> mapPrice(rs),
        itemId,
        provider,
        region,
        currency,
        amountMinor,
        active);
  }

  private CatalogItemRecord mapItem(ResultSet rs) throws SQLException {
    return new CatalogItemRecord(
        UUID.fromString(rs.getString("id")),
        rs.getString("code"),
        rs.getString("name"),
        rs.getString("item_type"),
        rs.getString("metadata"),
        rs.getBoolean("is_active"),
        rs.getInt("version"));
  }

  private CatalogPriceRecord mapPrice(ResultSet rs) throws SQLException {
    return new CatalogPriceRecord(
        UUID.fromString(rs.getString("id")),
        UUID.fromString(rs.getString("item_id")),
        rs.getString("provider"),
        rs.getString("region"),
        rs.getString("currency"),
        rs.getLong("amount_minor"),
        rs.getBoolean("is_active"));
  }
}
