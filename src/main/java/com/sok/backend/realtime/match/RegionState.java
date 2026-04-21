package com.sok.backend.realtime.match;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-region slice of the map for a live match. Fields match the shape currently emitted to
 * clients on {@code room_update.mapState}.
 */
public class RegionState {
  public int id;
  public String ownerUid;
  public boolean isCastle;
  public boolean isShielded;
  public String type;
  public int points;
  public List<Integer> neighbors = new ArrayList<Integer>();
}
