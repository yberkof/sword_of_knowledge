package com.sok.backend.realtime.match;

import com.corundumstudio.socketio.SocketIOServer;
import com.sok.backend.domain.game.ClaimingPhaseService;
import com.sok.backend.domain.game.QuestionEngineService;
import com.sok.backend.realtime.persistence.RoomSnapshotCoordinator;
import com.sok.backend.realtime.room.RoomBroadcaster;
import com.sok.backend.realtime.room.RoomTimerScheduler;
import com.sok.backend.service.config.GameRuntimeConfig;
import com.sok.backend.service.config.RuntimeGameConfigService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Handles the claim (estimation + pick) phase lifecycle. */
@Component
public class ClaimPhaseOrchestrator {
  private static final Logger log = LoggerFactory.getLogger(ClaimPhaseOrchestrator.class);
  private static final String PHASE_CLAIM_Q = "claiming_question";
  private static final String PHASE_CLAIM_PICK = "claiming_pick";
  private static final String TIMER_CLAIM_Q = "claim_question_timeout";

  private final QuestionEngineService questionEngineService;
  private final ClaimingPhaseService claimingPhaseService;
  private final RuntimeGameConfigService runtimeConfigService;
  private final RoomBroadcaster broadcaster;
  private final RoomTimerScheduler roomTimers;
  private final RoomSnapshotCoordinator snapshotCoordinator;

  public ClaimPhaseOrchestrator(
      QuestionEngineService questionEngineService,
      ClaimingPhaseService claimingPhaseService,
      RuntimeGameConfigService runtimeConfigService,
      RoomBroadcaster broadcaster,
      RoomTimerScheduler roomTimers,
      RoomSnapshotCoordinator snapshotCoordinator) {
    this.questionEngineService = questionEngineService;
    this.claimingPhaseService = claimingPhaseService;
    this.runtimeConfigService = runtimeConfigService;
    this.broadcaster = broadcaster;
    this.roomTimers = roomTimers;
    this.snapshotCoordinator = snapshotCoordinator;
  }

  public void startClaimingQuestionRound(SocketIOServer server, RoomState room) {
    room.phase = PHASE_CLAIM_Q;
    room.phaseStartedAt = System.currentTimeMillis();
    room.claimPicksLeftByUid.clear();
    room.claimQueue.clear();
    room.estimationAnswers.clear();
    room.activeNumericQuestion = questionEngineService.nextNumericQuestion();
    GameRuntimeConfig cfg = runtimeConfigService.get();
    server
        .getRoomOperations(room.id)
        .sendEvent(
            "estimation_question",
            questionEngineService.toClient(
                room.activeNumericQuestion, room.phaseStartedAt, cfg.getClaimDurationMs()));
    roomTimers.scheduleTimer(
        room,
        TIMER_CLAIM_Q,
        cfg.getClaimDurationMs() + 50L,
        () -> resolveEstimationRound(server, room));
    broadcaster.emitRoomUpdate(room);
    snapshotCoordinator.snapshotDurable(room);
  }

  public void resolveEstimationRound(SocketIOServer server, RoomState room) {
    roomTimers.cancelTimer(room, TIMER_CLAIM_Q);
    if (room.activeNumericQuestion == null) {
      log.warn("sok resolveEstimationRound skipped: no activeNumericQuestion roomId={}", room.id);
      return;
    }
    log.info(
        "sok resolveEstimationRound roomId={} phase={} answers={}",
        room.id,
        room.phase,
        room.estimationAnswers.size());
    if (room.estimationAnswers.isEmpty()) {
      for (PlayerState p : room.players) {
        if (!p.isEliminated && p.online) {
          AnswerMetric m = new AnswerMetric();
          m.uid = p.uid;
          m.value = 0;
          m.latencyMs = runtimeConfigService.get().getClaimDurationMs();
          room.estimationAnswers.put(p.uid, m);
        }
      }
    }
    List<ClaimingPhaseService.Metric> rows = new ArrayList<>();
    for (AnswerMetric metric : room.estimationAnswers.values()) {
      ClaimingPhaseService.Metric m = new ClaimingPhaseService.Metric();
      m.uid = metric.uid;
      m.value = metric.value;
      m.latencyMs = metric.latencyMs;
      rows.add(m);
    }
    List<ClaimingPhaseService.Metric> rankedRows =
        claimingPhaseService.rankByDeltaThenLatency(rows, room.activeNumericQuestion.answer);
    List<AnswerMetric> ranked = new ArrayList<>();
    for (ClaimingPhaseService.Metric m : rankedRows) {
      AnswerMetric out = new AnswerMetric();
      out.uid = m.uid;
      out.value = m.value;
      out.latencyMs = m.latencyMs;
      ranked.add(out);
    }
    GameRuntimeConfig cfg = runtimeConfigService.get();
    room.claimPicksLeftByUid.clear();
    room.claimPicksLeftByUid.putAll(
        claimingPhaseService.assignClaimPicks(
            rankedRows, cfg.getClaimFirstPicks(), cfg.getClaimSecondPicks()));
    room.claimQueue.clear();
    for (AnswerMetric m : ranked) {
      Integer left = room.claimPicksLeftByUid.get(m.uid);
      if (left != null && left > 0) room.claimQueue.add(m.uid);
    }
    room.claimTurnUid = room.claimQueue.isEmpty() ? null : room.claimQueue.get(0);
    room.phase = PHASE_CLAIM_PICK;
    room.phaseStartedAt = System.currentTimeMillis();
    HashMap<String, Object> rankings = new HashMap<>();
    rankings.put("rankings", rankedToPayload(ranked, room.activeNumericQuestion.answer));
    rankings.put("claimPicks", room.claimPicksLeftByUid);
    server.getRoomOperations(room.id).sendEvent("claim_rankings", rankings);
    broadcaster.emitRoomUpdate(room);
    snapshotCoordinator.snapshotDurable(room);
  }

  public List<Map<String, Object>> rankedToPayload(List<AnswerMetric> ranked, int correctAnswer) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (int i = 0; i < ranked.size(); i++) {
      AnswerMetric m = ranked.get(i);
      HashMap<String, Object> row = new HashMap<>();
      row.put("uid", m.uid);
      row.put("rank", i + 1);
      row.put("delta", Math.abs(m.value - correctAnswer));
      row.put("latencyMs", m.latencyMs);
      out.add(row);
    }
    return out;
  }

  public void rotateClaimTurn(RoomState room) {
    if (room.claimQueue.isEmpty()) {
      room.claimTurnUid = null;
      return;
    }
    String current = room.claimQueue.remove(0);
    Integer remain = room.claimPicksLeftByUid.get(current);
    if (remain != null && remain > 0) {
      room.claimQueue.add(current);
    }
    room.claimTurnUid = room.claimQueue.isEmpty() ? null : room.claimQueue.get(0);
  }

  public boolean claimsQueueEmpty(RoomState room) {
    return room.claimTurnUid == null || room.claimQueue.isEmpty();
  }

  public boolean allRegionsClaimed(RoomState room) {
    for (RegionState r : room.regions.values()) {
      if (r.ownerUid == null) return false;
    }
    return true;
  }

  public int countNeutralRegions(RoomState room) {
    int n = 0;
    for (RegionState r : room.regions.values()) {
      if (r.ownerUid == null) n++;
    }
    return n;
  }
}
