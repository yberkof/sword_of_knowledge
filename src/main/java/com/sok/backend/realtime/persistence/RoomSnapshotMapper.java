package com.sok.backend.realtime.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sok.backend.domain.game.QuestionEngineService;
import com.sok.backend.realtime.match.AnswerMetric;
import com.sok.backend.realtime.match.DuelAnswer;
import com.sok.backend.realtime.match.DuelState;
import com.sok.backend.realtime.match.PlayerState;
import com.sok.backend.realtime.match.RegionState;
import com.sok.backend.realtime.match.RoomState;
import com.sok.backend.domain.game.tiebreaker.MemoryTieBreakRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Maps between live {@link RoomState} and {@link RoomSnapshotDto} for durable / hot storage. */
@Component
public class RoomSnapshotMapper {

  private final ObjectMapper objectMapper;

  public RoomSnapshotMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String toJson(RoomState room) {
    try {
      return objectMapper.writeValueAsString(toDto(room));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("room snapshot serialize", e);
    }
  }

  public RoomSnapshotDto toDto(RoomState room) {
    RoomSnapshotDto d = new RoomSnapshotDto();
    d.id = room.id;
    d.mapId = room.mapId;
    d.matchMode = room.matchMode;
    d.rulesetId = room.rulesetId;
    d.phase = room.phase;
    d.currentTurnIndex = room.currentTurnIndex;
    d.hostUid = room.hostUid;
    d.inviteCode = room.inviteCode;
    d.round = room.round;
    d.roundAttackCount = room.roundAttackCount;
    d.createdAt = room.createdAt;
    d.lastActivityAt = room.lastActivityAt;
    d.matchStartedAt = room.matchStartedAt;
    d.phaseStartedAt = room.phaseStartedAt;
    d.mcqSpeedTieRetries = room.mcqSpeedTieRetries;
    d.tieBreakOverride = room.tieBreakOverride;
    d.scoreByUid = new HashMap<>(room.scoreByUid);
    d.claimPicksLeftByUid = new HashMap<>(room.claimPicksLeftByUid);
    d.claimQueue = new ArrayList<>(room.claimQueue);
    d.claimTurnUid = room.claimTurnUid;
    d.estimationAnswers = new HashMap<>();
    for (Map.Entry<String, AnswerMetric> e : room.estimationAnswers.entrySet()) {
      d.estimationAnswers.put(e.getKey(), toMetricDto(e.getValue()));
    }
    d.activeNumericQuestion = toNumericDto(room.activeNumericQuestion);
    d.activeDuel = toDuelDto(room.activeDuel);
    for (PlayerState p : room.players) {
      d.players.add(toPlayerDto(p));
    }
    for (RegionState r : room.regions.values()) {
      d.regions.add(toRegionDto(r));
    }
    return d;
  }

  public RoomState toRoomState(RoomSnapshotDto d) {
    long now = System.currentTimeMillis();
    RoomState room = new RoomState();
    room.id = d.id;
    room.mapId = d.mapId;
    room.matchMode = d.matchMode;
    room.rulesetId = d.rulesetId;
    room.phase = d.phase == null ? RoomState.PHASE_WAITING : d.phase;
    room.currentTurnIndex = d.currentTurnIndex;
    room.hostUid = d.hostUid;
    room.inviteCode = d.inviteCode;
    room.round = d.round;
    room.roundAttackCount = d.roundAttackCount;
    room.createdAt = d.createdAt;
    room.lastActivityAt = d.lastActivityAt;
    room.matchStartedAt = d.matchStartedAt;
    room.phaseStartedAt = d.phaseStartedAt;
    room.mcqSpeedTieRetries = d.mcqSpeedTieRetries;
    room.tieBreakOverride = d.tieBreakOverride;
    room.scoreByUid = d.scoreByUid == null ? new HashMap<>() : new HashMap<>(d.scoreByUid);
    room.claimPicksLeftByUid =
        d.claimPicksLeftByUid == null ? new HashMap<>() : new HashMap<>(d.claimPicksLeftByUid);
    room.claimQueue = d.claimQueue == null ? new ArrayList<>() : new ArrayList<>(d.claimQueue);
    room.claimTurnUid = d.claimTurnUid;
    room.estimationAnswers = new HashMap<>();
    if (d.estimationAnswers != null) {
      for (Map.Entry<String, RoomSnapshotDto.AnswerMetricDto> e : d.estimationAnswers.entrySet()) {
        room.estimationAnswers.put(e.getKey(), fromMetricDto(e.getValue()));
      }
    }
    room.activeNumericQuestion = fromNumericDto(d.activeNumericQuestion);
    room.activeDuel = fromDuelDto(d.activeDuel);
    room.players = new ArrayList<>();
    room.playersByUid = new HashMap<>();
    if (d.players != null) {
      for (RoomSnapshotDto.PlayerSnapshotDto p : d.players) {
        PlayerState ps = fromPlayerDto(p, now);
        room.players.add(ps);
        room.playersByUid.put(ps.uid, ps);
      }
    }
    room.regions = new HashMap<>();
    if (d.regions != null) {
      for (RoomSnapshotDto.RegionSnapshotDto r : d.regions) {
        RegionState rs = fromRegionDto(r);
        room.regions.put(rs.id, rs);
      }
    }
    return room;
  }

  public RoomSnapshotDto fromJson(String json) {
    try {
      return objectMapper.readValue(json, RoomSnapshotDto.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("room snapshot parse", e);
    }
  }

  private static RoomSnapshotDto.PlayerSnapshotDto toPlayerDto(PlayerState p) {
    RoomSnapshotDto.PlayerSnapshotDto d = new RoomSnapshotDto.PlayerSnapshotDto();
    d.uid = p.uid;
    d.name = p.name;
    d.avatarUrl = p.avatarUrl;
    d.castleHp = p.castleHp;
    d.castleRegionId = p.castleRegionId;
    d.score = p.score;
    d.color = p.color;
    d.teamId = p.teamId;
    d.isEliminated = p.isEliminated;
    d.trophies = p.trophies;
    d.eliminatedAt = p.eliminatedAt;
    return d;
  }

  private static PlayerState fromPlayerDto(RoomSnapshotDto.PlayerSnapshotDto d, long now) {
    PlayerState p = new PlayerState();
    p.uid = d.uid;
    p.name = d.name;
    p.avatarUrl = d.avatarUrl;
    p.socketId = null;
    p.castleHp = d.castleHp;
    p.castleRegionId = d.castleRegionId;
    p.score = d.score;
    p.color = d.color;
    p.teamId = d.teamId;
    p.isEliminated = d.isEliminated;
    p.trophies = d.trophies;
    p.eliminatedAt = d.eliminatedAt;
    p.online = false;
    p.lastSeenAt = now;
    return p;
  }

  private static RoomSnapshotDto.RegionSnapshotDto toRegionDto(RegionState r) {
    RoomSnapshotDto.RegionSnapshotDto d = new RoomSnapshotDto.RegionSnapshotDto();
    d.id = r.id;
    d.ownerUid = r.ownerUid;
    d.isCastle = r.isCastle;
    d.isShielded = r.isShielded;
    d.type = r.type;
    d.points = r.points;
    d.neighbors = r.neighbors == null ? new ArrayList<>() : new ArrayList<>(r.neighbors);
    return d;
  }

  private static RegionState fromRegionDto(RoomSnapshotDto.RegionSnapshotDto d) {
    RegionState r = new RegionState();
    r.id = d.id;
    r.ownerUid = d.ownerUid;
    r.isCastle = d.isCastle;
    r.isShielded = d.isShielded;
    r.type = d.type;
    r.points = d.points;
    r.neighbors = d.neighbors == null ? new ArrayList<>() : new ArrayList<>(d.neighbors);
    return r;
  }

  private static RoomSnapshotDto.AnswerMetricDto toMetricDto(AnswerMetric m) {
    if (m == null) {
      return null;
    }
    RoomSnapshotDto.AnswerMetricDto d = new RoomSnapshotDto.AnswerMetricDto();
    d.uid = m.uid;
    d.value = m.value;
    d.latencyMs = m.latencyMs;
    return d;
  }

  private static AnswerMetric fromMetricDto(RoomSnapshotDto.AnswerMetricDto d) {
    if (d == null) {
      return null;
    }
    AnswerMetric m = new AnswerMetric();
    m.uid = d.uid;
    m.value = d.value;
    m.latencyMs = d.latencyMs;
    return m;
  }

  private static RoomSnapshotDto.NumericQuestionDto toNumericDto(
      QuestionEngineService.NumericQuestion q) {
    if (q == null) {
      return null;
    }
    RoomSnapshotDto.NumericQuestionDto d = new RoomSnapshotDto.NumericQuestionDto();
    d.id = q.id;
    d.text = q.text;
    d.answer = q.answer;
    return d;
  }

  private static QuestionEngineService.NumericQuestion fromNumericDto(
      RoomSnapshotDto.NumericQuestionDto d) {
    if (d == null) {
      return null;
    }
    QuestionEngineService.NumericQuestion q = new QuestionEngineService.NumericQuestion();
    q.id = d.id;
    q.text = d.text;
    q.answer = d.answer;
    return q;
  }

  private static RoomSnapshotDto.McqQuestionDto toMcqDto(QuestionEngineService.McqQuestion q) {
    if (q == null) {
      return null;
    }
    RoomSnapshotDto.McqQuestionDto d = new RoomSnapshotDto.McqQuestionDto();
    d.id = q.id;
    d.text = q.text;
    d.options = q.options == null ? null : new ArrayList<>(q.options);
    d.correctIndex = q.correctIndex;
    d.category = q.category;
    return d;
  }

  private static QuestionEngineService.McqQuestion fromMcqDto(RoomSnapshotDto.McqQuestionDto d) {
    if (d == null) {
      return null;
    }
    QuestionEngineService.McqQuestion q = new QuestionEngineService.McqQuestion();
    q.id = d.id;
    q.text = d.text;
    q.options = d.options == null ? null : new ArrayList<>(d.options);
    q.correctIndex = d.correctIndex;
    q.category = d.category;
    return q;
  }

  private static RoomSnapshotDto.DuelAnswerDto toDuelAnswerDto(DuelAnswer a) {
    if (a == null) {
      return null;
    }
    RoomSnapshotDto.DuelAnswerDto d = new RoomSnapshotDto.DuelAnswerDto();
    d.answerIndex = a.answerIndex;
    d.timeTaken = a.timeTaken;
    return d;
  }

  private static DuelAnswer fromDuelAnswerDto(RoomSnapshotDto.DuelAnswerDto d) {
    if (d == null) {
      return null;
    }
    DuelAnswer a = new DuelAnswer();
    a.answerIndex = d.answerIndex;
    a.timeTaken = d.timeTaken;
    return a;
  }

  private RoomSnapshotDto.DuelSnapshotDto toDuelDto(DuelState duel) {
    if (duel == null) {
      return null;
    }
    RoomSnapshotDto.DuelSnapshotDto d = new RoomSnapshotDto.DuelSnapshotDto();
    d.attackerUid = duel.attackerUid;
    d.defenderUid = duel.defenderUid;
    d.targetRegionId = duel.targetRegionId;
    d.mcqQuestion = toMcqDto(duel.mcqQuestion);
    d.numericQuestion = toNumericDto(duel.numericQuestion);
    for (Map.Entry<String, DuelAnswer> e : duel.answers.entrySet()) {
      d.answers.put(e.getKey(), toDuelAnswerDto(e.getValue()));
    }
    for (Map.Entry<String, AnswerMetric> e : duel.tiebreakerAnswers.entrySet()) {
      d.tiebreakerAnswers.put(e.getKey(), toMetricDto(e.getValue()));
    }
    d.tiebreakKind = duel.tiebreakKind;
    d.xoCells = duel.xoCells == null ? null : duel.xoCells.clone();
    d.xoTurnUid = duel.xoTurnUid;
    d.xoReplayCount = duel.xoReplayCount;
    d.avoidBombsSubPhase = duel.avoidBombsSubPhase;
    for (Map.Entry<String, int[]> e : duel.avoidBombsBoards.entrySet()) {
      d.avoidBombsBoards.put(e.getKey(), e.getValue().clone());
    }
    for (Map.Entry<String, int[]> e : duel.avoidBombsOpened.entrySet()) {
      d.avoidBombsOpened.put(e.getKey(), e.getValue().clone());
    }
    d.avoidBombsPlaced.putAll(duel.avoidBombsPlaced);
    d.avoidBombsHitsBy.putAll(duel.avoidBombsHitsBy);
    d.avoidBombsTurnUid = duel.avoidBombsTurnUid;

    d.collectionSubPhase = duel.collectionSubPhase;
    d.collectionAttackerPick = duel.collectionAttackerPick;
    d.collectionDefenderPick = duel.collectionDefenderPick;
    d.collectionPickDeadlineAtMs = duel.collectionPickDeadlineAtMs;
    d.rpsAttackerWins = duel.rpsAttackerWins;
    d.rpsDefenderWins = duel.rpsDefenderWins;
    d.rpsPendingAttacker = duel.rpsPendingAttacker;
    d.rpsPendingDefender = duel.rpsPendingDefender;
    d.rhythmRound = duel.rhythmRound;
    d.rhythmSequence = duel.rhythmSequence == null ? null : duel.rhythmSequence.clone();
    d.rhythmRoundDeadlineAtMs = duel.rhythmRoundDeadlineAtMs;
    d.rhythmPendingAttackerInput = duel.rhythmPendingAttackerInput;
    d.rhythmPendingDefenderInput = duel.rhythmPendingDefenderInput;
    d.memorySubPhase = duel.memorySubPhase;
    d.memoryPairByCell =
        duel.memoryPairByCell == null ? null : duel.memoryPairByCell.clone();
    d.memoryMatchedFlags = memoryMatchedToFlags(duel.memoryMatched);
    d.memoryFirstPickIndex = duel.memoryFirstPickIndex;
    d.memoryTurnUid = duel.memoryTurnUid;
    d.memoryAttackerPairs = duel.memoryAttackerPairs;
    d.memoryDefenderPairs = duel.memoryDefenderPairs;
    d.memoryPeekEndsAtMs = duel.memoryPeekEndsAtMs;
    return d;
  }

  private DuelState fromDuelDto(RoomSnapshotDto.DuelSnapshotDto d) {
    if (d == null) {
      return null;
    }
    DuelState duel = new DuelState();
    duel.attackerUid = d.attackerUid;
    duel.defenderUid = d.defenderUid;
    duel.targetRegionId = d.targetRegionId;
    duel.mcqQuestion = fromMcqDto(d.mcqQuestion);
    duel.numericQuestion = fromNumericDto(d.numericQuestion);
    duel.answers = new HashMap<>();
    if (d.answers != null) {
      for (Map.Entry<String, RoomSnapshotDto.DuelAnswerDto> e : d.answers.entrySet()) {
        duel.answers.put(e.getKey(), fromDuelAnswerDto(e.getValue()));
      }
    }
    duel.tiebreakerAnswers = new HashMap<>();
    if (d.tiebreakerAnswers != null) {
      for (Map.Entry<String, RoomSnapshotDto.AnswerMetricDto> e : d.tiebreakerAnswers.entrySet()) {
        duel.tiebreakerAnswers.put(e.getKey(), fromMetricDto(e.getValue()));
      }
    }
    duel.tiebreakKind = d.tiebreakKind;
    duel.xoCells = d.xoCells == null ? null : d.xoCells.clone();
    duel.xoTurnUid = d.xoTurnUid;
    duel.xoReplayCount = d.xoReplayCount;
    duel.avoidBombsSubPhase = d.avoidBombsSubPhase;
    duel.avoidBombsBoards = new HashMap<>();
    if (d.avoidBombsBoards != null) {
      for (Map.Entry<String, int[]> e : d.avoidBombsBoards.entrySet()) {
        duel.avoidBombsBoards.put(e.getKey(), e.getValue().clone());
      }
    }
    duel.avoidBombsOpened = new HashMap<>();
    if (d.avoidBombsOpened != null) {
      for (Map.Entry<String, int[]> e : d.avoidBombsOpened.entrySet()) {
        duel.avoidBombsOpened.put(e.getKey(), e.getValue().clone());
      }
    }
    duel.avoidBombsPlaced =
        d.avoidBombsPlaced == null ? new HashMap<>() : new HashMap<>(d.avoidBombsPlaced);
    duel.avoidBombsHitsBy =
        d.avoidBombsHitsBy == null ? new HashMap<>() : new HashMap<>(d.avoidBombsHitsBy);
    duel.avoidBombsTurnUid = d.avoidBombsTurnUid;

    duel.collectionSubPhase = d.collectionSubPhase;
    duel.collectionAttackerPick = d.collectionAttackerPick;
    duel.collectionDefenderPick = d.collectionDefenderPick;
    duel.collectionPickDeadlineAtMs = d.collectionPickDeadlineAtMs;
    duel.rpsAttackerWins = d.rpsAttackerWins;
    duel.rpsDefenderWins = d.rpsDefenderWins;
    duel.rpsPendingAttacker = d.rpsPendingAttacker;
    duel.rpsPendingDefender = d.rpsPendingDefender;
    duel.rhythmRound = d.rhythmRound;
    duel.rhythmSequence = d.rhythmSequence == null ? null : d.rhythmSequence.clone();
    duel.rhythmRoundDeadlineAtMs = d.rhythmRoundDeadlineAtMs;
    duel.rhythmPendingAttackerInput = d.rhythmPendingAttackerInput;
    duel.rhythmPendingDefenderInput = d.rhythmPendingDefenderInput;
    duel.memorySubPhase = d.memorySubPhase;
    duel.memoryPairByCell =
        d.memoryPairByCell == null ? null : d.memoryPairByCell.clone();
    duel.memoryMatched = flagsToMemoryMatched(d.memoryMatchedFlags);
    duel.memoryFirstPickIndex = d.memoryFirstPickIndex;
    duel.memoryTurnUid = d.memoryTurnUid;
    duel.memoryAttackerPairs = d.memoryAttackerPairs;
    duel.memoryDefenderPairs = d.memoryDefenderPairs;
    duel.memoryPeekEndsAtMs = d.memoryPeekEndsAtMs;
    return duel;
  }

  private static int[] memoryMatchedToFlags(boolean[] matched) {
    if (matched == null) return null;
    int[] out = new int[matched.length];
    for (int i = 0; i < matched.length; i++) {
      out[i] = matched[i] ? 1 : 0;
    }
    return out;
  }

  private static boolean[] flagsToMemoryMatched(int[] flags) {
    if (flags == null) return null;
    boolean[] out = new boolean[MemoryTieBreakRules.GRID_CELLS];
    for (int i = 0; i < flags.length && i < out.length; i++) {
      out[i] = flags[i] != 0;
    }
    return out;
  }
}
