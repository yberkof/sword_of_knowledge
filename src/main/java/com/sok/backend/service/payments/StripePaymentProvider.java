package com.sok.backend.service.payments;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StripePaymentProvider implements PaymentProvider {
  private final String webhookSecret;

  public StripePaymentProvider(@Value("${app.payments.stripe-webhook-secret:}") String webhookSecret) {
    this.webhookSecret = webhookSecret;
  }

  @Override
  public String providerCode() {
    return "stripe";
  }

  @Override
  public PaymentVerificationResult verify(PaymentVerificationRequest request) {
    if (request.externalTransactionId() == null || request.externalTransactionId().trim().isEmpty()) {
      return new PaymentVerificationResult(false, "invalid", "unknown", providerCode());
    }
    if (request.rawPayload() != null && !request.rawPayload().trim().isEmpty()) {
      if (!verifySignature(request.rawPayload(), request.signature())) {
        return new PaymentVerificationResult(false, "invalid_signature", "unknown", providerCode());
      }
    }
    String type = request.productCode() != null && request.productCode().startsWith("skin_")
        ? "durable"
        : "consumable";
    return new PaymentVerificationResult(true, "succeeded", type, providerCode());
  }

  private boolean verifySignature(String payload, String signature) {
    if (webhookSecret == null || webhookSecret.trim().isEmpty()) return false;
    if (signature == null || signature.trim().isEmpty()) return false;
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(digest.length * 2);
      for (byte b : digest) sb.append(String.format("%02x", b));
      return sb.toString().equalsIgnoreCase(signature.trim());
    } catch (Exception ex) {
      return false;
    }
  }
}
