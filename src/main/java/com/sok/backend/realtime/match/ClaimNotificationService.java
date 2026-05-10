package com.sok.backend.realtime.match;

import com.corundumstudio.socketio.SocketIOServer;
import com.sok.backend.domain.game.QuestionEngineService;
import com.sok.backend.realtime.room.RoomBroadcaster;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Handles socket emissions for the claim phase.
 */
@Component
public class ClaimNotificationService {

  private final RoomBroadcaster broadcaster;
  private final QuestionEngineService questionEngineService;

  public ClaimNotificationService(
      RoomBroadcaster broadcaster,
      QuestionEngineService questionEngineService) {
    this.broadcaster = broadcaster;
    this.questionEngineService = questionEngineService;
  }

  public void notifyEstimationQuestion(SocketIOServer server, RoomState room, int durationMs) {
    server
        .getRoomOperations(room.id)
        .sendEvent(
            "estimation_question",
            questionEngineService.toClient(
                room.activeNumericQuestion, room.phaseStartedAt, durationMs));
    broadcaster.emitRoomUpdate(room);
  }

  public void notifyClaimRankings(
      SocketIOServer server, 
      RoomState room, 
      List<Map<String, Object>> rankedPayload) {
    
    HashMap<String, Object> payload = new HashMap<>();
    payload.put("rankings", rankedPayload);
    payload.put("claimPicks", room.claimPicksLeftByUid);
    payload.put("correctAnswer", room.activeNumericQuestion.answer);
    payload.put("questionId", room.activeNumericQuestion.id);
    
    server.getRoomOperations(room.id).sendEvent("claim_rankings", payload);
    broadcaster.emitRoomUpdate(room);
  }
}
