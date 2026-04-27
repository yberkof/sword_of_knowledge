package com.sok.backend.realtime.socket.match;

import com.sok.backend.realtime.match.PlayerState;
import com.sok.backend.realtime.match.RoomState;
import com.sok.backend.realtime.room.RoomStore;
import org.springframework.stereotype.Component;

/** Global count of non-eliminated, online players (used for max-capacity gating). */
@Component
public class OnlinePlayerCountService {
  private final RoomStore store;

  public OnlinePlayerCountService(RoomStore store) {
    this.store = store;
  }

  public int currentOnline() {
    int c = 0;
    for (RoomState r : store.values()) {
      for (PlayerState p : r.players) {
        if (p.online && !p.isEliminated) c++;
      }
    }
    return c;
  }
}
