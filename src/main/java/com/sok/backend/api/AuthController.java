package com.sok.backend.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.sok.backend.api.dto.AuthExchangeRequest;
import com.sok.backend.api.dto.AuthLinkFirebaseRequest;
import com.sok.backend.api.dto.AuthLogoutRequest;
import com.sok.backend.api.dto.AuthPasswordLoginRequest;
import com.sok.backend.api.dto.AuthRefreshRequest;
import com.sok.backend.api.dto.AuthRegisterRequest;
import com.sok.backend.api.dto.AuthTokensResponse;
import com.sok.backend.auth.identity.AuthGrantTypes;
import com.sok.backend.security.SecurityUtils;
import com.sok.backend.service.AuthLinkService;
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
public class AuthController {
  private final AuthService authService;
  private final AuthLinkService authLinkService;

  public AuthController(
      AuthService authService, AuthLinkService authLinkService) {
    this.authService = authService;
    this.authLinkService = authLinkService;
  }

  /**
   * Extensible token issuance: {@code grantType} selects an {@link
   * com.sok.backend.auth.identity.AuthIdentityProvider}. Same access/refresh JWT shape for every
   * provider.
   */
  @PostMapping("/token")
  public ResponseEntity<?> token(
      @RequestBody(required = false) JsonNode body, HttpServletRequest httpRequest) {
    if (body == null || !body.hasNonNull("grantType")) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Collections.singletonMap("error", "grantType is required"));
    }
    String grantType = body.get("grantType").asText("").trim();
    if (grantType.isEmpty()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Collections.singletonMap("error", "grantType is required"));
    }
    if (!authService.providerForGrant(grantType).isPresent()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Collections.singletonMap("error", "unsupported_grant_type"));
    }
    if (!authService.grantAvailable(grantType)) {
      if (AuthGrantTypes.GOOGLE_FIREBASE_ID_TOKEN.equals(grantType)) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Collections.singletonMap("error", "identity_not_configured"));
      }
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(Collections.singletonMap("error", "provider_unavailable"));
    }
    String deviceInfo = body.path("deviceInfo").asText("");
    try {
      AuthTokenService.TokenPair pair =
          authService.authenticateGrant(
              grantType, body, deviceInfo.trim().isEmpty() ? null : deviceInfo, extractIp(httpRequest));
      return ResponseEntity.ok(new AuthTokensResponse(pair.accessToken(), pair.refreshToken()));
    } catch (IllegalArgumentException ex) {
      String m = ex.getMessage();
      if ("idToken is required".equals(m)) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Collections.singletonMap("error", "idToken is required"));
      }
      if ("unsupported_grant_type".equals(m)) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Collections.singletonMap("error", "unsupported_grant_type"));
      }
      if (AuthGrantTypes.GOOGLE_FIREBASE_ID_TOKEN.equals(grantType)) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Collections.singletonMap("error", "Invalid token"));
      }
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(Collections.singletonMap("error", "Invalid credentials"));
    } catch (IllegalStateException ex) {
      if ("cert_fetch_failed".equals(ex.getMessage())) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Collections.singletonMap("error", "identity_keys_unavailable"));
      }
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(Collections.singletonMap("error", "identity_not_configured"));
    }
  }

  /** Primary sign-in: email + password → same JWT pair as all other grants. */
  @PostMapping("/login")
  public ResponseEntity<?> loginWithPassword(
      @RequestBody(required = false) AuthPasswordLoginRequest request, HttpServletRequest httpRequest) {
    if (request == null
        || request.getEmail() == null
        || request.getPassword() == null
        || request.getEmail().trim().isEmpty()
        || request.getPassword().isEmpty()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Collections.singletonMap("error", "email and password are required"));
    }
    try {
      AuthTokenService.TokenPair pair =
          authService.loginWithPassword(
              request.getEmail(),
              request.getPassword(),
              request.getDeviceInfo(),
              extractIp(httpRequest));
      return ResponseEntity.ok(new AuthTokensResponse(pair.accessToken(), pair.refreshToken()));
    } catch (IllegalArgumentException ex) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(Collections.singletonMap("error", "Invalid credentials"));
    }
  }

  /** Primary registration (password account). */
  @PostMapping("/register")
  public ResponseEntity<?> register(
      @RequestBody(required = false) AuthRegisterRequest request, HttpServletRequest httpRequest) {
    if (request == null
        || request.getEmail() == null
        || request.getPassword() == null
        || request.getEmail().trim().isEmpty()
        || request.getPassword().isEmpty()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Collections.singletonMap("error", "email and password are required"));
    }
    try {
      AuthTokenService.TokenPair pair =
          authService.registerWithPassword(
              request.getEmail(),
              request.getPassword(),
              request.getDisplayName(),
              request.getDeviceInfo(),
              extractIp(httpRequest));
      return ResponseEntity.status(HttpStatus.CREATED)
          .body(new AuthTokensResponse(pair.accessToken(), pair.refreshToken()));
    } catch (IllegalStateException ex) {
      if ("email_taken".equals(ex.getMessage())) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Collections.singletonMap("error", "email_taken"));
      }
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Collections.singletonMap("error", "server_error"));
    } catch (IllegalArgumentException ex) {
      String code = ex.getMessage();
      if ("invalid_email".equals(code) || "weak_password".equals(code)) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Collections.singletonMap("error", code));
      }
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Collections.singletonMap("error", "invalid_request"));
    }
  }

  /** Secondary: Firebase Auth ID token (Google on device). Backward-compatible path. */
  @PostMapping("/exchange")
  public ResponseEntity<?> exchange(
      @RequestBody(required = false) AuthExchangeRequest request, HttpServletRequest httpRequest) {
    if (request == null || request.getIdToken() == null || request.getIdToken().trim().isEmpty()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Collections.singletonMap("error", "idToken is required"));
    }
    if (!authService.grantAvailable(AuthGrantTypes.GOOGLE_FIREBASE_ID_TOKEN)) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(Collections.singletonMap("error", "identity_not_configured"));
    }
    try {
      AuthTokenService.TokenPair pair =
          authService.exchangeIdToken(
              request.getIdToken(),
              request.getDeviceInfo(),
              extractIp(httpRequest));
      return ResponseEntity.ok(new AuthTokensResponse(pair.accessToken(), pair.refreshToken()));
    } catch (IllegalArgumentException ex) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(Collections.singletonMap("error", "Invalid token"));
    } catch (IllegalStateException ex) {
      if ("cert_fetch_failed".equals(ex.getMessage())) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Collections.singletonMap("error", "identity_keys_unavailable"));
      }
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(Collections.singletonMap("error", "identity_not_configured"));
    }
  }

  /**
   * Authenticated: attach the Firebase account in {@code idToken} to the current JWT user. After
   * success, Google sign-in yields tokens for the same canonical {@code users.id}.
   */
  @PostMapping("/link/firebase")
  public ResponseEntity<?> linkFirebase(
      @RequestBody(required = false) AuthLinkFirebaseRequest request, HttpServletRequest httpRequest) {
    if (request == null || request.getIdToken() == null || request.getIdToken().trim().isEmpty()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Collections.singletonMap("error", "idToken is required"));
    }
    if (!authService.grantAvailable(AuthGrantTypes.GOOGLE_FIREBASE_ID_TOKEN)) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(Collections.singletonMap("error", "identity_not_configured"));
    }
    try {
      authLinkService.linkFirebaseForCurrentUser(SecurityUtils.currentUid(), request.getIdToken());
      return ResponseEntity.ok(Collections.singletonMap("ok", true));
    } catch (IllegalArgumentException ex) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(Collections.singletonMap("error", "Invalid token"));
    } catch (IllegalStateException ex) {
      if ("provider_already_linked".equals(ex.getMessage())) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Collections.singletonMap("error", "provider_already_linked"));
      }
      if ("identity_not_configured".equals(ex.getMessage())) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Collections.singletonMap("error", "identity_not_configured"));
      }
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(Collections.singletonMap("error", "provider_already_linked"));
    }
  }

  @PostMapping("/refresh")
  public ResponseEntity<?> refresh(
      @RequestBody(required = false) AuthRefreshRequest request, HttpServletRequest httpRequest) {
    if (request == null || request.getRefreshToken() == null || request.getRefreshToken().trim().isEmpty()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Collections.singletonMap("error", "refreshToken is required"));
    }
    AuthTokenService.TokenPair pair =
        authService.refresh(request.getRefreshToken(), request.getDeviceInfo(), extractIp(httpRequest));
    return ResponseEntity.ok(new AuthTokensResponse(pair.accessToken(), pair.refreshToken()));
  }

  @PostMapping("/logout")
  public ResponseEntity<Map<String, Object>> logout(@RequestBody(required = false) AuthLogoutRequest request) {
    if (request == null || request.getRefreshToken() == null || request.getRefreshToken().trim().isEmpty()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Collections.<String, Object>singletonMap("ok", false));
    }
    authService.logout(request.getRefreshToken());
    return ResponseEntity.ok(Collections.<String, Object>singletonMap("ok", true));
  }

  private String extractIp(HttpServletRequest request) {
    String header = request.getHeader("X-Forwarded-For");
    if (header != null && !header.trim().isEmpty()) {
      int comma = header.indexOf(',');
      if (comma > 0) return header.substring(0, comma).trim();
      return header.trim();
    }
    return request.getRemoteAddr();
  }
}
