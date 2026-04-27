package com.sok.backend.realtime.room;

import com.sok.backend.realtime.match.RoomState;

/**
 * Room mutation without a {@link com.corundumstudio.socketio.SocketIOServer} (e.g. timer-driven).
 */
@FunctionalInterface
public interface RoomStateOnlyAction {
  void run(RoomState room);
}
