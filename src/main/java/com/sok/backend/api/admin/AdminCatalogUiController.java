package com.sok.backend.api.admin;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/catalog-ui")
public class AdminCatalogUiController {
  @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
  public String page() {
    return "<!doctype html><html><head><meta charset='utf-8'><title>Catalog Admin</title></head>"
        + "<body><h1>Catalog Admin</h1>"
        + "<p>1) Login via /api/admin/auth/login and copy token.</p>"
        + "<p>2) Use X-Admin-Session header with /api/admin/catalog endpoints.</p>"
        + "<pre>curl -H 'X-Admin-Session: TOKEN' http://localhost:8080/api/admin/catalog/items</pre>"
        + "</body></html>";
  }
}
