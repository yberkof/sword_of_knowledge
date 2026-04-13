package com.sok.backend.security;

public class AuthenticatedUser {
  private final String uid;

  public AuthenticatedUser(String uid) {
    this.uid = uid;
  }

  public String uid() {
    return uid;
  }
}
