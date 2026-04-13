package com.sok.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sok.backend.auth.identity.AuthGrantTypes;
import com.sok.backend.auth.identity.AuthIdentityProvider;
import com.sok.backend.auth.identity.IdentityEmail;
import com.sok.backend.persistence.UserRepository;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
  private final Map<String, AuthIdentityProvider> byGrant;
  private final AuthTokenService authTokenService;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public AuthService(
      List<AuthIdentityProvider> identityProviders,
      AuthTokenService authTokenService,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder) {
    Map<String, AuthIdentityProvider> m = new LinkedHashMap<>();
    for (AuthIdentityProvider p : identityProviders) {
      if (m.put(p.grantType(), p) != null) {
        throw new IllegalStateException("Duplicate grantType: " + p.grantType());
      }
    }
    this.byGrant = Collections.unmodifiableMap(m);
    this.authTokenService = authTokenService;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  public Optional<AuthIdentityProvider> providerForGrant(String grantType) {
    return Optional.ofNullable(byGrant.get(grantType));
  }

  public boolean grantAvailable(String grantType) {
    AuthIdentityProvider p = byGrant.get(grantType);
    return p != null && p.isAvailable();
  }

  public AuthTokenService.TokenPair authenticateGrant(
      String grantType, JsonNode body, String deviceInfo, String ip) {
    AuthIdentityProvider p = byGrant.get(grantType);
    if (p == null) {
      throw new IllegalArgumentException("unsupported_grant_type");
    }
    return p.authenticate(body, deviceInfo, ip);
  }

  /** Secondary: Firebase ID token (same JWTs as password flow). */
  public AuthTokenService.TokenPair exchangeIdToken(String idToken, String deviceInfo, String ip) {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("idToken", idToken);
    return authenticateGrant(AuthGrantTypes.GOOGLE_FIREBASE_ID_TOKEN, body, deviceInfo, ip);
  }

  /** Primary: email + password. */
  public AuthTokenService.TokenPair loginWithPassword(
      String email, String password, String deviceInfo, String ip) {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("email", email == null ? "" : email);
    body.put("password", password == null ? "" : password);
    return authenticateGrant(AuthGrantTypes.PASSWORD, body, deviceInfo, ip);
  }

  public AuthTokenService.TokenPair registerWithPassword(
      String email, String password, String displayName, String deviceInfo, String ip) {
    String em = IdentityEmail.normalize(email);
    if (!IdentityEmail.isValid(em)) {
      throw new IllegalArgumentException("invalid_email");
    }
    if (password == null || password.length() < 8 || password.length() > 128) {
      throw new IllegalArgumentException("weak_password");
    }
    if (userRepository.existsEmail(em)) {
      throw new IllegalStateException("email_taken");
    }
    String id = UUID.randomUUID().toString();
    String hash = passwordEncoder.encode(password);
    String disp =
        displayName == null || displayName.trim().isEmpty() ? "Warrior" : displayName.trim();
    String username = em;
    try {
      userRepository.insertPasswordUser(id, disp, username, em, hash);
    } catch (DuplicateKeyException ex) {
      throw new IllegalStateException("email_taken");
    }
    return authTokenService.issueForUser(id, deviceInfo, ip);
  }

  public AuthTokenService.TokenPair refresh(String refreshToken, String deviceInfo, String ip) {
    return authTokenService.rotateRefreshToken(refreshToken, deviceInfo, ip);
  }

  public void logout(String refreshToken) {
    authTokenService.revokeByRefreshToken(refreshToken);
  }
}
