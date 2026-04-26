package com.sok.backend.realtime.room;

import com.corundumstudio.socketio.SocketIOServer;
import com.sok.backend.realtime.match.RoomState;
import org.springframework.stereotype.Component;

/**
 * The only supported way to run code that mutates (or must see a consistent view of) a
 * {@link RoomState}: enqueue on the per-room lock in {@link RoomExecutorRegistry} and resolve the
 * room from {@link RoomStore} inside the task.
 *
 * <p>Callers on Socket.IO or timer threads must not read {@link RoomState} directly before
 * enqueueing: always pass work through this service to avoid data races with other threads.
 */
@Component
public class RoomSerialCommandService {

  private final RoomStore store;
  private final RoomExecutorRegistry roomExecutors;

  public RoomSerialCommandService(RoomStore store, RoomExecutorRegistry roomExecutors) {
    this.store = store;
    this.roomExecutors = roomExecutors;
  }

  /**
   * @return false if the work was not scheduled (e.g. missing roomId or pool back-pressure).
   */
  public boolean runWithServer(String roomId, SocketIOServer server, RoomIsolatedAction action) {
    if (roomId == null) {
      return false;
    }
    return roomExecutors.submitToRoom(
        roomId,
        () -> {
          RoomState room = store.get(roomId);
          if (room == null) {
            return;
          }
          action.run(server, room);
        });
  }

  public boolean runInRoom(String roomId, RoomStateOnlyAction action) {
    if (roomId == null) {
      return false;
    }
    return roomExecutors.submitToRoom(
        roomId,
        () -> {
          RoomState room = store.get(roomId);
          if (room == null) {
            return;
          }
          action.run(room);
        });
  }
}
