package com.sok.backend.realtime.binding;

import com.corundumstudio.socketio.SocketIOServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.sok.backend.domain.game.GameInputRules;
import com.sok.backend.realtime.room.RoomSerialCommandService;
import java.util.HashMap;
import org.springframework.stereotype.Component;

/** Handles the {@code room_chat} Socket.IO event. */
@Component
public class ChatEventBinder implements SocketEventBinder {
  private final GameInputRules gameInputRules;
  private final RoomSerialCommandService roomCommands;

  public ChatEventBinder(GameInputRules gameInputRules, RoomSerialCommandService roomCommands) {
    this.gameInputRules = gameInputRules;
    this.roomCommands = roomCommands;
  }

  @Override
  public void bind(SocketIOServer server) {
    server.addEventListener(
        "room_chat",
        JsonNode.class,
        (client, payload, ack) -> {
          String roomId = payload.path("roomId").asText("");
          String uid = payload.path("uid").asText("");
          String name = payload.path("name").asText("");
          String message = gameInputRules.sanitizeChatMessage(payload.path("message").asText(""));
          if (message.trim().isEmpty()) return;
          roomCommands.runWithServer(
              roomId,
              server,
              (s, room) -> {
                if (!room.playersByUid.containsKey(uid)) {
                  return;
                }
                HashMap<String, Object> out = new HashMap<>();
                out.put("uid", uid);
                out.put("name", name.trim().isEmpty() ? "Player" : name);
                out.put("message", message);
                out.put("ts", System.currentTimeMillis());
                s.getRoomOperations(room.id).sendEvent("room_chat", out);
              });
        });
  }
}
