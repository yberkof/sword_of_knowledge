package com.sok.backend.realtime.persistence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializable snapshot of {@link com.sok.backend.realtime.match.RoomState} for Redis + Postgres.
 * Excludes {@code timers} and connection-only player fields ({@code socketId}, {@code online}).
 */
public class RoomSnapshotDto {

  /** Bump when snapshot shape changes (future migrations). */
  public String schemaVersion = "1";

  public String id;
  public String mapId;
  public String matchMode;
  public String rulesetId;

  public List<PlayerSnapshotDto> players = new ArrayList<>();

  public List<RegionSnapshotDto> regions = new ArrayList<>();

  public String phase;
  public int currentTurnIndex;
  public String hostUid;
  public String inviteCode;
  public int round;
  public int roundAttackCount;

  public DuelSnapshotDto activeDuel;

  public long createdAt;
  public long lastActivityAt;
  public long matchStartedAt;
  public long phaseStartedAt;

  public Map<String, Integer> scoreByUid = new HashMap<>();
  public Map<String, Integer> claimPicksLeftByUid = new HashMap<>();
  public List<String> claimQueue = new ArrayList<>();
  public String claimTurnUid;
  public Map<String, AnswerMetricDto> estimationAnswers = new HashMap<>();

  public NumericQuestionDto activeNumericQuestion;

  public int mcqSpeedTieRetries;
  public String tieBreakOverride;

  public static class PlayerSnapshotDto {
    public String uid;
    public String name;
    public int castleHp;
    public Integer castleRegionId;
    public int score;
    public String color;
    public String teamId;
    public boolean isEliminated;
    public int trophies;
    public Long eliminatedAt;
  }

  public static class RegionSnapshotDto {
    public int id;
    public String ownerUid;
    public boolean isCastle;
    public boolean isShielded;
    public String type;
    public int points;
    public List<Integer> neighbors = new ArrayList<>();
  }

  public static class AnswerMetricDto {
    public String uid;
    public int value;
    public long latencyMs;
  }

  public static class NumericQuestionDto {
    public String id;
    public String text;
    public int answer;
  }

  public static class McqQuestionDto {
    public String id;
    public String text;
    public List<String> options;
    public int correctIndex;
    public String category;
  }

  public static class DuelAnswerDto {
    public int answerIndex;
    public long timeTaken;
  }

  public static class DuelSnapshotDto {
    public String attackerUid;
    public String defenderUid;
    public int targetRegionId;
    public McqQuestionDto mcqQuestion;
    public NumericQuestionDto numericQuestion;
    public Map<String, DuelAnswerDto> answers = new HashMap<>();
    public Map<String, AnswerMetricDto> tiebreakerAnswers = new HashMap<>();
    public String tiebreakKind;
    public int[] xoCells;
    public String xoTurnUid;
    public int xoReplayCount;

    public String avoidBombsSubPhase;
    public Map<String, int[]> avoidBombsBoards = new HashMap<>();
    public Map<String, int[]> avoidBombsOpened = new HashMap<>();
    public Map<String, Boolean> avoidBombsPlaced = new HashMap<>();
    public Map<String, Integer> avoidBombsHitsBy = new HashMap<>();
    public String avoidBombsTurnUid;

    public String collectionSubPhase;
    public String collectionAttackerPick;
    public String collectionDefenderPick;
    public Long collectionPickDeadlineAtMs;

    public int rpsAttackerWins;
    public int rpsDefenderWins;
    public String rpsPendingAttacker;
    public String rpsPendingDefender;

    public int rhythmRound;
    public int[] rhythmSequence;
    public Long rhythmRoundDeadlineAtMs;
    public String rhythmPendingAttackerInput;
    public String rhythmPendingDefenderInput;

    public String memorySubPhase;
    public int[] memoryPairByCell;
    /** Same length as memory grid (36 cells), 1 = matched. */
    public int[] memoryMatchedFlags;
    public int memoryFirstPickIndex;
    public String memoryTurnUid;
    public int memoryAttackerPairs;
    public int memoryDefenderPairs;
    public Long memoryPeekEndsAtMs;
  }
}
