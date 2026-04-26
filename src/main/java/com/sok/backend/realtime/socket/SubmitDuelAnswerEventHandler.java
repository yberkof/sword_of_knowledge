package com.sok.backend.realtime.socket;

import com.corundumstudio.socketio.SocketIOServer;
import com.sok.backend.realtime.match.BattleOrchestrator;
import com.sok.backend.realtime.match.DuelAnswer;
import com.sok.backend.realtime.match.DuelState;
import com.sok.backend.realtime.match.RoomState;
import com.sok.backend.realtime.room.RoomBroadcaster;
import com.sok.backend.service.config.RuntimeGameConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Handles {@code submit_answer} for the MCQ duel (not tie-break; use {@code submit_estimation}). */
@Component
public class SubmitDuelAnswerEventHandler {
  private static final Logger log = LoggerFactory.getLogger(SubmitDuelAnswerEventHandler.class);

  private final BattleOrchestrator battle;
  private final RoomBroadcaster broadcaster;
  private final RuntimeGameConfigService runtimeConfigService;

  public SubmitDuelAnswerEventHandler(
      BattleOrchestrator battle, RoomBroadcaster broadcaster, RuntimeGameConfigService runtimeConfigService) {
    this.battle = battle;
    this.broadcaster = broadcaster;
    this.runtimeConfigService = runtimeConfigService;
  }

  public void onSubmit(String roomId, String uid, int finalAnswer, SocketIOServer server, RoomState room) {
    if (room == null) {
      log.warn("sok submit_answer ignored: room missing roomId={}", roomId);
      return;
    }
    if (room.activeDuel == null || GamePhases.BATTLE_TIEBREAKER.equals(room.phase)) {
      log.warn(
          "sok submit_answer ignored: MCQ only in duel phase (use submit_estimation in tiebreaker) roomId={} phase={} hasDuel={}",
          roomId,
          room.phase,
          room.activeDuel != null);
      return;
    }
    DuelState duel = room.activeDuel;
    boolean participant = uid.equals(duel.attackerUid) || uid.equals(duel.defenderUid);
    if (!participant || duel.answers.containsKey(uid)) {
      log.warn(
          "sok submit_answer ignored: not participant or duplicate roomId={} uid={} participant={} duplicate={}",
          roomId,
          uid,
          participant,
          duel.answers.containsKey(uid));
      return;
    }
    DuelAnswer ans = new DuelAnswer();
    ans.answerIndex = finalAnswer;
    ans.timeTaken =
        Math.min(
            runtimeConfigService.get().getDuelDurationMs(),
            System.currentTimeMillis() - room.phaseStartedAt);
    duel.answers.put(uid, ans);
    log.info(
        "sok submit_answer accepted roomId={} uid={} idx={} duelAnswerCount={}",
        roomId,
        uid,
        finalAnswer,
        duel.answers.size());
    boolean done = duel.answers.containsKey(duel.attackerUid);
    if (!"neutral".equals(duel.defenderUid)) {
      done = done && duel.answers.containsKey(duel.defenderUid);
    }
    if (done) {
      battle.resolveDuel(server, room);
    } else {
      broadcaster.emitRoomUpdate(room);
    }
  }
}
