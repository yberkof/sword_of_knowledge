package com.sok.backend.api.admin;

import com.sok.backend.api.dto.AdminCatalogItemUpsertRequest;
import com.sok.backend.api.dto.AdminCatalogPriceUpsertRequest;
import com.sok.backend.persistence.CatalogItemRecord;
import com.sok.backend.persistence.CatalogPriceRecord;
import com.sok.backend.service.AdminAuthService;
import com.sok.backend.service.CatalogAdminService;
import java.util.Collections;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/catalog")
public class AdminCatalogController {
  private final CatalogAdminService catalogAdminService;
  private final AdminAuthService adminAuthService;

  public AdminCatalogController(CatalogAdminService catalogAdminService, AdminAuthService adminAuthService) {
    this.catalogAdminService = catalogAdminService;
    this.adminAuthService = adminAuthService;
  }

  @GetMapping("/items")
  public ResponseEntity<List<CatalogItemRecord>> items(@RequestHeader(name = "X-Admin-Session", required = false) String token) {
    adminAuthService.requireAccount(token, true);
    return ResponseEntity.ok(catalogAdminService.listItems());
  }

  @PostMapping("/items")
  public ResponseEntity<?> upsertItem(
      @RequestHeader(name = "X-Admin-Session", required = false) String token,
      @RequestBody(required = false) AdminCatalogItemUpsertRequest body) {
    if (body == null || body.getCode() == null || body.getName() == null || body.getItemType() == null) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Collections.<String, String>singletonMap("error", "invalid_request"));
    }
    adminAuthService.requireAccount(token, true);
    CatalogItemRecord out =
        catalogAdminService.upsertItem(
            body.getCode(),
            body.getName(),
            body.getItemType(),
            body.getMetadataJson(),
            body.getActive() == null ? true : body.getActive().booleanValue());
    return ResponseEntity.ok(out);
  }

  @PostMapping("/prices")
  public ResponseEntity<?> upsertPrice(
      @RequestHeader(name = "X-Admin-Session", required = false) String token,
      @RequestBody(required = false) AdminCatalogPriceUpsertRequest body) {
    if (body == null
        || body.getItemCode() == null
        || body.getProvider() == null
        || body.getRegion() == null
        || body.getCurrency() == null
        || body.getAmountMinor() == null) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Collections.<String, String>singletonMap("error", "invalid_request"));
    }
    adminAuthService.requireAccount(token, true);
    CatalogPriceRecord out =
        catalogAdminService.upsertPrice(
            body.getItemCode(),
            body.getProvider(),
            body.getRegion(),
            body.getCurrency(),
            body.getAmountMinor().longValue(),
            body.getActive() == null ? true : body.getActive().booleanValue());
    return ResponseEntity.ok(out);
  }
}
