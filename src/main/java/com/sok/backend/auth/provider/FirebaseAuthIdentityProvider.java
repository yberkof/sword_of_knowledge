package com.sok.backend.auth.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.sok.backend.auth.identity.AuthGrantTypes;
import com.sok.backend.auth.identity.AuthIdentityProvider;
import com.sok.backend.persistence.UserIdentityLinkRepository;
import com.sok.backend.persistence.UserRepository;
import com.sok.backend.security.FirebaseGoogleIdentityTokenVerifier;
import com.sok.backend.security.FirebaseGoogleIdentityTokenVerifier.VerifiedToken;
import com.sok.backend.service.AuthTokenService;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(20)
@Component
public class FirebaseAuthIdentityProvider implements AuthIdentityProvider {
  public static final String LINK_PROVIDER = "google_firebase";

  private final FirebaseGoogleIdentityTokenVerifier identityTokenVerifier;
  private final UserRepository userRepository;
  private final UserIdentityLinkRepository userIdentityLinkRepository;
  private final AuthTokenService authTokenService;

  public FirebaseAuthIdentityProvider(
      FirebaseGoogleIdentityTokenVerifier identityTokenVerifier,
      UserRepository userRepository,
      UserIdentityLinkRepository userIdentityLinkRepository,
      AuthTokenService authTokenService) {
    this.identityTokenVerifier = identityTokenVerifier;
    this.userRepository = userRepository;
    this.userIdentityLinkRepository = userIdentityLinkRepository;
    this.authTokenService = authTokenService;
  }

  @Override
  public String grantType() {
    return AuthGrantTypes.GOOGLE_FIREBASE_ID_TOKEN;
  }

  @Override
  public boolean isAvailable() {
    return identityTokenVerifier.isConfigured();
  }

  @Override
  public AuthTokenService.TokenPair authenticate(JsonNode body, String deviceInfo, String ip) {
    String idToken = body.path("idToken").asText("");
    if (idToken.trim().isEmpty()) {
      throw new IllegalArgumentException("idToken is required");
    }
    VerifiedToken t = identityTokenVerifier.verify(idToken);
    String name = t.name();
    String email = t.email();
    String username = email.trim().isEmpty() ? name : email;
    Optional<String> canonical =
        userIdentityLinkRepository.findUserIdByProviderAndSubject(LINK_PROVIDER, t.uid());
    String userId = canonical.orElse(t.uid());
    userRepository.upsertIdentity(userId, name, username, "");
    if (!canonical.isPresent()) {
      userIdentityLinkRepository.setCanonicalUser(t.uid(), LINK_PROVIDER, t.uid());
    }
    return authTokenService.issueForUser(userId, deviceInfo, ip);
  }
}
