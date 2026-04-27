package com.sok.backend.realtime.socket;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.sok.backend.realtime.binding.SocketEventBinder;
import com.sok.backend.realtime.room.RoomSerialCommandService;
import java.util.ArrayList;
import org.springframework.stereotype.Component;

/** Inbound: collection pick, RPS, rhythm, memory tie-breakers. */
@Component
public class TiebreakPart2SocketEventBinder implements SocketEventBinder {

  private final RoomSerialCommandService roomCommands;
  private final TiebreakRestEventHandler rest;

  public TiebreakPart2SocketEventBinder(
      RoomSerialCommandService roomCommands, TiebreakRestEventHandler rest) {
    this.roomCommands = roomCommands;
    this.rest = rest;
  }

  @Override
  public void bind(SocketIOServer server) {
    server.addEventListener(
        "tiebreaker_collection_pick",
        JsonNode.class,
        (client, payload, ack) -> {
          SocketEventPayloads.validateUidOrDisconnect(client, payload);
          String roomId = SocketEventPayloads.asString(payload, "roomId");
          String uid = SocketEventPayloads.asString(payload, "uid");
          String choice = payload.path("choice").asText("").trim().toLowerCase();
          roomCommands.runWithServer(
              roomId, server, (s, room) -> rest.onCollectionPick(s, roomId, uid, choice, room));
        });
    server.addEventListener(
        "tiebreaker_rps_throw",
        JsonNode.class,
        (client, payload, ack) -> {
          SocketEventPayloads.validateUidOrDisconnect(client, payload);
          String roomId = SocketEventPayloads.asString(payload, "roomId");
          String uid = SocketEventPayloads.asString(payload, "uid");
          String hand = payload.path("hand").asText("");
          roomCommands.runWithServer(
              roomId, server, (s, room) -> rest.onRpsThrow(s, roomId, uid, hand, room));
        });
    server.addEventListener(
        "tiebreaker_rhythm_submit",
        JsonNode.class,
        (client, payload, ack) -> {
          SocketEventPayloads.validateUidOrDisconnect(client, payload);
          String roomId = SocketEventPayloads.asString(payload, "roomId");
          String uid = SocketEventPayloads.asString(payload, "uid");
          ArrayList<Integer> inputsList = new ArrayList<Integer>();
          JsonNode arr = payload.path("inputs");
          if (arr.isArray()) {
            for (JsonNode n : arr) {
              inputsList.add(n.asInt(-1));
            }
          }
          int[] inputs = new int[inputsList.size()];
          for (int i = 0; i < inputsList.size(); i++) {
            inputs[i] = inputsList.get(i);
          }
          int[] finalInputs = inputs;
          roomCommands.runWithServer(
              roomId,
              server,
              (s, room) -> rest.onRhythmSubmit(s, roomId, uid, finalInputs, room));
        });
    server.addEventListener(
        "tiebreaker_memory_flip",
        JsonNode.class,
        (client, payload, ack) -> {
          SocketEventPayloads.validateUidOrDisconnect(client, payload);
          String roomId = SocketEventPayloads.asString(payload, "roomId");
          String uid = SocketEventPayloads.asString(payload, "uid");
          int cellIndex = payload.path("cellIndex").asInt(-1);
          roomCommands.runWithServer(
              roomId,
              server,
              (s, room) -> rest.onMemoryFlip(client, s, roomId, uid, cellIndex, room));
        });
  }
}
