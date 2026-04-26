package com.sok.backend.realtime.socket.match;

import com.sok.backend.realtime.matchmaking.MatchmakingAllocator;
import com.sok.backend.realtime.room.RoomLifecycle;
import org.springframework.stereotype.Component;

/** Rolls back an empty public queue room when a join task cannot be enqueued. */
@Component
public class MatchJoinRollbackService {
  private final MatchmakingAllocator matchmakingAllocator;
  private final RoomLifecycle lifecycle;

  public MatchJoinRollbackService(
      MatchmakingAllocator matchmakingAllocator, RoomLifecycle lifecycle) {
    this.matchmakingAllocator = matchmakingAllocator;
    this.lifecycle = lifecycle;
  }

  public void rollbackOrphanWaitingRoom(String roomId, boolean brandNewEmpty) {
    if (matchmakingAllocator.isOrphanWaitingRoom(roomId, brandNewEmpty)) {
      lifecycle.shutdownRoom(roomId);
    }
  }
}
