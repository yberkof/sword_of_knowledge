package com.sok.backend.auth.identity;

/** {@code grantType} values for {@code POST /api/auth/token} and internal routing. */
public final class AuthGrantTypes {
  public static final String PASSWORD = "password";
  /** Firebase Auth ID token (Google sign-in on device). Secondary to email/password. */
  public static final String GOOGLE_FIREBASE_ID_TOKEN = "google_firebase_id_token";

  private AuthGrantTypes() {}
}
