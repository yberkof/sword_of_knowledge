package com.sok.backend.service.payments;

public class PaymentVerificationResult {
  private final boolean valid;
  private final String state;
  private final String productType;
  private final String provider;

  public PaymentVerificationResult(boolean valid, String state, String productType, String provider) {
    this.valid = valid;
    this.state = state;
    this.productType = productType;
    this.provider = provider;
  }

  public boolean valid() { return valid; }
  public String state() { return state; }
  public String productType() { return productType; }
  public String provider() { return provider; }
}
