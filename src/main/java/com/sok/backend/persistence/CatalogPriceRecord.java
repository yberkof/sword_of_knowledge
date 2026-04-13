package com.sok.backend.persistence;

import java.util.UUID;

public class CatalogPriceRecord {
  private final UUID id;
  private final UUID itemId;
  private final String provider;
  private final String region;
  private final String currency;
  private final long amountMinor;
  private final boolean active;

  public CatalogPriceRecord(
      UUID id,
      UUID itemId,
      String provider,
      String region,
      String currency,
      long amountMinor,
      boolean active) {
    this.id = id;
    this.itemId = itemId;
    this.provider = provider;
    this.region = region;
    this.currency = currency;
    this.amountMinor = amountMinor;
    this.active = active;
  }

  public UUID id() { return id; }
  public UUID itemId() { return itemId; }
  public String provider() { return provider; }
  public String region() { return region; }
  public String currency() { return currency; }
  public long amountMinor() { return amountMinor; }
  public boolean active() { return active; }
}
