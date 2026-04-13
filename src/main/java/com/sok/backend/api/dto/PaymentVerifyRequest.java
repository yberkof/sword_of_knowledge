package com.sok.backend.api.dto;

public class PaymentVerifyRequest {
  private String provider;
  private String externalTransactionId;
  private String productCode;
  private Long amountMinor;
  private String currency;
  private String eventId;
  private String payload;
  private String signature;

  public String getProvider() { return provider; }
  public void setProvider(String provider) { this.provider = provider; }
  public String getExternalTransactionId() { return externalTransactionId; }
  public void setExternalTransactionId(String externalTransactionId) { this.externalTransactionId = externalTransactionId; }
  public String getProductCode() { return productCode; }
  public void setProductCode(String productCode) { this.productCode = productCode; }
  public Long getAmountMinor() { return amountMinor; }
  public void setAmountMinor(Long amountMinor) { this.amountMinor = amountMinor; }
  public String getCurrency() { return currency; }
  public void setCurrency(String currency) { this.currency = currency; }
  public String getEventId() { return eventId; }
  public void setEventId(String eventId) { this.eventId = eventId; }
  public String getPayload() { return payload; }
  public void setPayload(String payload) { this.payload = payload; }
  public String getSignature() { return signature; }
  public void setSignature(String signature) { this.signature = signature; }
}
