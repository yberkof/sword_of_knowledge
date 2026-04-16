package com.sok.backend.persistence;

public class UserRecord {
  private final String id;
  private final String displayName;
  private final String username;
  private final String avatarUrl;
  private final String countryCode;
  private final String title;
  private final int level;
  private final int xp;
  private final int gold;
  private final int gems;
  private final int trophies;
  private final String rank;
  private final String inventoryJson;

  public UserRecord(
      String id,
      String displayName,
      String username,
      String avatarUrl,
      String countryCode,
      String title,
      int level,
      int xp,
      int gold,
      int gems,
      int trophies,
      String rank,
      String inventoryJson) {
    this.id = id;
    this.displayName = displayName;
    this.username = username;
    this.avatarUrl = avatarUrl;
    this.countryCode = countryCode;
    this.title = title;
    this.level = level;
    this.xp = xp;
    this.gold = gold;
    this.gems = gems;
    this.trophies = trophies;
    this.rank = rank;
    this.inventoryJson = inventoryJson;
  }

  public String id() { return id; }
  public String displayName() { return displayName; }
  public String username() { return username; }
  public String avatarUrl() { return avatarUrl; }
  public String countryCode() { return countryCode; }
  public String title() { return title; }
  public int level() { return level; }
  public int xp() { return xp; }
  public int gold() { return gold; }
  public int gems() { return gems; }
  public int trophies() { return trophies; }
  public String rank() { return rank; }
  public String inventoryJson() { return inventoryJson; }
}
