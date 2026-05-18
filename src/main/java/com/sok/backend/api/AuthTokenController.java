package com.sok.backend.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.sok.backend.api.dto.AuthTokensResponse;
import com.sok.backend.auth.identity.AuthGrantTypes;
import com.sok.backend.service.AuthService;
import com.sok.backend.service.AuthTokenService;
import java.util.Collections;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthTokenController {
  private final AuthService authService;

  public AuthTokenController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/token")
  public ResponseEntity<?> token(
      @RequestBody(required = false) JsonNode body, HttpServletRequest httpRequest) {
    if (body == null || !body.hasNonNull("grantType")) {
      return error(HttpStatus.BAD_REQUEST, "grantType is required");
    }
    String grantType = body.get("grantType").asText("").trim();
    if (grantType.isEmpty()) {
      return error(HttpStatus.BAD_REQUEST, "grantType is required");
    }
    if (!authService.providerForGrant(grantType).isPresent()) {
      return error(HttpStatus.BAD_REQUEST, "unsupported_grant_type");
    }
    if (!authService.grantAvailable(grantType)) {
      String err = AuthGrantTypes.GOOGLE_FIREBASE_ID_TOKEN.equals(grantType)
          ? "identity_not_configured" : "provider_unavailable";
      return error(HttpStatus.SERVICE_UNAVAILABLE, err);
    }
    return executeTokenGrant(grantType, body, httpRequest);
  }

  private ResponseEntity<?> executeTokenGrant(String grant, JsonNode body, HttpServletRequest req) {
    try {
      String deviceInfo = body.path("deviceInfo").asText("");
      AuthTokenService.TokenPair pair = authService.authenticateGrant(
          grant, body, deviceInfo.trim().isEmpty() ? null : deviceInfo, HttpUtils.extractIp(req));
      return ResponseEntity.ok(new AuthTokensResponse(pair.accessToken(), pair.refreshToken()));
    } catch (IllegalArgumentException ex) {
      return handleTokenError(grant, ex.getMessage());
    } catch (IllegalStateException ex) {
      String err = "cert_fetch_failed".equals(ex.getMessage())
          ? "identity_keys_unavailable" : "identity_not_configured";
      return error(HttpStatus.SERVICE_UNAVAILABLE, err);
    }
  }

  private ResponseEntity<?> handleTokenError(String grant, String m) {
    if ("idToken is required".equals(m) || "unsupported_grant_type".equals(m)) {
      return error(HttpStatus.BAD_REQUEST, m);
    }
    String err = AuthGrantTypes.GOOGLE_FIREBASE_ID_TOKEN.equals(grant)
        ? "Invalid token" : "Invalid credentials";
    return error(HttpStatus.UNAUTHORIZED, err);
  }

  private ResponseEntity<?> error(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(Collections.singletonMap("error", message));
  }
}
