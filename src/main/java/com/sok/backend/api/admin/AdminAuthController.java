package com.sok.backend.api.admin;

import com.sok.backend.api.dto.AdminChangePasswordRequest;
import com.sok.backend.api.dto.AdminLoginRequest;
import com.sok.backend.service.AdminAuthService;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {
  private static final String ERROR_KEY = "error";
  private final AdminAuthService adminAuthService;

  public AdminAuthController(AdminAuthService adminAuthService) {
    this.adminAuthService = adminAuthService;
  }

  @PostMapping("/login")
  public ResponseEntity<Map<String, Object>> login(@RequestBody(required = false) AdminLoginRequest body) {
    if (body == null
        || body.getEmail() == null
        || body.getEmail().trim().isEmpty()
        || body.getPassword() == null
        || body.getPassword().isEmpty()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Collections.singletonMap(ERROR_KEY, "invalid_request"));
    }
    AdminAuthService.LoginResult res = adminAuthService.login(body.getEmail().trim(), body.getPassword());
    Map<String, Object> out = new HashMap<>();
    out.put("token", res.token());
    out.put("email", res.email());
    out.put("sessionId", res.sessionId());
    out.put("mustChangePassword", res.mustChangePassword());
    return ResponseEntity.ok(out);
  }

  @PostMapping("/change-password")
  public ResponseEntity<Map<String, Object>> changePassword(
      @RequestBody(required = false) AdminChangePasswordRequest body) {
    if (body == null
        || body.getToken() == null
        || body.getToken().trim().isEmpty()
        || body.getNewPassword() == null
        || body.getNewPassword().length() < 8) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Collections.singletonMap(ERROR_KEY, "invalid_request"));
    }
    adminAuthService.changePassword(body.getToken(), body.getNewPassword());
    return ResponseEntity.ok(Collections.singletonMap("ok", true));
  }
}
