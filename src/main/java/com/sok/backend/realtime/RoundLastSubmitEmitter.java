package com.sok.backend.realtime;

import com.corundumstudio.socketio.SocketIOServer;
import com.sok.backend.realtime.match.RoomState;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Emits {@value #EVENT_NAME} for question rounds and tie-break turn resolutions so the client
 * can show a winner/actor highlight before global room snapshots land.
 */
@Component
public class RoundLastSubmitEmitter {

  public static final String EVENT_NAME = "round_last_submit_resolved";

  public void emit(
      SocketIOServer server,
      RoomState room,
      String kind,
      String roundWinnerUid,
      boolean isTie,
      String reason,
      String lastActorUid) {
    if (room == null) return;
    Map<String, Object> payload = new HashMap<String, Object>();
    payload.put("roomId", room.id);
    payload.put("phase", room.phase);
    payload.put("round", room.round);
    payload.put("kind", kind);
    payload.put("winnerUid", roundWinnerUid);
    payload.put("isTie", isTie);
    payload.put("reason", reason);
    if (lastActorUid != null) {
      payload.put("lastActorUid", lastActorUid);
    }
    server.getRoomOperations(room.id).sendEvent(EVENT_NAME, payload);
  }
}
