package com.sok.backend.service.payments;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class StripePaymentProviderTest {
  @Test
  void invalidSignatureFails() {
    StripePaymentProvider provider = new StripePaymentProvider("secret");
    PaymentVerificationResult result =
        provider.verify(
            new PaymentVerificationRequest(
                "txn_1",
                "skin_knight_gold",
                "u1",
                499,
                "USD",
                "{\"t\":1}",
                "bad",
                "evt_1"));
    Assertions.assertFalse(result.valid());
  }

  @Test
  void simpleNoPayloadValidationPasses() {
    StripePaymentProvider provider = new StripePaymentProvider("secret");
    PaymentVerificationResult result =
        provider.verify(
            new PaymentVerificationRequest(
                "txn_2",
                "gems_pack_1",
                "u1",
                199,
                "USD",
                null,
                null,
                "evt_2"));
    Assertions.assertTrue(result.valid());
  }
}
