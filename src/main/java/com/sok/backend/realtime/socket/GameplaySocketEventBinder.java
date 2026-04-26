package com.sok.backend.realtime.socket;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.sok.backend.domain.game.GameInputRules;
import com.sok.backend.realtime.binding.SocketEventBinder;
import com.sok.backend.realtime.room.RoomSerialCommandService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Castle placement, claim, attack, and duel answer/estimation (moved from {@code SocketGateway}).
 */
@Component
public class GameplaySocketEventBinder implements SocketEventBinder {
  private static final Logger log = LoggerFactory.getLogger(GameplaySocketEventBinder.class);

  private final RoomSerialCommandService roomCommands;
  private final PlaceCastleEventHandler placeCastle;
  private final ClaimRegionEventHandler claimRegion;
  private final AttackEventHandler attack;
  private final SubmitEstimationEventHandler submitEst;
  private final SubmitDuelAnswerEventHandler submitDuel;
  private final GameInputRules gameInputRules;

  public GameplaySocketEventBinder(
      RoomSerialCommandService roomCommands,
      PlaceCastleEventHandler placeCastle,
      ClaimRegionEventHandler claimRegion,
      AttackEventHandler attack,
      SubmitEstimationEventHandler submitEst,
      SubmitDuelAnswerEventHandler submitDuel,
      GameInputRules gameInputRules) {
    this.roomCommands = roomCommands;
    this.placeCastle = placeCastle;
    this.claimRegion = claimRegion;
    this.attack = attack;
    this.submitEst = submitEst;
    this.submitDuel = submitDuel;
    this.gameInputRules = gameInputRules;
  }

  @Override
  public void bind(SocketIOServer server) {
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
        "attack",
        JsonNode.class,
        (client, payload, ack) -> {
          SocketEventPayloads.validateUidOrDisconnect(client, payload);
          String roomId = SocketEventPayloads.asString(payload, "roomId");
          String attackerUid = SocketEventPayloads.asString(payload, "attackerUid");
          int targetHexId = payload.path("targetHexId").asInt(-1);
          roomCommands.runWithServer(
              roomId,
              server,
              (s, room) -> attack.onAttack(client, s, roomId, attackerUid, targetHexId, room));
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
    server.addEventListener(
        "submit_answer",
        JsonNode.class,
        (client, payload, ack) -> {
          SocketEventPayloads.validateUidOrDisconnect(client, payload);
          String roomId = SocketEventPayloads.asString(payload, "roomId");
          String uid = SocketEventPayloads.asString(payload, "uid");
          logSubmitAnswer(client, roomId, uid, payload);
          Integer answerIndex = parseAnswerIndex(payload, roomId, uid);
          if (answerIndex == null) {
            return;
          }
          final int finalAnswer = answerIndex;
          roomCommands.runWithServer(
              roomId,
              server,
              (s, room) -> submitDuel.onSubmit(roomId, uid, finalAnswer, s, room));
        });
  }

  private void logSubmitAnswer(
      SocketIOClient client, String roomId, String uid, JsonNode payload) {
    log.info(
        "sok evt submit_answer roomId={} uid={} rawAnswerIndex={}",
        roomId,
        uid,
        payload.path("answerIndex"));
  }

  private Integer parseAnswerIndex(JsonNode payload, String roomId, String uid) {
    Integer answerIndex =
        gameInputRules.coerceChoiceIndex(
            payload.path("answerIndex").isMissingNode()
                ? null
                : payload.path("answerIndex").asText());
    if (answerIndex == null && payload.path("answerIndex").asInt(99999) == -1) {
      answerIndex = -1;
    }
    if (answerIndex == null) {
      log.warn("sok submit_answer rejected: could not coerce answerIndex roomId={} uid={}", roomId, uid);
      return null;
    }
    return answerIndex;
  }
}
