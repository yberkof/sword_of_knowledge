package com.sok.backend.service.config;

import com.fasterxml.jackson.annotation.JsonProperty;
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
   * {@code minigame_xo} — tic-tac-toe on a 3×3 grid;
   * {@code minigame_avoid_bombs} — hidden-bomb hunt on a 3×3 grid (first to open 3 bombs loses);
   * {@code minigame_collection} — players vote then play one sub-minigame (avoid bombs, RPS, rhythm, memory).
   */
  /** Default: vote lobby then one sub-minigame (see {@code minigame_collection}). */
  private String tieBreakerMode = "minigame_collection";
  /** Used only when tieBreakerMode is mcq_retry. */
  private int maxMcqTieRetries = 2;
  /** After a drawn X-O board, replay up to this many times before defender wins the duel. */
  private int xoDrawMaxReplay = 1;
  /** Window each player has to secretly place their 3 bombs in the avoid-bombs minigame. */
  private int avoidBombsPlacementMs = 15000;
  /** Votes for sub-minigame in collection tie-break. */
  private int collectionPickMs = 20000;
  private int memoryPeekMs = 10000;
  private int rhythmTimeoutBaseMs = 15000;
  private int rhythmTimeoutExtraPerRoundMs = 1000;
  private Map<String, Integer> regionPoints = new HashMap<String, Integer>();
  private Map<String, List<Integer>> neighbors = new HashMap<String, List<Integer>>();
  /**
   * When one player is left without a castle, server picks a random unclaimed region instead of
   * waiting (AFK / disconnect safety).
   */
  private boolean autoPlaceLastUnplacedCastle = true;
  /**
   * Auto castle placement (last AFK player): hex id per seat index (same order as {@code room.players},
   * index 0 = first joined). JSON key {@code castle_indecies} — wire name kept as requested.
   */
  private List<Integer> castleIndices = new ArrayList<Integer>();

  public static GameRuntimeConfig withDefaults() {
    GameRuntimeConfig cfg = new GameRuntimeConfig();
    // Neutral hexes 1, 4, 5 (1pt) and 6, 7, 8 (2pt)
    for (int i = 1; i <= 8; i++) cfg.regionPoints.put(String.valueOf(i), 1);
    cfg.regionPoints.put("5", 2);
    // Default 1v1: left castle hex 3, right castle hex 2 (see neighbors comments below).
    cfg.castleIndices = list(3, 2);

    // Topology based on screenshot (Reciprocal/Bi-directional)
    cfg.neighbors.put("1", list(3, 6));       // 1 touches Left Castle (now 3) and Forest (6)
    cfg.neighbors.put("3", list(1, 4, 5));    // Left Castle (ID 3) touches 1, 4, 5
    cfg.neighbors.put("2", list(7, 8, 5));    // Right Castle (ID 2) touches 7, 8, 5
    cfg.neighbors.put("4", list(3, 5,8 ));       // 4 touches Left Castle (3) and Village (5)
    cfg.neighbors.put("5", list(2, 3, 4, 6, 7, 8)); // Village touches everyone
    cfg.neighbors.put("6", list(1, 5, 7));    // 6 touches 1, 5, 7
    cfg.neighbors.put("7", list(2, 5, 6, 8)); // 7 touches Right Castle (2), 5, 6, 8
    cfg.neighbors.put("8", list(2, 5, 4));    // 8 touches Right Castle (2), 5, 7
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
  public int getAvoidBombsPlacementMs() { return avoidBombsPlacementMs; }
  public void setAvoidBombsPlacementMs(int avoidBombsPlacementMs) { this.avoidBombsPlacementMs = avoidBombsPlacementMs; }
  public int getCollectionPickMs() { return collectionPickMs; }
  public void setCollectionPickMs(int collectionPickMs) { this.collectionPickMs = collectionPickMs; }
  public int getMemoryPeekMs() { return memoryPeekMs; }
  public void setMemoryPeekMs(int memoryPeekMs) { this.memoryPeekMs = memoryPeekMs; }
  public int getRhythmTimeoutBaseMs() { return rhythmTimeoutBaseMs; }
  public void setRhythmTimeoutBaseMs(int rhythmTimeoutBaseMs) {
    this.rhythmTimeoutBaseMs = rhythmTimeoutBaseMs;
  }
  public int getRhythmTimeoutExtraPerRoundMs() {
    return rhythmTimeoutExtraPerRoundMs;
  }
  public void setRhythmTimeoutExtraPerRoundMs(int rhythmTimeoutExtraPerRoundMs) {
    this.rhythmTimeoutExtraPerRoundMs = rhythmTimeoutExtraPerRoundMs;
  }
  public Map<String, Integer> getRegionPoints() { return regionPoints; }
  public void setRegionPoints(Map<String, Integer> regionPoints) { this.regionPoints = regionPoints; }
  public Map<String, List<Integer>> getNeighbors() { return neighbors; }
  public void setNeighbors(Map<String, List<Integer>> neighbors) { this.neighbors = neighbors; }
  public boolean isAutoPlaceLastUnplacedCastle() { return autoPlaceLastUnplacedCastle; }
  public void setAutoPlaceLastUnplacedCastle(boolean autoPlaceLastUnplacedCastle) {
    this.autoPlaceLastUnplacedCastle = autoPlaceLastUnplacedCastle;
  }

  @JsonProperty("castle_indecies")
  public List<Integer> getCastleIndices() {
    return castleIndices;
  }

  @JsonProperty("castle_indecies")
  public void setCastleIndices(List<Integer> castleIndices) {
    this.castleIndices =
        castleIndices == null ? new ArrayList<Integer>() : new ArrayList<Integer>(castleIndices);
  }
}
