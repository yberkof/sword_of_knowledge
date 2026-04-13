package com.sok.backend.service.payments;

public interface PaymentProvider {
  String providerCode();
  PaymentVerificationResult verify(PaymentVerificationRequest request);
}
