package com.sok.backend.auth.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.sok.backend.auth.identity.AuthGrantTypes;
import com.sok.backend.auth.identity.AuthIdentityProvider;
import com.sok.backend.auth.identity.IdentityEmail;
import com.sok.backend.persistence.PasswordCredential;
import com.sok.backend.persistence.UserRepository;
import com.sok.backend.service.AuthTokenService;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Order(10)
@Component
public class PasswordAuthIdentityProvider implements AuthIdentityProvider {
  private final UserRepository userRepository;
  private final AuthTokenService authTokenService;
  private final PasswordEncoder passwordEncoder;

  public PasswordAuthIdentityProvider(
      UserRepository userRepository,
      AuthTokenService authTokenService,
      PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.authTokenService = authTokenService;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public String grantType() {
    return AuthGrantTypes.PASSWORD;
  }

  @Override
  public boolean isAvailable() {
    return true;
  }

  @Override
  public AuthTokenService.TokenPair authenticate(JsonNode body, String deviceInfo, String ip) {
    String email = IdentityEmail.normalize(body.path("email").asText(""));
    String password = body.path("password").asText("");
    if (!IdentityEmail.isValid(email) || password.isEmpty()) {
      throw new IllegalArgumentException("invalid_credentials");
    }
    Optional<PasswordCredential> row = userRepository.findPasswordCredentialByEmail(email);
    if (!row.isPresent()) {
      throw new IllegalArgumentException("invalid_credentials");
    }
    PasswordCredential cred = row.get();
    if (!passwordEncoder.matches(password, cred.passwordHash())) {
      throw new IllegalArgumentException("invalid_credentials");
    }
    return authTokenService.issueForUser(cred.userId(), deviceInfo, ip);
  }
}
