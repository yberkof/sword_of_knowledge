package com.sok.backend.realtime.match;

import com.corundumstudio.socketio.SocketIOServer;
import com.sok.backend.domain.game.BattlePhaseService;
import com.sok.backend.domain.game.QuestionEngineService;
import com.sok.backend.domain.game.tiebreaker.McqSpeedTieOutcome;
import com.sok.backend.domain.game.tiebreaker.McqSpeedTieResolutionService;
import com.sok.backend.domain.game.tiebreaker.NumericTiebreakEvaluator;
import com.sok.backend.domain.game.tiebreaker.TieBreakerAnswerAutofill;
import com.sok.backend.domain.game.tiebreaker.TieBreakerAttackPhaseComposer;
import com.sok.backend.domain.game.tiebreaker.TieBreakerAttackPhaseStrategy;
import com.sok.backend.domain.game.tiebreaker.TieBreakerModeIds;
import com.sok.backend.domain.game.tiebreaker.TieBreakerRealtimeBridge;
import com.sok.backend.realtime.RoundLastSubmitEmitter;
import com.sok.backend.realtime.persistence.RoomSnapshotCoordinator;
import com.sok.backend.realtime.room.RoomBroadcaster;
import com.sok.backend.realtime.room.RoomClientSnapshotFactory;
import com.sok.backend.realtime.room.RoomExecutorRegistry;
import com.sok.backend.realtime.room.RoomRulesResolver;
import com.sok.backend.realtime.room.RoomTimerScheduler;
import com.sok.backend.service.config.GameRuntimeConfig;
import com.sok.backend.service.config.RuntimeGameConfigService;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Orchestrates the battle phase, duels, and tie-breakers. */
@Component
public class BattleOrchestrator {
  private static final Logger log = LoggerFactory.getLogger(BattleOrchestrator.class);
  private static final String PHASE_BATTLE = "battle";
  private static final String PHASE_DUEL = "duel";
  private static final String PHASE_TIE = "battle_tiebreaker";
  private static final String TIMER_DUEL = "duel_timeout";

  private final QuestionEngineService questionEngineService;
  private final BattlePhaseService battlePhaseService;
  private final McqSpeedTieResolutionService mcqSpeedTieResolutionService;
  private final TieBreakerAttackPhaseComposer tieBreakerAttackPhaseComposer;
  private final RuntimeGameConfigService runtimeConfigService;
  private final RoomBroadcaster broadcaster;
  private final RoomClientSnapshotFactory snapshotFactory;
  private final RoomTimerScheduler roomTimers;
  private final RoomExecutorRegistry executors;
  private final RoomRulesResolver rulesResolver;
  private final MatchOutcomeService matchOutcome;
  private final RoomSnapshotCoordinator snapshotCoordinator;
  private final RoundLastSubmitEmitter roundLastSubmitEmitter;

  public BattleOrchestrator(
      QuestionEngineService questionEngineService,
      BattlePhaseService battlePhaseService,
      McqSpeedTieResolutionService mcqSpeedTieResolutionService,
      TieBreakerAttackPhaseComposer tieBreakerAttackPhaseComposer,
      RuntimeGameConfigService runtimeConfigService,
      RoomBroadcaster broadcaster,
      RoomClientSnapshotFactory snapshotFactory,
      RoomTimerScheduler roomTimers,
      RoomExecutorRegistry executors,
      RoomRulesResolver rulesResolver,
      MatchOutcomeService matchOutcome,
      RoomSnapshotCoordinator snapshotCoordinator,
      RoundLastSubmitEmitter roundLastSubmitEmitter) {
    this.questionEngineService = questionEngineService;
    this.battlePhaseService = battlePhaseService;
    this.mcqSpeedTieResolutionService = mcqSpeedTieResolutionService;
    this.tieBreakerAttackPhaseComposer = tieBreakerAttackPhaseComposer;
    this.runtimeConfigService = runtimeConfigService;
    this.broadcaster = broadcaster;
    this.snapshotFactory = snapshotFactory;
    this.roomTimers = roomTimers;
    this.executors = executors;
    this.rulesResolver = rulesResolver;
    this.matchOutcome = matchOutcome;
    this.snapshotCoordinator = snapshotCoordinator;
    this.roundLastSubmitEmitter = roundLastSubmitEmitter;
  }

  public void startBattlePhase(SocketIOServer server, RoomState room) {
    room.phase = PHASE_BATTLE;
    room.currentTurnIndex = firstAliveIndex(room);
    room.phaseStartedAt = System.currentTimeMillis();
    log.info(
        "sok startBattlePhase roomId={} turnIndex={} turnUid={}",
        room.id,
        room.currentTurnIndex,
        room.players.isEmpty() ? "none" : room.players.get(room.currentTurnIndex).uid);
    HashMap<String, Object> payload = new HashMap<>();
    payload.put("phase", PHASE_BATTLE);
    payload.put("round", room.round);
    server.getRoomOperations(room.id).sendEvent("phase_changed", payload);
    broadcaster.emitRoomUpdate(room);
    snapshotCoordinator.snapshotDurable(room);
  }

  public int firstAliveIndex(RoomState room) {
    for (int i = 0; i < room.players.size(); i++) {
      if (!room.players.get(i).isEliminated) return i;
    }
    return 0;
  }

  public boolean canAttackRegion(RoomState room, String attackerUid, int targetRegionId) {
    RegionState target = room.regions.get(targetRegionId);
    if (target == null) return false;
    String ownerUid = target.ownerUid;
    if (ownerUid != null && rulesResolver.sameTeam(room, attackerUid, ownerUid)) return false;
    for (Integer n : target.neighbors) {
      RegionState near = room.regions.get(n);
      if (near != null && attackerUid.equals(near.ownerUid)) return true;
    }
    return false;
  }

  public void startDuel(
      SocketIOServer server,
      RoomState room,
      String attackerUid,
      int targetRegionId,
      boolean tieBreakerAttack) {
    RegionState target = room.regions.get(targetRegionId);
    if (target == null) return;
    GameRuntimeConfig cfg = runtimeConfigService.get();
    room.phase = tieBreakerAttack ? PHASE_TIE : PHASE_DUEL;
    room.phaseStartedAt = System.currentTimeMillis();
    DuelState duel = new DuelState();
    duel.attackerUid = attackerUid;
    duel.defenderUid = target.ownerUid == null ? "neutral" : target.ownerUid;
    duel.targetRegionId = targetRegionId;
    if (tieBreakerAttack) {
      String effective =
          room.tieBreakOverride != null
              ? room.tieBreakOverride
              : TieBreakerModeIds.normalize(cfg.getTieBreakerMode());
      room.tieBreakOverride = null;
      boolean needsHumanDefender =
          TieBreakerModeIds.MINIGAME_XO.equals(effective)
              || TieBreakerModeIds.MINIGAME_AVOID_BOMBS.equals(effective)
              || TieBreakerModeIds.MINIGAME_COLLECTION.equals(effective);
      if (needsHumanDefender && "neutral".equals(duel.defenderUid)) {
        effective = TieBreakerModeIds.NUMERIC_CLOSEST;
      }
      TieBreakerAttackPhaseStrategy strat;
      try {
        strat = tieBreakerAttackPhaseComposer.resolve(effective, duel.defenderUid);
      } catch (RuntimeException ex) {
        log.error(
            "sok tieBreak strategy resolve failed room={} effective={} defender={} err={}",
            room.id,
            effective,
            duel.defenderUid,
            ex.toString());
        throw ex;
      }
      log.info(
          "sok tieBreak strategy resolved room={} effective={} defender={} strat={}",
          room.id,
          effective,
          duel.defenderUid,
          strat.getClass().getSimpleName());
      strat.begin(
          new TieBreakerAttackPhaseStrategy.BeginContext() {
            @Override
            public DuelState duel() {
              return duel;
            }

            @Override
            public TieBreakerRealtimeBridge bridge() {
              return tieBreakerBridge(server, room);
            }

            @Override
            public long phaseStartedAtMs() {
              return room.phaseStartedAt;
            }

            @Override
            public Runnable onNumericTiebreakDeadline() {
              return () -> resolveTiebreaker(server, room);
            }
          });
    } else {
      duel.mcqQuestion = questionEngineService.nextMcqQuestion(cfg.getDefaultQuestionCategory());
      server
          .getRoomOperations(room.id)
          .sendEvent(
              "duel_start",
              mcqDuelPayload(duel, room.phaseStartedAt, cfg.getDuelDurationMs()));
      roomTimers.scheduleTimer(
          room,
          TIMER_DUEL,
          cfg.getDuelDurationMs() + 50L,
          () -> {
            autoFillDuel(duel, cfg.getDuelDurationMs());
            resolveDuel(server, room);
          });
    }
    room.activeDuel = duel;
    broadcaster.emitRoomUpdate(room);
    snapshotCoordinator.snapshotDurable(room);
  }

  public Map<String, Object> mcqDuelPayload(DuelState duel, long now, int durationMs) {
    HashMap<String, Object> payload = new HashMap<>();
    payload.put("question", questionEngineService.toClient(duel.mcqQuestion, now, durationMs));
    payload.put("serverNowMs", now);
    payload.put("phaseEndsAt", now + durationMs);
    payload.put("duelDurationMs", durationMs);
    payload.put("hiddenOptionIndices", new java.util.ArrayList<Integer>());
    payload.put("duelHammerConsumed", false);
    payload.put("attackerUid", duel.attackerUid);
    payload.put("defenderUid", duel.defenderUid);
    payload.put("targetHexId", duel.targetRegionId);
    return payload;
  }

  public void resolveDuel(SocketIOServer server, RoomState room) {
    roomTimers.cancelTimer(room, TIMER_DUEL);
    if (room.activeDuel == null || room.activeDuel.mcqQuestion == null) return;
    DuelState duel = room.activeDuel;
    log.info(
        "sok resolveDuel start room={} attacker={} defender={} phase={}",
        room.id,
        duel.attackerUid,
        duel.defenderUid,
        room.phase);
    DuelAnswer attacker = duel.answers.get(duel.attackerUid);
    DuelAnswer defender =
        "neutral".equals(duel.defenderUid) ? null : duel.answers.get(duel.defenderUid);
    boolean attackerCorrect = attacker != null && attacker.answerIndex == duel.mcqQuestion.correctIndex;
    boolean defenderCorrect =
        !"neutral".equals(duel.defenderUid)
            && defender != null
            && defender.answerIndex == duel.mcqQuestion.correctIndex;

    GameRuntimeConfig cfgTb = runtimeConfigService.get();
    if (!"neutral".equals(duel.defenderUid) && attackerCorrect && defenderCorrect) {
      log.info(
          "sok duel MCQ tie (same latency) room={} tieBreakerMode={}",
          room.id,
          TieBreakerModeIds.normalize(cfgTb.getTieBreakerMode()));
      McqSpeedTieOutcome outcome =
          mcqSpeedTieResolutionService.resolve(cfgTb, room.mcqSpeedTieRetries);
      switch (outcome.action()) {
        case FINISH_BATTLE:
          finishBattle(server, room, outcome.attackerWinsIfFinishing(), true, true, false, duel);
          return;
        case RESTART_MCQ_DUEL:
          room.mcqSpeedTieRetries = outcome.nextMcqRetryCount();
          startDuel(server, room, duel.attackerUid, duel.targetRegionId, false);
          return;
        case ENTER_POST_MCQ_TIE_ATTACK:
          if (outcome.resetMcqRetriesBeforeTieAttack()) {
            room.mcqSpeedTieRetries = 0;
          }
          room.tieBreakOverride = outcome.tieBreakOverrideOrNull();
          startDuel(server, room, duel.attackerUid, duel.targetRegionId, true);
          return;
      }
    }

    long a = attacker == null ? runtimeConfigService.get().getDuelDurationMs() : attacker.timeTaken;
    long d = defender == null ? runtimeConfigService.get().getDuelDurationMs() : defender.timeTaken;
    BattlePhaseService.DuelOutcome outcome =
        battlePhaseService.resolveMcq(
            attackerCorrect, defenderCorrect, a, d, !"neutral".equals(duel.defenderUid));
    boolean attackerWins = outcome == BattlePhaseService.DuelOutcome.ATTACKER_WINS;
    log.info(
        "sok duel outcome room={} attackerWins={} attackerCorrect={} defenderCorrect={}",
        room.id,
        attackerWins,
        attackerCorrect,
        defenderCorrect);
    finishBattle(server, room, attackerWins, attackerCorrect, defenderCorrect, false, duel);
  }

  public void resolveTiebreaker(SocketIOServer server, RoomState room) {
    roomTimers.cancelTimer(room, "tiebreak_timeout");
    if (room.activeDuel == null || room.activeDuel.numericQuestion == null) {
      log.warn(
          "sok resolveTiebreaker skipped roomId={} hasDuel={}", room.id, room.activeDuel != null);
      return;
    }
    if (!"numeric".equals(room.activeDuel.tiebreakKind)) {
      log.warn(
          "sok resolveTiebreaker skipped non-numeric tiebreak kind={} roomId={}",
          room.activeDuel.tiebreakKind,
          room.id);
      return;
    }
    log.info("sok resolveTiebreaker roomId={}", room.id);
    DuelState duel = room.activeDuel;
    TieBreakerAnswerAutofill.autofillTiebreaker(
        duel, runtimeConfigService.get().getTiebreakDurationMs());
    AnswerMetric a = duel.tiebreakerAnswers.get(duel.attackerUid);
    AnswerMetric d = duel.tiebreakerAnswers.get(duel.defenderUid);
    boolean neutralDef = "neutral".equals(duel.defenderUid);
    if (d == null && neutralDef) {
      finishBattle(server, room, true, true, false, true, duel);
      return;
    }
    boolean attackerWins = NumericTiebreakEvaluator.attackerWinsClosest(duel, a, d, neutralDef);
    finishBattle(server, room, attackerWins, true, true, true, duel);
  }

  public void finishBattle(
      SocketIOServer server,
      RoomState room,
      boolean attackerWins,
      boolean attackerCorrect,
      boolean defenderCorrect,
      boolean tieBreakerMinigame,
      DuelState duel) {
    if (duel == null) return;
    room.mcqSpeedTieRetries = 0;
    room.tieBreakOverride = null;
    if (attackerWins) applyAttackerCapture(room, duel);
    room.phase = PHASE_BATTLE;
    room.activeDuel = null;
    advanceTurnSkipEliminated(room);
    room.roundAttackCount++;
    if (room.roundAttackCount >= room.players.size()) {
      room.roundAttackCount = 0;
      room.round++;
    }

    HashMap<String, Object> result = new HashMap<>();
    result.put("tieBreakerMinigame", tieBreakerMinigame);
    result.put("attackerWins", attackerWins);
    result.put("winnerUid", attackerWins ? duel.attackerUid : duel.defenderUid);
    result.put("attackerUid", duel.attackerUid);
    result.put("defenderUid", duel.defenderUid);
    result.put("attackerCorrect", attackerCorrect);
    result.put("defenderCorrect", defenderCorrect);
    result.put("wonBySpeed", attackerCorrect && defenderCorrect);
    result.put("correctIndex", duel.mcqQuestion != null ? duel.mcqQuestion.correctIndex : null);
    result.put("targetHexId", duel.targetRegionId);

    HashMap<String, Object> payload = new HashMap<>();
    payload.put("room", snapshotFactory.roomToClient(room));
    payload.put("result", result);
    server.getRoomOperations(room.id).sendEvent("duel_resolved", payload);
    log.debug("finishBattle emitted duel_resolved room={}", room.id);
    {
      String kind = "duel_mcq";
      if (tieBreakerMinigame) {
        String tk = duel.tiebreakKind;
        kind = (tk == null || tk.isEmpty()) ? "tiebreak" : "tiebreak_" + tk;
      }
      String w =
          attackerWins
              ? duel.attackerUid
              : ("neutral".equals(duel.defenderUid) ? null : duel.defenderUid);
      roundLastSubmitEmitter.emit(server, room, kind, w, false, "duel_resolved", null);
    }
    broadcaster.emitRoomUpdate(room);
    snapshotCoordinator.snapshotDurable(room);
    matchOutcome.evaluateEndConditions(server, room);
  }

  public void applyAttackerCapture(RoomState room, DuelState duel) {
    PlayerState attacker = room.playersByUid.get(duel.attackerUid);
    PlayerState defender = room.playersByUid.get(duel.defenderUid);
    RegionState hex = room.regions.get(duel.targetRegionId);
    if (attacker == null || hex == null) return;
    if (hex.isCastle && defender != null) {
      defender.castleHp = defender.castleHp - 1;
      if (defender.castleHp <= 0) {
        defender.isEliminated = true;
        defender.eliminatedAt = System.currentTimeMillis();
        for (RegionState h : room.regions.values()) {
          if (defender.uid.equals(h.ownerUid)) {
            h.ownerUid = attacker.uid;
            attacker.score += snapshotFactory.pointValue(room, h.id);
          }
        }
        room.scoreByUid.put(attacker.uid, attacker.score);
      }
      return;
    }
    hex.ownerUid = attacker.uid;
    hex.type = "player";
  }

  public void advanceTurnSkipEliminated(RoomState room) {
    int n = room.players.size();
    for (int i = 0; i < n; i++) {
      room.currentTurnIndex = (room.currentTurnIndex + 1) % n;
      if (!room.players.get(room.currentTurnIndex).isEliminated) return;
    }
  }

  public void autoFillDuel(DuelState duel, int duelDurationMs) {
    if (!duel.answers.containsKey(duel.attackerUid)) {
      DuelAnswer a = new DuelAnswer();
      a.answerIndex = -1;
      a.timeTaken = duelDurationMs;
      duel.answers.put(duel.attackerUid, a);
    }
    if (!"neutral".equals(duel.defenderUid) && !duel.answers.containsKey(duel.defenderUid)) {
      DuelAnswer d = new DuelAnswer();
      d.answerIndex = -1;
      d.timeTaken = duelDurationMs;
      duel.answers.put(duel.defenderUid, d);
    }
  }

  public TieBreakerRealtimeBridge tieBreakerBridge(SocketIOServer server, RoomState room) {
    return new TieBreakerRealtimeBridge() {
      @Override
      public String roomId() {
        return room.id;
      }

      @Override
      public void emitToRoom(String eventName, Map<String, Object> payload) {
        server.getRoomOperations(room.id).sendEvent(eventName, payload);
      }

      @Override
      public void scheduleRoomTimer(String timerKey, long delayMs, Runnable task) {
        roomTimers.scheduleTimer(
            room,
            timerKey,
            delayMs,
            () -> {
              if (!executors.submitToRoom(room.id, task)) {
                log.warn(
                    "sok tieBreak timer submit rejected room={} timerKey={}", room.id, timerKey);
              }
            });
      }

      @Override
      public void cancelRoomTimer(String timerKey) {
        roomTimers.cancelTimer(room, timerKey);
      }

      @Override
      public GameRuntimeConfig configuration() {
        return runtimeConfigService.get();
      }

      @Override
      public QuestionEngineService questionEngine() {
        return questionEngineService;
      }
    };
  }
}
