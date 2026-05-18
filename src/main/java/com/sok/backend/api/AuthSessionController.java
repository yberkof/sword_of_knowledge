package com.sok.backend.api;

import com.sok.backend.api.dto.AuthLogoutRequest;
import com.sok.backend.api.dto.AuthRefreshRequest;
import com.sok.backend.api.dto.AuthTokensResponse;
import com.sok.backend.service.AuthService;
import com.sok.backend.service.AuthTokenService;
import java.util.Collections;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthSessionController {
  private final AuthService authService;

  public AuthSessionController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/refresh")
  public ResponseEntity<?> refresh(
      @RequestBody(required = false) AuthRefreshRequest req, HttpServletRequest hReq) {
    if (req == null || isBlank(req.getRefreshToken())) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Collections.singletonMap("error", "refreshToken is required"));
    }
    AuthTokenService.TokenPair pair = authService.refresh(
        req.getRefreshToken(), req.getDeviceInfo(), HttpUtils.extractIp(hReq));
    return ResponseEntity.ok(new AuthTokensResponse(pair.accessToken(), pair.refreshToken()));
  }

  @PostMapping("/logout")
  public ResponseEntity<Map<String, Object>> logout(@RequestBody(required = false) AuthLogoutRequest req) {
    if (req == null || isBlank(req.getRefreshToken())) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Collections.singletonMap("ok", false));
    }
    authService.logout(req.getRefreshToken());
    return ResponseEntity.ok(Collections.singletonMap("ok", true));
  }

  private boolean isBlank(String s) {
    return s == null || s.trim().isEmpty();
  }
}
