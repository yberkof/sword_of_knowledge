package com.sok.backend.realtime.socket;

import com.corundumstudio.socketio.SocketIOServer;
import com.sok.backend.realtime.match.AnswerMetric;
import com.sok.backend.realtime.match.BattleOrchestrator;
import com.sok.backend.realtime.match.ClaimPhaseOrchestrator;
import com.sok.backend.realtime.match.DuelState;
import com.sok.backend.realtime.match.RoomState;
import com.sok.backend.realtime.room.RoomBroadcaster;
import com.sok.backend.realtime.room.RoomClientSnapshotFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Handles {@code submit_estimation} in claiming and numeric tie-break. */
@Component
public class SubmitEstimationEventHandler {
  private static final Logger log = LoggerFactory.getLogger(SubmitEstimationEventHandler.class);

  private final BattleOrchestrator battle;
  private final ClaimPhaseOrchestrator claimPhase;
  private final RoomClientSnapshotFactory snapshotFactory;
  private final RoomBroadcaster broadcaster;

  public SubmitEstimationEventHandler(
      BattleOrchestrator battle,
      ClaimPhaseOrchestrator claimPhase,
      RoomClientSnapshotFactory snapshotFactory,
      RoomBroadcaster broadcaster) {
    this.battle = battle;
    this.claimPhase = claimPhase;
    this.snapshotFactory = snapshotFactory;
    this.broadcaster = broadcaster;
  }

  public void onSubmit(String roomId, String uid, int value, SocketIOServer server, RoomState room) {
    log.info("sok evt submit_estimation roomId={} uid={} value={}", roomId, uid, value);
    if (room == null) {
      log.warn("sok submit_estimation ignored: room missing roomId={}", roomId);
      return;
    }
    if (!GamePhases.CLAIMING_QUESTION.equals(room.phase)
        && !GamePhases.BATTLE_TIEBREAKER.equals(room.phase)) {
      log.warn(
          "sok submit_estimation ignored: wrong phase roomId={} phase={} (want claiming_question or battle_tiebreaker)",
          roomId,
          room.phase);
      return;
    }
    if (value == Integer.MIN_VALUE) {
      log.warn("sok submit_estimation ignored: value missing roomId={} uid={}", roomId, uid);
      return;
    }
    if (!room.playersByUid.containsKey(uid)) {
      log.warn("sok submit_estimation ignored: uid not in room roomId={} uid={}", roomId, uid);
      return;
    }
    if (GamePhases.BATTLE_TIEBREAKER.equals(room.phase)) {
      onTiebreakNumeric(server, room, roomId, uid, value);
      return;
    }
    onClaimingQuestion(server, room, roomId, uid, value);
  }

  private void onTiebreakNumeric(
      SocketIOServer server, RoomState room, String roomId, String uid, int value) {
    DuelState duel = room.activeDuel;
    if (duel == null || duel.numericQuestion == null || !"numeric".equals(duel.tiebreakKind)) {
      log.warn("sok submit_estimation ignored: tiebreaker is not numeric estimation roomId={}", roomId);
      return;
    }
    if (!uid.equals(duel.attackerUid) && !uid.equals(duel.defenderUid)) {
      log.warn("sok submit_estimation ignored: not duel participant roomId={} uid={}", roomId, uid);
      return;
    }
    if (duel.tiebreakerAnswers.containsKey(uid)) {
      log.warn("sok submit_estimation ignored: duplicate tiebreak submit roomId={} uid={}", roomId, uid);
      return;
    }
    AnswerMetric m = new AnswerMetric();
    m.uid = uid;
    m.value = value;
    m.latencyMs = System.currentTimeMillis() - room.phaseStartedAt;
    duel.tiebreakerAnswers.put(uid, m);
    boolean defenderHuman = !"neutral".equals(duel.defenderUid);
    boolean both =
        duel.tiebreakerAnswers.containsKey(duel.attackerUid)
            && (!defenderHuman || duel.tiebreakerAnswers.containsKey(duel.defenderUid));
    if (both) {
      battle.resolveTiebreaker(server, room);
    } else {
      broadcaster.emitRoomUpdate(room);
    }
  }

  private void onClaimingQuestion(
      SocketIOServer server, RoomState room, String roomId, String uid, int value) {
    if (room.estimationAnswers.containsKey(uid)) {
      log.warn("sok submit_estimation ignored: duplicate submit roomId={} uid={}", roomId, uid);
      return;
    }
    AnswerMetric m = new AnswerMetric();
    m.uid = uid;
    m.value = value;
    m.latencyMs = System.currentTimeMillis() - room.phaseStartedAt;
    room.estimationAnswers.put(uid, m);
    log.info(
        "sok submit_estimation accepted roomId={} uid={} answers={}/{}",
        roomId,
        uid,
        room.estimationAnswers.size(),
        snapshotFactory.onlinePlayerCount(room));
    if (room.estimationAnswers.size() == snapshotFactory.onlinePlayerCount(room)) {
      claimPhase.resolveEstimationRound(server, room);
    } else {
      broadcaster.emitRoomUpdate(room);
    }
  }
}
