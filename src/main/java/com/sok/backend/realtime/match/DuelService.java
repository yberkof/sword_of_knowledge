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
import com.sok.backend.realtime.room.RoomExecutorRegistry;
import com.sok.backend.realtime.room.RoomTimerScheduler;
import com.sok.backend.service.config.GameRuntimeConfig;
import com.sok.backend.service.config.RuntimeGameConfigService;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles duel and tiebreaker domain logic.
 */
@Component
public class DuelService {
  private static final Logger log = LoggerFactory.getLogger(DuelService.class);

  private final QuestionEngineService questionEngineService;
  private final BattlePhaseService battlePhaseService;
  private final McqSpeedTieResolutionService mcqSpeedTieResolutionService;
  private final TieBreakerAttackPhaseComposer tieBreakerAttackPhaseComposer;
  private final RuntimeGameConfigService runtimeConfigService;
  private final RoomTimerScheduler roomTimers;
  private final RoomExecutorRegistry executors;

  public DuelService(
      QuestionEngineService questionEngineService,
      BattlePhaseService battlePhaseService,
      McqSpeedTieResolutionService mcqSpeedTieResolutionService,
      TieBreakerAttackPhaseComposer tieBreakerAttackPhaseComposer,
      RuntimeGameConfigService runtimeConfigService,
      RoomTimerScheduler roomTimers,
      RoomExecutorRegistry executors) {
    this.questionEngineService = questionEngineService;
    this.battlePhaseService = battlePhaseService;
    this.mcqSpeedTieResolutionService = mcqSpeedTieResolutionService;
    this.tieBreakerAttackPhaseComposer = tieBreakerAttackPhaseComposer;
    this.runtimeConfigService = runtimeConfigService;
    this.roomTimers = roomTimers;
    this.executors = executors;
  }

  public DuelState createDuel(RoomState room, String attackerUid, int targetRegionId) {
    RegionState target = room.regions.get(targetRegionId);
    if (target == null) return null;
    DuelState duel = new DuelState();
    duel.attackerUid = attackerUid;
    duel.defenderUid = target.ownerUid == null ? "neutral" : target.ownerUid;
    duel.targetRegionId = targetRegionId;
    return duel;
  }

  public void autoFillDuel(DuelState duel) {
    int duelDurationMs = runtimeConfigService.get().getDuelDurationMs();
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

  public DuelOutcome resolveMcqDuel(RoomState room, DuelState duel) {
    if (duel == null || duel.mcqQuestion == null) return null;
    DuelAnswer attacker = duel.answers.get(duel.attackerUid);
    DuelAnswer defender =
        "neutral".equals(duel.defenderUid) ? null : duel.answers.get(duel.defenderUid);
    boolean attackerCorrect = attacker != null && attacker.answerIndex == duel.mcqQuestion.correctIndex;
    boolean defenderCorrect =
        !"neutral".equals(duel.defenderUid)
            && defender != null
            && defender.answerIndex == duel.mcqQuestion.correctIndex;

    GameRuntimeConfig cfg = runtimeConfigService.get();
    if (!"neutral".equals(duel.defenderUid) && attackerCorrect && defenderCorrect) {
      McqSpeedTieOutcome outcome =
          mcqSpeedTieResolutionService.resolve(cfg, room.mcqSpeedTieRetries);
      return new DuelOutcome(outcome, attackerCorrect, defenderCorrect);
    }

    long a = attacker == null ? cfg.getDuelDurationMs() : attacker.timeTaken;
    long d = defender == null ? cfg.getDuelDurationMs() : defender.timeTaken;
    BattlePhaseService.DuelOutcome outcome =
        battlePhaseService.resolveMcq(
            attackerCorrect, defenderCorrect, a, d, !"neutral".equals(duel.defenderUid));
    boolean attackerWins = outcome == BattlePhaseService.DuelOutcome.ATTACKER_WINS;
    return new DuelOutcome(attackerWins, attackerCorrect, defenderCorrect, false);
  }

  public DuelOutcome resolveNumericTiebreaker(RoomState room, DuelState duel) {
    if (duel == null || duel.numericQuestion == null) return null;
    TieBreakerAnswerAutofill.autofillTiebreaker(
        duel, runtimeConfigService.get().getTiebreakDurationMs());
    AnswerMetric a = duel.tiebreakerAnswers.get(duel.attackerUid);
    AnswerMetric d = duel.tiebreakerAnswers.get(duel.defenderUid);
    boolean neutralDef = "neutral".equals(duel.defenderUid);
    if (d == null && neutralDef) {
      return new DuelOutcome(true, true, false, true);
    }
    boolean attackerWins = NumericTiebreakEvaluator.attackerWinsClosest(duel, a, d, neutralDef);
    return new DuelOutcome(attackerWins, true, true, true);
  }

  public void startTiebreakerStrategy(
      SocketIOServer server, 
      RoomState room, 
      DuelState duel, 
      Runnable onNumericTiebreakDeadline) {
    GameRuntimeConfig cfg = runtimeConfigService.get();
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
    TieBreakerAttackPhaseStrategy strat = tieBreakerAttackPhaseComposer.resolve(effective, duel.defenderUid);
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
            return onNumericTiebreakDeadline;
          }
        });
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

  public static class DuelOutcome {
    public final boolean attackerWins;
    public final boolean attackerCorrect;
    public final boolean defenderCorrect;
    public final boolean tieBreakerMinigame;
    public final McqSpeedTieOutcome mcqTieOutcome;

    public DuelOutcome(boolean attackerWins, boolean attackerCorrect, boolean defenderCorrect, boolean tieBreakerMinigame) {
      this.attackerWins = attackerWins;
      this.attackerCorrect = attackerCorrect;
      this.defenderCorrect = defenderCorrect;
      this.tieBreakerMinigame = tieBreakerMinigame;
      this.mcqTieOutcome = null;
    }

    public DuelOutcome(McqSpeedTieOutcome mcqTieOutcome, boolean attackerCorrect, boolean defenderCorrect) {
      this.attackerWins = false;
      this.attackerCorrect = attackerCorrect;
      this.defenderCorrect = defenderCorrect;
      this.tieBreakerMinigame = false;
      this.mcqTieOutcome = mcqTieOutcome;
    }
  }
}
