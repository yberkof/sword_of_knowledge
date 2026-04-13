package com.sok.backend.auth.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.sok.backend.service.AuthTokenService;

/**
 * Pluggable sign-in: each implementation handles one {@link #grantType()} and returns the same JWT
 * pair shape as every other provider ({@link AuthTokenService.TokenPair}).
 */
public interface AuthIdentityProvider {

  String grantType();

  /** When false, token endpoint returns {@code identity_not_configured} (Firebase) or rejects. */
  boolean isAvailable();

  /**
   * Reads provider-specific fields from {@code body}.
   *
   * @throws IllegalArgumentException invalid credentials or payload
   * @throws IllegalStateException misconfiguration / upstream identity outage
   */
  AuthTokenService.TokenPair authenticate(JsonNode body, String deviceInfo, String ip);
}
