package com.sok.backend.persistence;

import java.util.UUID;

public class PaymentTransactionRecord {
  private final UUID id;
  private final String userId;
  private final String provider;
  private final String externalTxnId;
  private final String productCode;
  private final String productType;
  private final String currency;
  private final long amountMinor;
  private final String state;

  public PaymentTransactionRecord(
      UUID id,
      String userId,
      String provider,
      String externalTxnId,
      String productCode,
      String productType,
      String currency,
      long amountMinor,
      String state) {
    this.id = id;
    this.userId = userId;
    this.provider = provider;
    this.externalTxnId = externalTxnId;
    this.productCode = productCode;
    this.productType = productType;
    this.currency = currency;
    this.amountMinor = amountMinor;
    this.state = state;
  }

  public UUID id() { return id; }
  public String userId() { return userId; }
  public String provider() { return provider; }
  public String externalTxnId() { return externalTxnId; }
  public String productCode() { return productCode; }
  public String productType() { return productType; }
  public String currency() { return currency; }
  public long amountMinor() { return amountMinor; }
  public String state() { return state; }
}
