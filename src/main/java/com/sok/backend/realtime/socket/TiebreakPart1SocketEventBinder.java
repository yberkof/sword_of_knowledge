package com.sok.backend.realtime.socket;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.sok.backend.realtime.binding.SocketEventBinder;
import com.sok.backend.realtime.room.RoomSerialCommandService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Inbound: {@code tiebreaker_xo_move}, avoid-bombs place/open. */
@Component
public class TiebreakPart1SocketEventBinder implements SocketEventBinder {

  private final RoomSerialCommandService roomCommands;
  private final TiebreakXoEventHandler xo;
  private final AvoidBombsTiebreakEventHandler avoid;

  public TiebreakPart1SocketEventBinder(
      RoomSerialCommandService roomCommands,
      TiebreakXoEventHandler xo,
      AvoidBombsTiebreakEventHandler avoid) {
    this.roomCommands = roomCommands;
    this.xo = xo;
    this.avoid = avoid;
  }

  @Override
  public void bind(SocketIOServer server) {
    server.addEventListener(
        "tiebreaker_xo_move",
        JsonNode.class,
        (client, payload, ack) -> {
          SocketEventPayloads.validateUidOrDisconnect(client, payload);
          String roomId = SocketEventPayloads.asString(payload, "roomId");
          String uid = SocketEventPayloads.asString(payload, "uid");
          int cellIndex = payload.path("cellIndex").asInt(-1);
          roomCommands.runWithServer(
              roomId,
              server,
              (s, room) -> xo.onMove(client, s, roomId, uid, cellIndex, room));
        });
    server.addEventListener(
        "tiebreaker_avoid_bombs_place",
        JsonNode.class,
        (client, payload, ack) -> {
          SocketEventPayloads.validateUidOrDisconnect(client, payload);
          String roomId = SocketEventPayloads.asString(payload, "roomId");
          String uid = SocketEventPayloads.asString(payload, "uid");
          List<Integer> cells = new ArrayList<Integer>();
          JsonNode cellsNode = payload.path("cells");
          if (cellsNode.isArray()) {
            for (JsonNode c : cellsNode) {
              cells.add(c.asInt(-1));
            }
          }
          roomCommands.runWithServer(
              roomId,
              server,
              (s, room) -> avoid.onPlaceBombs(client, s, roomId, uid, cells, room));
        });
    server.addEventListener(
        "tiebreaker_avoid_bombs_open",
        JsonNode.class,
        (client, payload, ack) -> {
          SocketEventPayloads.validateUidOrDisconnect(client, payload);
          String roomId = SocketEventPayloads.asString(payload, "roomId");
          String uid = SocketEventPayloads.asString(payload, "uid");
          int cellIndex = payload.path("cellIndex").asInt(-1);
          roomCommands.runWithServer(
              roomId,
              server,
              (s, room) -> avoid.onOpen(client, s, roomId, uid, cellIndex, room));
        });
  }
}
