package com.sok.backend.service.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameRuntimeConfig {
  private int minPlayers = 2;
  private int maxPlayers = 6;
  private int initialCastleHp = 3;
  private int claimFirstPicks = 2;
  private int claimSecondPicks = 1;
  private int duelDurationMs = 10000;
  private int claimDurationMs = 18000;
  private int tiebreakDurationMs = 12000;
  private int maxRounds = 30;
  private int maxMatchDurationSeconds = 1800;
  private int reconnectGraceSeconds = 90;
  private String defaultQuestionCategory = "general";
  /** Sent on every room_update as mapId for Marefa map registry matching. */
  private String defaultMapId = "basic_1v1_map";
  /** Default match grouping: "ffa" or "teams_2v2" (teams require exactly four players). */
  private String defaultMatchMode = "ffa";
  /** Identifier for rule plugins / alternate game loops on the same socket protocol. */
  private String defaultRulesetId = "sok_v1";
  /**
   * After an MCQ duel where both players are correct with identical latency:
   * {@code numeric_closest} — estimation question (default); {@code mcq_retry} — extra MCQ round(s)
   * then estimation; {@code attacker_advantage} — attacker wins immediately;
   * {@code minigame_xo} — tic-tac-toe on a 3×3 grid.
   */
  private String tieBreakerMode = "numeric_closest";
  /** Used only when tieBreakerMode is mcq_retry. */
  private int maxMcqTieRetries = 2;
  /** After a drawn X-O board, replay up to this many times before defender wins the duel. */
  private int xoDrawMaxReplay = 1;
  private Map<String, Integer> regionPoints = new HashMap<String, Integer>();
  private Map<String, List<Integer>> neighbors = new HashMap<String, List<Integer>>();

  public static GameRuntimeConfig withDefaults() {
    GameRuntimeConfig cfg = new GameRuntimeConfig();
    for (int i = 0; i < 13; i++) cfg.regionPoints.put(String.valueOf(i), 1);
    cfg.regionPoints.put("6", 2);
    cfg.regionPoints.put("7", 2);
    cfg.regionPoints.put("8", 2);
    cfg.regionPoints.put("9", 2);
    cfg.regionPoints.put("10", 3);
    cfg.regionPoints.put("11", 3);
    cfg.regionPoints.put("12", 3);
    cfg.neighbors.put("0", list(1, 5));
    cfg.neighbors.put("1", list(0, 2, 5, 6));
    cfg.neighbors.put("2", list(1, 3, 6, 7));
    cfg.neighbors.put("3", list(2, 4, 7, 8));
    cfg.neighbors.put("4", list(3, 8, 9));
    cfg.neighbors.put("5", list(0, 1, 6));
    cfg.neighbors.put("6", list(1, 2, 5, 7, 10));
    cfg.neighbors.put("7", list(2, 3, 6, 8, 10, 11));
    cfg.neighbors.put("8", list(3, 4, 7, 9, 11, 12));
    cfg.neighbors.put("9", list(4, 8, 12));
    cfg.neighbors.put("10", list(6, 7, 11));
    cfg.neighbors.put("11", list(7, 8, 10, 12));
    cfg.neighbors.put("12", list(8, 9, 11));
    return cfg;
  }

  private static List<Integer> list(int... values) {
    ArrayList<Integer> out = new ArrayList<Integer>();
    for (int value : values) out.add(value);
    return out;
  }

  public int getMinPlayers() { return minPlayers; }
  public void setMinPlayers(int minPlayers) { this.minPlayers = minPlayers; }
  public int getMaxPlayers() { return maxPlayers; }
  public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }
  public int getInitialCastleHp() { return initialCastleHp; }
  public void setInitialCastleHp(int initialCastleHp) { this.initialCastleHp = initialCastleHp; }
  public int getClaimFirstPicks() { return claimFirstPicks; }
  public void setClaimFirstPicks(int claimFirstPicks) { this.claimFirstPicks = claimFirstPicks; }
  public int getClaimSecondPicks() { return claimSecondPicks; }
  public void setClaimSecondPicks(int claimSecondPicks) { this.claimSecondPicks = claimSecondPicks; }
  public int getDuelDurationMs() { return duelDurationMs; }
  public void setDuelDurationMs(int duelDurationMs) { this.duelDurationMs = duelDurationMs; }
  public int getClaimDurationMs() { return claimDurationMs; }
  public void setClaimDurationMs(int claimDurationMs) { this.claimDurationMs = claimDurationMs; }
  public int getTiebreakDurationMs() { return tiebreakDurationMs; }
  public void setTiebreakDurationMs(int tiebreakDurationMs) { this.tiebreakDurationMs = tiebreakDurationMs; }
  public int getMaxRounds() { return maxRounds; }
  public void setMaxRounds(int maxRounds) { this.maxRounds = maxRounds; }
  public int getMaxMatchDurationSeconds() { return maxMatchDurationSeconds; }
  public void setMaxMatchDurationSeconds(int maxMatchDurationSeconds) { this.maxMatchDurationSeconds = maxMatchDurationSeconds; }
  public int getReconnectGraceSeconds() { return reconnectGraceSeconds; }
  public void setReconnectGraceSeconds(int reconnectGraceSeconds) { this.reconnectGraceSeconds = reconnectGraceSeconds; }
  public String getDefaultQuestionCategory() { return defaultQuestionCategory; }
  public void setDefaultQuestionCategory(String defaultQuestionCategory) { this.defaultQuestionCategory = defaultQuestionCategory; }
  public String getDefaultMapId() { return defaultMapId; }
  public void setDefaultMapId(String defaultMapId) { this.defaultMapId = defaultMapId; }
  public String getDefaultMatchMode() { return defaultMatchMode; }
  public void setDefaultMatchMode(String defaultMatchMode) { this.defaultMatchMode = defaultMatchMode; }
  public String getDefaultRulesetId() { return defaultRulesetId; }
  public void setDefaultRulesetId(String defaultRulesetId) { this.defaultRulesetId = defaultRulesetId; }
  public String getTieBreakerMode() { return tieBreakerMode; }
  public void setTieBreakerMode(String tieBreakerMode) { this.tieBreakerMode = tieBreakerMode; }
  public int getMaxMcqTieRetries() { return maxMcqTieRetries; }
  public void setMaxMcqTieRetries(int maxMcqTieRetries) { this.maxMcqTieRetries = maxMcqTieRetries; }
  public int getXoDrawMaxReplay() { return xoDrawMaxReplay; }
  public void setXoDrawMaxReplay(int xoDrawMaxReplay) { this.xoDrawMaxReplay = xoDrawMaxReplay; }
  public Map<String, Integer> getRegionPoints() { return regionPoints; }
  public void setRegionPoints(Map<String, Integer> regionPoints) { this.regionPoints = regionPoints; }
  public Map<String, List<Integer>> getNeighbors() { return neighbors; }
  public void setNeighbors(Map<String, List<Integer>> neighbors) { this.neighbors = neighbors; }
}
