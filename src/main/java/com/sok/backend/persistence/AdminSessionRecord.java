package com.sok.backend.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

public class AdminSessionRecord {
  private final UUID id;
  private final UUID adminId;
  private final String tokenHash;
  private final OffsetDateTime expiresAt;
  private final OffsetDateTime revokedAt;

  public AdminSessionRecord(UUID id, UUID adminId, String tokenHash, OffsetDateTime expiresAt, OffsetDateTime revokedAt) {
    this.id = id;
    this.adminId = adminId;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
    this.revokedAt = revokedAt;
  }

  public UUID id() { return id; }
  public UUID adminId() { return adminId; }
  public String tokenHash() { return tokenHash; }
  public OffsetDateTime expiresAt() { return expiresAt; }
  public OffsetDateTime revokedAt() { return revokedAt; }
}
