package com.sok.backend.realtime.match;

import org.springframework.stereotype.Component;

/**
 * Manages domain state for the claim phase.
 */
@Component
public class ClaimStateService {

  public void resetClaimState(RoomState room) {
    room.claimPicksLeftByUid.clear();
    room.claimQueue.clear();
    room.estimationAnswers.clear();
  }

  public void rotateClaimTurn(RoomState room) {
    if (room.claimQueue.isEmpty()) {
      room.claimTurnUid = null;
      return;
    }
    String current = room.claimQueue.remove(0);
    Integer remain = room.claimPicksLeftByUid.get(current);
    if (remain != null && remain > 0) {
      room.claimQueue.add(current);
    }
    room.claimTurnUid = room.claimQueue.isEmpty() ? null : room.claimQueue.get(0);
  }

  public boolean claimsQueueEmpty(RoomState room) {
    return room.claimTurnUid == null || room.claimQueue.isEmpty();
  }

  public boolean allRegionsClaimed(RoomState room) {
    for (RegionState r : room.regions.values()) {
      if (r.ownerUid == null) return false;
    }
    return true;
  }

  public int countNeutralRegions(RoomState room) {
    int n = 0;
    for (RegionState r : room.regions.values()) {
      if (r.ownerUid == null) n++;
    }
    return n;
  }
}
