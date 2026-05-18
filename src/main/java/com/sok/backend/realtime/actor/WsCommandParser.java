package com.sok.backend.realtime.actor;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Maps inbound WebSocket JSON to {@link RoomCommand} instances.
 */
public final class WsCommandParser {

    private static final Logger log = LoggerFactory.getLogger(WsCommandParser.class);

    private WsCommandParser() {}

    public static Optional<RoomCommand> parse(String uid, JsonNode node) {
        String type = node.path("type").asText("");
        return switch (type) {
            case "join", "join_matchmaking" -> Optional.of(new RoomCommand.Join(
                uid,
                node.path("name").asText("Warrior"),
                node.path("avatar").asText(""),
                null
            ));
            case "start_match" -> Optional.of(new RoomCommand.StartMatch(uid));
            case "claim_region" -> Optional.of(new RoomCommand.ClaimRegion(uid, node.path("regionId").asInt()));
            case "attack" -> Optional.of(new RoomCommand.Attack(uid, node.path("targetHexId").asInt()));
            case "submit_estimation" -> Optional.of(new RoomCommand.SubmitEstimation(
                uid, node.path("value").asInt(), System.currentTimeMillis()));
            case "submit_answer" -> Optional.of(new RoomCommand.SubmitAnswer(
                uid, node.path("answerIndex").asInt(), System.currentTimeMillis()));
            case "leave_room" -> Optional.of(new RoomCommand.LeaveRoom(uid));
            default -> {
                if (!type.isEmpty()) {
                    log.debug("Unknown WS command type: {}", type);
                }
                yield Optional.empty();
            }
        };
    }
}
