package com.sok.backend.domain.game;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CastlePlacementPhaseService {
  public boolean allCastlesPlaced(List<String> activePlayerUids, Map<String, Integer> castleRegionByUid) {
    for (String uid : activePlayerUids) {
      if (!castleRegionByUid.containsKey(uid)) return false;
      if (castleRegionByUid.get(uid) == null) return false;
    }
    return true;
  }
}
