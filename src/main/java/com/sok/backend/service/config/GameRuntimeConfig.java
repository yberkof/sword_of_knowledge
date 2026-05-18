package com.sok.backend.service.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.*;

public class GameRuntimeConfig {
  private int minPlayers = 2, maxPlayers = 6, initialCastleHp = 3, claimFirstPicks = 2, claimSecondPicks = 1;
  private int maxRounds = 30, maxMatchDurationSeconds = 1800, reconnectGraceSeconds = 90, maxMcqTieRetries = 2, xoDrawMaxReplay = 1;
  
  // New Timing Configuration (Timeline)
  private int phaseAnimationBufferMs = 4000;
  private int rankingSlideInMs = 2000;
  private int answerAnimationMs = 4000;
  private int victoryCinematicMs = 5000;
  private int estimationTurnMs = 18000;
  private int claimPickTurnMs = 15000;
  private int battleAttackTurnMs = 35000;
  private int duelMcqMs = 10000;
  private int minigameMoveTurnMs = 10000;
  private int tiebreakNumericMs = 10000;

  // Legacy fields (for backward compatibility if needed, but we'll prefer the new ones)
  private int duelDurationMs = 10000, claimDurationMs = 18000, tiebreakDurationMs = 10000;
  private int avoidBombsPlacementMs = 30000, collectionPickMs = 25000, memoryPeekMs = 15000;
  private int rhythmTimeoutBaseMs = 25000, rhythmTimeoutExtraPerRoundMs = 2000;

  private String defaultQuestionCategory = "general", defaultMapId = "basic_1v1_map";
  private String defaultMatchMode = "ffa", defaultRulesetId = "sok_v1", tieBreakerMode = "minigame_collection";
  private Map<String, Integer> regionPoints = new HashMap<>();
  private Map<String, List<Integer>> neighbors = new HashMap<>();
  private boolean autoPlaceLastUnplacedCastle = true;
  private List<Integer> castleIndices = new ArrayList<>();

  // Getters and Setters
  public int getMinPlayers() { return minPlayers; }
  public void setMinPlayers(int v) { this.minPlayers = v; }
  public int getMaxPlayers() { return maxPlayers; }
  public void setMaxPlayers(int v) { this.maxPlayers = v; }
  public int getInitialCastleHp() { return initialCastleHp; }
  public void setInitialCastleHp(int v) { this.initialCastleHp = v; }
  public int getClaimFirstPicks() { return claimFirstPicks; }
  public void setClaimFirstPicks(int v) { this.claimFirstPicks = v; }
  public int getClaimSecondPicks() { return claimSecondPicks; }
  public void setClaimSecondPicks(int v) { this.claimSecondPicks = v; }
  public int getMaxRounds() { return maxRounds; }
  public void setMaxRounds(int v) { this.maxRounds = v; }
  public int getMaxMatchDurationSeconds() { return maxMatchDurationSeconds; }
  public void setMaxMatchDurationSeconds(int v) { this.maxMatchDurationSeconds = v; }
  public int getReconnectGraceSeconds() { return reconnectGraceSeconds; }
  public void setReconnectGraceSeconds(int v) { this.reconnectGraceSeconds = v; }
  
  public int getPhaseAnimationBufferMs() { return phaseAnimationBufferMs; }
  public void setPhaseAnimationBufferMs(int v) { this.phaseAnimationBufferMs = v; }
  public int getRankingSlideInMs() { return rankingSlideInMs; }
  public void setRankingSlideInMs(int v) { this.rankingSlideInMs = v; }
  public int getAnswerAnimationMs() { return answerAnimationMs; }
  public void setAnswerAnimationMs(int v) { this.answerAnimationMs = v; }
  public int getVictoryCinematicMs() { return victoryCinematicMs; }
  public void setVictoryCinematicMs(int v) { this.victoryCinematicMs = v; }
  public int getEstimationTurnMs() { return estimationTurnMs; }
  public void setEstimationTurnMs(int v) { this.estimationTurnMs = v; }
  public int getClaimPickTurnMs() { return claimPickTurnMs; }
  public void setClaimPickTurnMs(int v) { this.claimPickTurnMs = v; }
  public int getBattleAttackTurnMs() { return battleAttackTurnMs; }
  public void setBattleAttackTurnMs(int v) { this.battleAttackTurnMs = v; }
  public int getDuelMcqMs() { return duelMcqMs; }
  public void setDuelMcqMs(int v) { this.duelMcqMs = v; }
  public int getMinigameMoveTurnMs() { return minigameMoveTurnMs; }
  public void setMinigameMoveTurnMs(int v) { this.minigameMoveTurnMs = v; }
  public int getTiebreakNumericMs() { return tiebreakNumericMs; }
  public void setTiebreakNumericMs(int v) { this.tiebreakNumericMs = v; }

  public int getDuelDurationMs() { return duelDurationMs; }
  public void setDuelDurationMs(int v) { this.duelDurationMs = v; }
  public int getClaimDurationMs() { return claimDurationMs; }
  public void setClaimDurationMs(int v) { this.claimDurationMs = v; }
  public int getTiebreakDurationMs() { return tiebreakDurationMs; }
  public void setTiebreakDurationMs(int v) { this.tiebreakDurationMs = v; }
  public int getAvoidBombsPlacementMs() { return avoidBombsPlacementMs; }
  public void setAvoidBombsPlacementMs(int v) { this.avoidBombsPlacementMs = v; }
  public int getCollectionPickMs() { return collectionPickMs; }
  public void setCollectionPickMs(int v) { this.collectionPickMs = v; }
  public int getMemoryPeekMs() { return memoryPeekMs; }
  public void setMemoryPeekMs(int v) { this.memoryPeekMs = v; }
  public int getRhythmTimeoutBaseMs() { return rhythmTimeoutBaseMs; }
  public void setRhythmTimeoutBaseMs(int v) { this.rhythmTimeoutBaseMs = v; }
  public int getRhythmTimeoutExtraPerRoundMs() { return rhythmTimeoutExtraPerRoundMs; }
  public void setRhythmTimeoutExtraPerRoundMs(int v) { this.rhythmTimeoutExtraPerRoundMs = v; }

  public String getDefaultQuestionCategory() { return defaultQuestionCategory; }
  public void setDefaultQuestionCategory(String v) { this.defaultQuestionCategory = v; }
  public String getDefaultMapId() { return defaultMapId; }
  public void setDefaultMapId(String v) { this.defaultMapId = v; }
  public String getDefaultMatchMode() { return defaultMatchMode; }
  public void setDefaultMatchMode(String v) { this.defaultMatchMode = v; }
  public String getDefaultRulesetId() { return defaultRulesetId; }
  public void setDefaultRulesetId(String v) { this.defaultRulesetId = v; }
  public String getTieBreakerMode() { return tieBreakerMode; }
  public void setTieBreakerMode(String v) { this.tieBreakerMode = v; }
  public int getMaxMcqTieRetries() { return maxMcqTieRetries; }
  public void setMaxMcqTieRetries(int v) { this.maxMcqTieRetries = v; }
  public int getXoDrawMaxReplay() { return xoDrawMaxReplay; }
  public void setXoDrawMaxReplay(int v) { this.xoDrawMaxReplay = v; }
  public Map<String, Integer> getRegionPoints() { return regionPoints; }
  public void setRegionPoints(Map<String, Integer> v) { this.regionPoints = v; }
  public Map<String, List<Integer>> getNeighbors() { return neighbors; }
  public void setNeighbors(Map<String, List<Integer>> v) { this.neighbors = v; }
  public boolean isAutoPlaceLastUnplacedCastle() { return autoPlaceLastUnplacedCastle; }
  public void setAutoPlaceLastUnplacedCastle(boolean v) { this.autoPlaceLastUnplacedCastle = v; }
  @JsonProperty("castle_indecies") public List<Integer> getCastleIndices() { return castleIndices; }
  @JsonProperty("castle_indecies") public void setCastleIndices(List<Integer> v) { this.castleIndices = v == null ? new ArrayList<>() : new ArrayList<>(v); }
}
