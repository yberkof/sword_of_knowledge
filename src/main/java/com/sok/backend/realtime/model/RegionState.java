package com.sok.backend.realtime.model;

import java.util.ArrayList;
import java.util.List;

public class RegionState {
  public int id;
  public String ownerUid, type;
  public boolean isCastle;
  public List<Integer> neighbors = new ArrayList<>();
}
