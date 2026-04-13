package com.sok.backend.persistence;

import java.util.UUID;

public class AdminAccountRecord {
  private final UUID id;
  private final String email;
  private final String passwordHash;
  private final boolean mustChangePassword;
  private final boolean active;

  public AdminAccountRecord(UUID id, String email, String passwordHash, boolean mustChangePassword, boolean active) {
    this.id = id;
    this.email = email;
    this.passwordHash = passwordHash;
    this.mustChangePassword = mustChangePassword;
    this.active = active;
  }

  public UUID id() { return id; }
  public String email() { return email; }
  public String passwordHash() { return passwordHash; }
  public boolean mustChangePassword() { return mustChangePassword; }
  public boolean active() { return active; }
}
