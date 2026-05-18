package com.sok.backend.api;

import com.sok.backend.api.dto.AuthExchangeRequest;
import com.sok.backend.api.dto.AuthLinkFirebaseRequest;
import com.sok.backend.api.dto.AuthTokensResponse;
import com.sok.backend.auth.identity.AuthGrantTypes;
import com.sok.backend.security.SecurityUtils;
import com.sok.backend.service.AuthLinkService;
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
public class AuthLegacyController {
  private final AuthService authService;
  private final AuthLinkService authLinkService;

  public AuthLegacyController(AuthService authService, AuthLinkService authLinkService) {
    this.authService = authService;
    this.authLinkService = authLinkService;
  }

  @PostMapping("/exchange")
  public ResponseEntity<?> exchange(@RequestBody(required = false) AuthExchangeRequest req, HttpServletRequest hReq) {
    if (req == null || isBlank(req.getIdToken())) return error(HttpStatus.BAD_REQUEST, "idToken is required");
    if (!authService.grantAvailable(AuthGrantTypes.GOOGLE_FIREBASE_ID_TOKEN)) {
      return error(HttpStatus.SERVICE_UNAVAILABLE, "identity_not_configured");
    }
    try {
      AuthTokenService.TokenPair pair = authService.exchangeIdToken(req.getIdToken(), req.getDeviceInfo(), HttpUtils.extractIp(hReq));
      return ResponseEntity.ok(new AuthTokensResponse(pair.accessToken(), pair.refreshToken()));
    } catch (IllegalArgumentException ex) {
      return error(HttpStatus.UNAUTHORIZED, "Invalid token");
    } catch (IllegalStateException ex) {
      String err = "cert_fetch_failed".equals(ex.getMessage()) ? "identity_keys_unavailable" : "identity_not_configured";
      return error(HttpStatus.SERVICE_UNAVAILABLE, err);
    }
  }

  @PostMapping("/link/firebase")
  public ResponseEntity<?> linkFirebase(@RequestBody(required = false) AuthLinkFirebaseRequest req) {
    if (req == null || isBlank(req.getIdToken())) return error(HttpStatus.BAD_REQUEST, "idToken is required");
    if (!authService.grantAvailable(AuthGrantTypes.GOOGLE_FIREBASE_ID_TOKEN)) {
      return error(HttpStatus.SERVICE_UNAVAILABLE, "identity_not_configured");
    }
    try {
      authLinkService.linkFirebaseForCurrentUser(SecurityUtils.currentUid(), req.getIdToken());
      return ResponseEntity.ok(Collections.singletonMap("ok", true));
    } catch (IllegalArgumentException ex) {
      return error(HttpStatus.UNAUTHORIZED, "Invalid token");
    } catch (IllegalStateException ex) {
      String msg = ex.getMessage();
      HttpStatus s = "provider_already_linked".equals(msg) ? HttpStatus.CONFLICT : HttpStatus.SERVICE_UNAVAILABLE;
      return error(s, "identity_not_configured".equals(msg) ? msg : "provider_already_linked");
    }
  }

  private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
  private ResponseEntity<?> error(HttpStatus s, String m) { return ResponseEntity.status(s).body(Collections.singletonMap("error", m)); }
}
