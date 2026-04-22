package com.sok.backend.domain.game.engine;

import java.util.Map;

/**
 * Outbound port for phases / turns / the engine to emit events to clients. Generalisation of
 * {@code TieBreakerRealtimeBridge}: the same shape, but used by every phase rather than the
 * tie-break sub-system only.
 *
 * <p>Implemented by {@code SocketGateway} in production; a simple in-memory stub is used in tests.
 */
public interface RealtimeBridge {

  String roomId();

  /** Broadcast to everyone in the match. */
  void emitToRoom(String eventName, Map<String, Object> payload);

  /** Direct send to one player by uid. No-op if they are offline. */
  void emitToUid(String uid, String eventName, Map<String, Object> payload);

  /** Force a fresh {@code room_update} snapshot to all clients. */
  void publishRoomUpdate();
}
