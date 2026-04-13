package com.sok.backend.service.payments;

import org.springframework.stereotype.Component;

@Component
public class AppStorePaymentProvider implements PaymentProvider {
  @Override
  public String providerCode() {
    return "app_store";
  }

  @Override
  public PaymentVerificationResult verify(PaymentVerificationRequest request) {
    return new PaymentVerificationResult(false, "not_implemented", "unknown", providerCode());
  }
}
