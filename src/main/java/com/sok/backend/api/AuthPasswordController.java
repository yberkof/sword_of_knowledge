package com.sok.backend.api;

import com.sok.backend.api.dto.AuthPasswordLoginRequest;
import com.sok.backend.api.dto.AuthRegisterRequest;
import com.sok.backend.api.dto.AuthTokensResponse;
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
public class AuthPasswordController {
  private final AuthService authService;

  public AuthPasswordController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/login")
  public ResponseEntity<?> login(
      @RequestBody(required = false) AuthPasswordLoginRequest req, HttpServletRequest httpReq) {
    if (req == null || isBlank(req.getEmail()) || isBlank(req.getPassword())) {
      return error(HttpStatus.BAD_REQUEST, "email and password are required");
    }
    try {
      AuthTokenService.TokenPair pair = authService.loginWithPassword(
          req.getEmail(), req.getPassword(), req.getDeviceInfo(), HttpUtils.extractIp(httpReq));
      return ResponseEntity.ok(new AuthTokensResponse(pair.accessToken(), pair.refreshToken()));
    } catch (IllegalArgumentException ex) {
      return error(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }
  }

  @PostMapping("/register")
  public ResponseEntity<?> register(
      @RequestBody(required = false) AuthRegisterRequest req, HttpServletRequest httpReq) {
    if (req == null || isBlank(req.getEmail()) || isBlank(req.getPassword())) {
      return error(HttpStatus.BAD_REQUEST, "email and password are required");
    }
    try {
      AuthTokenService.TokenPair pair = authService.registerWithPassword(
          req.getEmail(), req.getPassword(), req.getDisplayName(), req.getDeviceInfo(), HttpUtils.extractIp(httpReq));
      return ResponseEntity.status(HttpStatus.CREATED)
          .body(new AuthTokensResponse(pair.accessToken(), pair.refreshToken()));
    } catch (IllegalStateException ex) {
      String err = "email_taken".equals(ex.getMessage()) ? "email_taken" : "server_error";
      return error("email_taken".equals(err) ? HttpStatus.CONFLICT : HttpStatus.INTERNAL_SERVER_ERROR, err);
    } catch (IllegalArgumentException ex) {
      return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }

  private boolean isBlank(String s) {
    return s == null || s.trim().isEmpty();
  }

  private ResponseEntity<?> error(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(Collections.singletonMap("error", message));
  }
}
