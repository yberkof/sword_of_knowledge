package com.sok.backend.realtime.match;

import com.corundumstudio.socketio.SocketIOServer;
import com.sok.backend.domain.game.QuestionEngineService;
import com.sok.backend.realtime.RoundLastSubmitEmitter;
import com.sok.backend.realtime.persistence.RoomSnapshotCoordinator;
import com.sok.backend.realtime.room.RoomTimerScheduler;
import com.sok.backend.service.config.GameRuntimeConfig;
import com.sok.backend.service.config.RuntimeGameConfigService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Orchestrates the claim (estimation + pick) phase lifecycle.
 * Coordinates between domain logic, state, and notifications.
 */
@Component
public class ClaimPhaseOrchestrator {
  private static final Logger log = LoggerFactory.getLogger(ClaimPhaseOrchestrator.class);
  private static final String PHASE_CLAIM_Q = "claiming_question";
  private static final String PHASE_CLAIM_PICK = "claiming_pick";
  private static final String TIMER_CLAIM_Q = "claim_question_timeout";

  private final QuestionEngineService questionEngineService;
  private final RuntimeGameConfigService runtimeConfigService;
  private final RoomTimerScheduler roomTimers;
  private final RoomSnapshotCoordinator snapshotCoordinator;
  private final RoundLastSubmitEmitter roundLastSubmitEmitter;

  private final ClaimNotificationService notificationService;
  private final ClaimStateService stateService;
  private final ClaimDomainService domainService;

  public ClaimPhaseOrchestrator(
      QuestionEngineService questionEngineService,
      RuntimeGameConfigService runtimeConfigService,
      RoomTimerScheduler roomTimers,
      RoomSnapshotCoordinator snapshotCoordinator,
      RoundLastSubmitEmitter roundLastSubmitEmitter,
      ClaimNotificationService notificationService,
      ClaimStateService stateService,
      ClaimDomainService domainService) {
    this.questionEngineService = questionEngineService;
    this.runtimeConfigService = runtimeConfigService;
    this.roomTimers = roomTimers;
    this.snapshotCoordinator = snapshotCoordinator;
    this.roundLastSubmitEmitter = roundLastSubmitEmitter;
    this.notificationService = notificationService;
    this.stateService = stateService;
    this.domainService = domainService;
  }

  public void startClaimingQuestionRound(SocketIOServer server, RoomState room) {
    room.phase = PHASE_CLAIM_Q;
    room.phaseStartedAt = System.currentTimeMillis();
    stateService.resetClaimState(room);

    GameRuntimeConfig cfg = runtimeConfigService.get();
    List<String> uids = room.players.stream()
        .map(p -> p.uid)
        .collect(Collectors.toList());
    
    room.activeNumericQuestion = questionEngineService.nextNumericQuestion(
        cfg.getDefaultQuestionCategory(), uids);

    notificationService.notifyEstimationQuestion(server, room, cfg.getClaimDurationMs());
    
    roomTimers.scheduleTimer(
        room,
        TIMER_CLAIM_Q,
        cfg.getClaimDurationMs() + 50L,
        () -> resolveEstimationRound(server, room));
    
    snapshotCoordinator.snapshotDurable(room);
  }

  public void resolveEstimationRound(SocketIOServer server, RoomState room) {
    roomTimers.cancelTimer(room, TIMER_CLAIM_Q);
    if (room.activeNumericQuestion == null) {
      log.warn("sok resolveEstimationRound skipped: no activeNumericQuestion roomId={}", room.id);
      return;
    }

    log.info("sok resolveEstimationRound roomId={} answers={}", room.id, room.estimationAnswers.size());

    List<AnswerMetric> ranked = domainService.rankEstimationAnswers(room);
    domainService.assignPicks(room, ranked);
    
    room.claimTurnUid = room.claimQueue.isEmpty() ? null : room.claimQueue.get(0);
    room.phase = PHASE_CLAIM_PICK;
    room.phaseStartedAt = System.currentTimeMillis();

    if (!ranked.isEmpty()) {
      roundLastSubmitEmitter.emit(
          server, room, "estimation", ranked.get(0).uid, false, "estimation_resolved", null);
    }

    List<Map<String, Object>> rankedPayload = domainService.buildRankedPayload(
        ranked, room.activeNumericQuestion.answer);
    notificationService.notifyClaimRankings(server, room, rankedPayload);
    
    snapshotCoordinator.snapshotDurable(room);
    recordQuestionSeen(room);
  }

  private void recordQuestionSeen(RoomState room) {
    if (room.activeNumericQuestion != null) {
      List<String> uids = room.players.stream()
          .map(p -> p.uid)
          .collect(Collectors.toList());
      questionEngineService.recordQuestionSeen(uids, room.activeNumericQuestion.id, "NUMERIC");
    }
  }

  public void rotateClaimTurn(RoomState room) {
    stateService.rotateClaimTurn(room);
  }

  public boolean claimsQueueEmpty(RoomState room) {
    return stateService.claimsQueueEmpty(room);
  }

  public boolean allRegionsClaimed(RoomState room) {
    return stateService.allRegionsClaimed(room);
  }

  public int countNeutralRegions(RoomState room) {
    return stateService.countNeutralRegions(room);
  }
}
