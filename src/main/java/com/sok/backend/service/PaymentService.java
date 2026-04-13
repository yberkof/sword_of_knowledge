package com.sok.backend.service;

import com.sok.backend.persistence.EconomyRepository;
import com.sok.backend.persistence.PaymentTransactionRecord;
import com.sok.backend.persistence.PaymentTransactionRepository;
import com.sok.backend.persistence.UserEntitlementRepository;
import com.sok.backend.service.payments.PaymentProvider;
import com.sok.backend.service.payments.PaymentVerificationRequest;
import com.sok.backend.service.payments.PaymentVerificationResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
  private final Map<String, PaymentProvider> providers = new HashMap<String, PaymentProvider>();
  private final PaymentTransactionRepository paymentTransactionRepository;
  private final EconomyRepository economyRepository;
  private final UserEntitlementRepository userEntitlementRepository;
  private final Counter paymentOk;
  private final Counter paymentFail;

  public PaymentService(
      List<PaymentProvider> providerList,
      PaymentTransactionRepository paymentTransactionRepository,
      EconomyRepository economyRepository,
      UserEntitlementRepository userEntitlementRepository,
      MeterRegistry meterRegistry) {
    for (PaymentProvider provider : providerList) {
      this.providers.put(provider.providerCode(), provider);
    }
    this.paymentTransactionRepository = paymentTransactionRepository;
    this.economyRepository = economyRepository;
    this.userEntitlementRepository = userEntitlementRepository;
    this.paymentOk = Counter.builder("sok.payments.ok").register(meterRegistry);
    this.paymentFail = Counter.builder("sok.payments.fail").register(meterRegistry);
  }

  @Transactional
  public PaymentTransactionRecord verifyAndGrant(
      String providerCode,
      String userId,
      String externalTxnId,
      String eventId,
      String productCode,
      long amountMinor,
      String currency,
      String rawPayload,
      String signature) {
    PaymentProvider provider = providers.get(providerCode);
    if (provider == null) throw new IllegalStateException("unsupported_provider");
    Optional<PaymentTransactionRecord> existing =
        paymentTransactionRepository.findByProviderExternal(providerCode, externalTxnId);
    if (existing.isPresent() && "succeeded".equals(existing.get().state())) {
      return existing.get();
    }

    PaymentVerificationResult verification =
        provider.verify(
            new PaymentVerificationRequest(
                externalTxnId, productCode, userId, amountMinor, currency, rawPayload, signature, eventId));
    if (!verification.valid()) {
      paymentFail.increment();
      throw new IllegalStateException("payment_invalid");
    }
    PaymentTransactionRecord tx =
        paymentTransactionRepository.upsert(
            userId,
            providerCode,
            externalTxnId,
            eventId,
            productCode,
            verification.productType(),
            currency,
            amountMinor,
            verification.state(),
            "{\"provider\":\"" + providerCode + "\"}");
    if ("consumable".equals(verification.productType())) {
      int gems = (int) Math.max(1, amountMinor);
      economyRepository.applyTransaction(
          userId,
          "pay:" + providerCode + ":" + externalTxnId,
          "payment_grant",
          "payment_consumable",
          0,
          gems,
          0,
          0,
          externalTxnId,
          "{\"productCode\":\"" + productCode + "\"}");
    } else if ("durable".equals(verification.productType())) {
      userEntitlementRepository.grant(userId, productCode, providerCode + ":" + externalTxnId);
    }
    paymentOk.increment();
    return tx;
  }
}
