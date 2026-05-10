package com.sok.backend.realtime.socket;

import com.corundumstudio.socketio.SocketIOServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.sok.backend.realtime.binding.SocketEventBinder;
import com.sok.backend.realtime.room.RoomSerialCommandService;
import org.springframework.stereotype.Component;

/**
 * Binds claim-phase events like region claiming and estimation submission.
 */
@Component
public class ClaimSocketEventBinder implements SocketEventBinder {

  private final RoomSerialCommandService roomCommands;
  private final ClaimRegionEventHandler claimRegion;
  private final SubmitEstimationEventHandler submitEst;

  public ClaimSocketEventBinder(
      RoomSerialCommandService roomCommands,
      ClaimRegionEventHandler claimRegion,
      SubmitEstimationEventHandler submitEst) {
    this.roomCommands = roomCommands;
    this.claimRegion = claimRegion;
    this.submitEst = submitEst;
  }

  @Override
  public void bind(SocketIOServer server) {
    server.addEventListener(
        "claim_region",
        JsonNode.class,
        (client, payload, ack) -> {
          SocketEventPayloads.validateUidOrDisconnect(client, payload);
          String roomId = SocketEventPayloads.asString(payload, "roomId");
          String uid = SocketEventPayloads.asString(payload, "uid");
          int regionId = payload.path("regionId").asInt(-1);
          roomCommands.runWithServer(
              roomId,
              server,
              (s, room) -> claimRegion.onClaim(client, s, roomId, uid, regionId, room));
        });

    server.addEventListener(
        "submit_estimation",
        JsonNode.class,
        (client, payload, ack) -> {
          SocketEventPayloads.validateUidOrDisconnect(client, payload);
          String roomId = SocketEventPayloads.asString(payload, "roomId");
          String uid = SocketEventPayloads.asString(payload, "uid");
          int value = payload.path("value").asInt(Integer.MIN_VALUE);
          roomCommands.runWithServer(
              roomId,
              server,
              (s, room) -> submitEst.onSubmit(roomId, uid, value, s, room));
        });
  }
}
