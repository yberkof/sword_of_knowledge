package com.sok.backend.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

public class AuthSessionRecord {
  private final UUID id;
  private final String userId;
  private final String refreshHash;
  private final OffsetDateTime expiresAt;
  private final OffsetDateTime revokedAt;
  private final OffsetDateTime createdAt;
  private final OffsetDateTime lastUsedAt;
  private final String deviceInfo;
  private final String ip;

  public AuthSessionRecord(
      UUID id,
      String userId,
      String refreshHash,
      OffsetDateTime expiresAt,
      OffsetDateTime revokedAt,
      OffsetDateTime createdAt,
      OffsetDateTime lastUsedAt,
      String deviceInfo,
      String ip) {
    this.id = id;
    this.userId = userId;
    this.refreshHash = refreshHash;
    this.expiresAt = expiresAt;
    this.revokedAt = revokedAt;
    this.createdAt = createdAt;
    this.lastUsedAt = lastUsedAt;
    this.deviceInfo = deviceInfo;
    this.ip = ip;
  }

  public UUID id() { return id; }
  public String userId() { return userId; }
  public String refreshHash() { return refreshHash; }
  public OffsetDateTime expiresAt() { return expiresAt; }
  public OffsetDateTime revokedAt() { return revokedAt; }
  public OffsetDateTime createdAt() { return createdAt; }
  public OffsetDateTime lastUsedAt() { return lastUsedAt; }
  public String deviceInfo() { return deviceInfo; }
  public String ip() { return ip; }
}
