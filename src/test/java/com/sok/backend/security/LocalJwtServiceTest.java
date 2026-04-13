package com.sok.backend.security;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class LocalJwtServiceTest {
  @Test
  void accessTokenRoundTripWorks() {
    LocalJwtService svc = new LocalJwtService("test_secret_123456789012345678901234", "issuer", 300, 600);
    String token = svc.issueAccessToken("u1", java.util.UUID.randomUUID());
    LocalJwtService.LocalClaims claims = svc.verifyAccessToken(token);
    Assertions.assertEquals("u1", claims.userId());
  }

  @Test
  void refreshCannotBeUsedAsAccess() {
    LocalJwtService svc = new LocalJwtService("test_secret_123456789012345678901234", "issuer", 300, 600);
    String token = svc.issueRefreshToken("u1", java.util.UUID.randomUUID());
    Assertions.assertThrows(IllegalStateException.class, () -> svc.verifyAccessToken(token));
  }
}
