package com.sok.backend.realtime.socket;

import com.corundumstudio.socketio.SocketIOServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.sok.backend.realtime.binding.SocketEventBinder;
import com.sok.backend.realtime.room.RoomSerialCommandService;
import org.springframework.stereotype.Component;

/**
 * Binds room-level events like castle placement and rematch voting.
 */
@Component
public class RoomSocketEventBinder implements SocketEventBinder {

  private final RoomSerialCommandService roomCommands;
  private final PlaceCastleEventHandler placeCastle;
  private final RematchEventHandler rematchHandler;

  public RoomSocketEventBinder(
      RoomSerialCommandService roomCommands,
      PlaceCastleEventHandler placeCastle,
      RematchEventHandler rematchHandler) {
    this.roomCommands = roomCommands;
    this.placeCastle = placeCastle;
    this.rematchHandler = rematchHandler;
  }

  @Override
  public void bind(SocketIOServer server) {
    server.addEventListener(
        "vote_rematch",
        JsonNode.class,
        (client, payload, ack) -> {
          SocketEventPayloads.validateUidOrDisconnect(client, payload);
          String roomId = SocketEventPayloads.asString(payload, "roomId");
          String uid = SocketEventPayloads.asString(payload, "uid");
          roomCommands.runWithServer(
              roomId,
              server,
              (s, room) -> rematchHandler.onVoteRematch(roomId, uid, room));
        });

    server.addEventListener(
        "place_castle",
        JsonNode.class,
        (client, payload, ack) -> {
          SocketEventPayloads.validateUidOrDisconnect(client, payload);
          String roomId = SocketEventPayloads.asString(payload, "roomId");
          String uid = SocketEventPayloads.asString(payload, "uid");
          int regionId = payload.path("regionId").asInt(-1);
          roomCommands.runWithServer(
              roomId,
              server,
              (s, room) -> placeCastle.onPlace(roomId, uid, regionId, s, room));
        });
  }
}
