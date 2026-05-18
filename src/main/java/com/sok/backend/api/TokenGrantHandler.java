package com.sok.backend.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.sok.backend.api.dto.AuthTokensResponse;
import com.sok.backend.auth.identity.AuthGrantTypes;
import com.sok.backend.service.AuthService;
import com.sok.backend.service.AuthTokenService;
import java.util.Collections;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class TokenGrantHandler {
  private final AuthService authService;

  public TokenGrantHandler(AuthService authService) {
    this.authService = authService;
  }

  public ResponseEntity<?> handleToken(JsonNode body, String ip) {
    if (body == null || !body.hasNonNull("grantType")) return badRequest("grantType is required");
    String gt = body.get("grantType").asText("").trim();
    if (gt.isEmpty()) return badRequest("grantType is required");
    if (!authService.providerForGrant(gt).isPresent()) return badRequest("unsupported_grant_type");
    if (!authService.grantAvailable(gt)) {
      String err = AuthGrantTypes.GOOGLE_FIREBASE_ID_TOKEN.equals(gt) ? "identity_not_configured" : "provider_unavailable";
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Collections.singletonMap("error", err));
    }
    try {
      String di = body.path("deviceInfo").asText("");
      AuthTokenService.TokenPair p = authService.authenticateGrant(gt, body, di.trim().isEmpty() ? null : di, ip);
      return ResponseEntity.ok(new AuthTokensResponse(p.accessToken(), p.refreshToken()));
    } catch (IllegalArgumentException ex) {
      return handleArgEx(gt, ex.getMessage());
    } catch (IllegalStateException ex) {
      String err = "cert_fetch_failed".equals(ex.getMessage()) ? "identity_keys_unavailable" : "identity_not_configured";
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Collections.singletonMap("error", err));
    }
  }

  private ResponseEntity<?> handleArgEx(String gt, String m) {
    if ("idToken is required".equals(m) || "unsupported_grant_type".equals(m)) return badRequest(m);
    String err = AuthGrantTypes.GOOGLE_FIREBASE_ID_TOKEN.equals(gt) ? "Invalid token" : "Invalid credentials";
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Collections.singletonMap("error", err));
  }

  private ResponseEntity<?> badRequest(String m) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Collections.singletonMap("error", m));
  }
}
