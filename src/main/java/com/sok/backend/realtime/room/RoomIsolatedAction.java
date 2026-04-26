package com.sok.backend.realtime.room;

import com.corundumstudio.socketio.SocketIOServer;
import com.sok.backend.realtime.match.RoomState;

/**
 * A chunk of work that may read and mutate a {@link RoomState}. Must run only on that room’s
 * serialized executor (see {@link RoomSerialCommandService} / {@link RoomExecutorRegistry}).
 */
@FunctionalInterface
public interface RoomIsolatedAction {
  void run(SocketIOServer server, RoomState room);
}
