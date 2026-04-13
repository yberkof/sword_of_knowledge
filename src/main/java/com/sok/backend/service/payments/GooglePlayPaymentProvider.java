package com.sok.backend.service.payments;

import org.springframework.stereotype.Component;

@Component
public class GooglePlayPaymentProvider implements PaymentProvider {
  @Override
  public String providerCode() {
    return "google_play";
  }

  @Override
  public PaymentVerificationResult verify(PaymentVerificationRequest request) {
    return new PaymentVerificationResult(false, "not_implemented", "unknown", providerCode());
  }
}
