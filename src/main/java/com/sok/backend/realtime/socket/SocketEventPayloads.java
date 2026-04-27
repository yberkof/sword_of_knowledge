package com.sok.backend.realtime.socket;

import com.corundumstudio.socketio.SocketIOClient;
import com.fasterxml.jackson.databind.JsonNode;

/** Shared Socket.IO event payload checks (extracted from {@link com.sok.backend.realtime.SocketGateway}). */
public final class SocketEventPayloads {

  private SocketEventPayloads() {}

  public static void validateUidOrDisconnect(SocketIOClient client, JsonNode payload) {
    if (!payloadHasSocketUid(client, payload, "uid")
        && !payloadHasSocketUid(client, payload, "attackerUid")) {
      client.disconnect();
    }
  }

  public static boolean payloadHasSocketUid(SocketIOClient client, JsonNode payload, String field) {
    Object socketUid = client.get("uid");
    if (socketUid == null || payload == null || payload.path(field).isMissingNode()) {
      return false;
    }
    return payload.path(field).asText("").equals(String.valueOf(socketUid));
  }

  public static String asString(JsonNode payload, String field) {
    if (payload == null || payload.path(field).isMissingNode()) {
      return "";
    }
    return payload.path(field).asText("");
  }
}
