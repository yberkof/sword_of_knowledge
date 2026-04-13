package com.sok.backend.service;

import com.sok.backend.auth.provider.FirebaseAuthIdentityProvider;
import com.sok.backend.persistence.UserIdentityLinkRepository;
import com.sok.backend.security.FirebaseGoogleIdentityTokenVerifier;
import com.sok.backend.security.FirebaseGoogleIdentityTokenVerifier.VerifiedToken;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Links a secondary identity (Firebase) to the currently authenticated canonical user. After
 * linking, Firebase sign-in resolves to the same {@code users.id} as email/password JWTs.
 */
@Service
public class AuthLinkService {
  private final FirebaseGoogleIdentityTokenVerifier identityTokenVerifier;
  private final UserIdentityLinkRepository userIdentityLinkRepository;

  public AuthLinkService(
      FirebaseGoogleIdentityTokenVerifier identityTokenVerifier,
      UserIdentityLinkRepository userIdentityLinkRepository) {
    this.identityTokenVerifier = identityTokenVerifier;
    this.userIdentityLinkRepository = userIdentityLinkRepository;
  }

  /**
   * Verifies the Firebase ID token and attaches {@code subject} to {@code currentUserId}.
   *
   * @throws IllegalArgumentException invalid token
   * @throws IllegalStateException subject already linked elsewhere, or unique constraint race
   */
  public void linkFirebaseForCurrentUser(String currentUserId, String idToken) {
    if (!identityTokenVerifier.isConfigured()) {
      throw new IllegalStateException("identity_not_configured");
    }
    if (idToken == null || idToken.trim().isEmpty()) {
      throw new IllegalArgumentException("idToken is required");
    }
    VerifiedToken t = identityTokenVerifier.verify(idToken);
    String subject = t.uid();
    Optional<String> existing =
        userIdentityLinkRepository.findUserIdByProviderAndSubject(
            FirebaseAuthIdentityProvider.LINK_PROVIDER, subject);
    if (existing.isPresent() && !existing.get().equals(currentUserId)) {
      throw new IllegalStateException("provider_already_linked");
    }
    if (existing.isPresent()) {
      return;
    }
    try {
      userIdentityLinkRepository.setCanonicalUser(
          currentUserId, FirebaseAuthIdentityProvider.LINK_PROVIDER, subject);
    } catch (IllegalStateException ex) {
      if ("provider_subject_taken".equals(ex.getMessage())) {
        throw new IllegalStateException("provider_already_linked");
      }
      throw ex;
    }
  }
}
