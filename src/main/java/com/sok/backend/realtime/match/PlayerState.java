package com.sok.backend.realtime.match;

/**
 * Per-player slice of a room. Extracted from {@code SocketGateway} so the engine layer can
 * reference it without reaching into a private nested class.
 */
public class PlayerState {
  public String uid;
  public String name;
  /** Optional profile image URL for clients (HUD). */
  public String avatarUrl;
  public String socketId;
  public int castleHp;
  public Integer castleRegionId;
  public int score;
  public String color;
  /** {@code null} in FFA; {@code "A"} or {@code "B"} in {@code teams_2v2}. */
  public String teamId;
  public boolean isEliminated;
  public boolean online;
  public long lastSeenAt;
  public int trophies;
  public Long eliminatedAt;
}
