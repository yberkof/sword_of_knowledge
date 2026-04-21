package com.sok.backend.domain.game;

import com.sok.backend.domain.game.engine.TurnOutcome;
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

  /**
   * Engine-facing placement outcome: a {@link TurnOutcome.Placed} records which uid put their
   * castle on which region. {@code SocketGateway} still performs the authoritative mutation; this
   * helper just packages the result into the sealed outcome hierarchy for consumption by the
   * forthcoming {@code CastlePlacementPhase} bean.
   */
  public TurnOutcome.Placed placementOutcome(String uid, int regionId) {
    return new TurnOutcome.Placed(uid, regionId);
  }
}
