package com.sok.backend.service.payments;

public class PaymentVerificationRequest {
  private final String externalTransactionId;
  private final String productCode;
  private final String userId;
  private final long amountMinor;
  private final String currency;
  private final String rawPayload;
  private final String signature;
  private final String eventId;

  public PaymentVerificationRequest(
      String externalTransactionId,
      String productCode,
      String userId,
      long amountMinor,
      String currency,
      String rawPayload,
      String signature,
      String eventId) {
    this.externalTransactionId = externalTransactionId;
    this.productCode = productCode;
    this.userId = userId;
    this.amountMinor = amountMinor;
    this.currency = currency;
    this.rawPayload = rawPayload;
    this.signature = signature;
    this.eventId = eventId;
  }

  public String externalTransactionId() { return externalTransactionId; }
  public String productCode() { return productCode; }
  public String userId() { return userId; }
  public long amountMinor() { return amountMinor; }
  public String currency() { return currency; }
  public String rawPayload() { return rawPayload; }
  public String signature() { return signature; }
  public String eventId() { return eventId; }
}
