package com.sok.backend.persistence;

/** Login row for email/password users ({@code users.password_hash} is non-null). */
public final class PasswordCredential {
  private final String userId;
  private final String passwordHash;

  public PasswordCredential(String userId, String passwordHash) {
    this.userId = userId;
    this.passwordHash = passwordHash;
  }

  public String userId() {
    return userId;
  }

  public String passwordHash() {
    return passwordHash;
  }
}
