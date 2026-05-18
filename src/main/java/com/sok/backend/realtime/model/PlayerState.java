package com.sok.backend.realtime.model;

public class PlayerState {
  public String uid, name, avatarUrl, socketId, color, teamId;
  public int castleHp, score, trophies;
  public Integer castleRegionId;
  public boolean isEliminated, online;
  public long lastSeenAt;
  public Long eliminatedAt;
}
