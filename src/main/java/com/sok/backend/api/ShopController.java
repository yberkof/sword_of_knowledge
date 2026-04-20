package com.sok.backend.api;

import com.sok.backend.api.dto.ShopPurchaseRequest;
import com.sok.backend.security.SecurityUtils;
import com.sok.backend.service.ShopService;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shop")
public class ShopController {
  private final ShopService shopService;

  public ShopController(ShopService shopService) {
    this.shopService = shopService;
  }

  @PostMapping("/purchase")
  public ResponseEntity<Map<String, Object>> purchase(
      @RequestBody(required = false) ShopPurchaseRequest body,
      @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
      HttpServletRequest request) {
    if (body == null || body.getItemId() == null || body.getItemId().trim().isEmpty()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Collections.<String, Object>singletonMap("ok", false));
    }
    if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Collections.<String, Object>singletonMap("ok", false));
    }
    String uid = SecurityUtils.currentUid();
    boolean ok = shopService.purchase(uid, body.getItemId(), idempotencyKey.trim());
    if (!ok) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Collections.<String, Object>singletonMap("ok", false));
    }
    Map<String, Object> out = new HashMap<>();
    out.put("ok", true);
    out.put("itemId", body.getItemId());
    return ResponseEntity.ok(out);
  }
}
