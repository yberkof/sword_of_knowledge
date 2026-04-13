package com.sok.backend.api.dto;

import java.util.List;

public class ProfileResponse {
  private final String uid;
  private final String name;
  private final String username;
  private final String countryFlag;
  private final String title;
  private final int level;
  private final int xp;
  private final int gold;
  private final int gems;
  private final String avatar;
  private final String rank;
  private final int trophies;
  private final List<String> inventory;

  public ProfileResponse(
      String uid,
      String name,
      String username,
      String countryFlag,
      String title,
      int level,
      int xp,
      int gold,
      int gems,
      String avatar,
      String rank,
      int trophies,
      List<String> inventory) {
    this.uid = uid;
    this.name = name;
    this.username = username;
    this.countryFlag = countryFlag;
    this.title = title;
    this.level = level;
    this.xp = xp;
    this.gold = gold;
    this.gems = gems;
    this.avatar = avatar;
    this.rank = rank;
    this.trophies = trophies;
    this.inventory = inventory;
  }

  public String getUid() { return uid; }
  public String getName() { return name; }
  public String getUsername() { return username; }
  public String getCountryFlag() { return countryFlag; }
  public String getTitle() { return title; }
  public int getLevel() { return level; }
  public int getXp() { return xp; }
  public int getGold() { return gold; }
  public int getGems() { return gems; }
  public String getAvatar() { return avatar; }
  public String getRank() { return rank; }
  public int getTrophies() { return trophies; }
  public List<String> getInventory() { return inventory; }
}
