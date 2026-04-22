package com.sok.backend.realtime.room;

import com.corundumstudio.socketio.SocketIOServer;
import com.sok.backend.realtime.match.RoomState;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Single owner of fan-out to clients. Wraps {@link SocketIOServer} so phase orchestrators never
 * need to hand the raw server around, and co-locates the standard {@code room_update} shape with
 * {@link RoomClientSnapshotFactory}.
 */
@Component
public class RoomBroadcaster {
  private final RoomClientSnapshotFactory snapshotFactory;
  private volatile SocketIOServer server;

  public RoomBroadcaster(RoomClientSnapshotFactory snapshotFactory) {
    this.snapshotFactory = snapshotFactory;
  }

  /** Called once from {@link com.sok.backend.realtime.SocketGateway} during registration. */
  public void attach(SocketIOServer server) {
    this.server = server;
  }

  public SocketIOServer server() {
    SocketIOServer s = server;
    if (s == null) {
      throw new IllegalStateException("RoomBroadcaster not attached to a SocketIOServer yet");
    }
    return s;
  }

  public RoomClientSnapshotFactory snapshotFactory() {
    return snapshotFactory;
  }

  /** Bump lastActivityAt and broadcast the canonical {@code room_update} snapshot. */
  public void emitRoomUpdate(RoomState room) {
    room.lastActivityAt = System.currentTimeMillis();
    server().getRoomOperations(room.id).sendEvent("room_update", snapshotFactory.roomToClient(room));
  }

  /** Send an arbitrary event to all members of {@code roomId}. */
  public void sendToRoom(String roomId, String event, Object payload) {
    server().getRoomOperations(roomId).sendEvent(event, payload);
  }

  public void sendToRoom(String roomId, String event, Map<String, Object> payload) {
    server().getRoomOperations(roomId).sendEvent(event, payload);
  }
}
