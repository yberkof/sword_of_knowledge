package com.sok.backend.realtime.socket;

import com.sok.backend.realtime.match.RematchService;
import com.sok.backend.realtime.match.RoomState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Socket event handler for rematch votes. 
 * Delegates core logic to {@link RematchService} to follow SRP.
 */
@Component
public class RematchEventHandler {
  private static final Logger log = LoggerFactory.getLogger(RematchEventHandler.class);
  private static final String PHASE_ENDED = "ended";

  private final RematchService rematchService;

  public RematchEventHandler(RematchService rematchService) {
    this.rematchService = rematchService;
  }

  public void onVoteRematch(String roomId, String uid, RoomState room) {
    if (!PHASE_ENDED.equals(room.phase)) {
      log.warn("sok vote_rematch rejected: room {} not in ended phase", roomId);
      return;
    }

    rematchService.processRematchVote(room, uid);
  }
}
