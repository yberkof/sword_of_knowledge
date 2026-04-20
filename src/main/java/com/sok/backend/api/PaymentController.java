package com.sok.backend.api;

import com.sok.backend.api.dto.PaymentVerifyRequest;
import com.sok.backend.persistence.PaymentTransactionRecord;
import com.sok.backend.security.SecurityUtils;
import com.sok.backend.service.PaymentService;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
  private final PaymentService paymentService;

  public PaymentController(PaymentService paymentService) {
    this.paymentService = paymentService;
  }

  @PostMapping("/verify")
  public ResponseEntity<?> verify(@RequestBody(required = false) PaymentVerifyRequest request) {
    String uid = SecurityUtils.currentUid();
    if (request == null
        || request.getProvider() == null
        || request.getExternalTransactionId() == null
        || request.getProductCode() == null
        || request.getAmountMinor() == null
        || request.getCurrency() == null) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Collections.singletonMap("error", "invalid_request"));
    }
    try {
      PaymentTransactionRecord tx =
          paymentService.verifyAndGrant(
              request.getProvider(),
              uid,
              request.getExternalTransactionId(),
              request.getEventId(),
              request.getProductCode(),
              request.getAmountMinor(),
              request.getCurrency(),
              request.getPayload(),
              request.getSignature());
      Map<String, Object> out = new HashMap<String, Object>();
      out.put("ok", true);
      out.put("state", tx.state());
      out.put("provider", tx.provider());
      out.put("externalTransactionId", tx.externalTxnId());
      return ResponseEntity.ok(out);
    } catch (IllegalStateException ex) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Collections.singletonMap("error", ex.getMessage()));
    }
  }

  @PostMapping("/webhook/stripe")
  public ResponseEntity<?> webhookStripe(
      @RequestBody(required = false) String payload,
      @RequestHeader(name = "X-Signature", required = false) String signature,
      @RequestHeader(name = "X-Event-Id", required = false) String eventId,
      @RequestHeader(name = "X-User-Id", required = false) String userId,
      @RequestHeader(name = "X-External-Transaction-Id", required = false) String externalTransactionId,
      @RequestHeader(name = "X-Product-Code", required = false) String productCode,
      @RequestHeader(name = "X-Amount-Minor", required = false) Long amountMinor,
      @RequestHeader(name = "X-Currency", required = false) String currency) {
    if (userId == null
        || externalTransactionId == null
        || productCode == null
        || amountMinor == null
        || currency == null) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Collections.singletonMap("error", "invalid_headers"));
    }
    try {
      paymentService.verifyAndGrant(
          "stripe",
          userId,
          externalTransactionId,
          eventId,
          productCode,
          amountMinor.longValue(),
          currency,
          payload,
          signature);
      return ResponseEntity.ok(Collections.singletonMap("ok", true));
    } catch (IllegalStateException ex) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Collections.singletonMap("error", ex.getMessage()));
    }
  }
}
