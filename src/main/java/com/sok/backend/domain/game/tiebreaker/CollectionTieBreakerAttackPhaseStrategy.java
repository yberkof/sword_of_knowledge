package com.sok.backend.domain.game.tiebreaker;

import com.sok.backend.realtime.match.DuelState;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Collection vote lobby before a sub-minigame. {@link #begin} only starts the pick window; resolution
 * and sub-games are handled by {@link CollectionTieBreakService} and {@code SocketGateway}.
 */
@Component
@Order(85)
public class CollectionTieBreakerAttackPhaseStrategy implements TieBreakerAttackPhaseStrategy {

  public static final String PICK_TIMER_KEY = CollectionTieBreakService.COLLECTION_PICK_TIMER_KEY;

  private final CollectionTieBreakService collectionTieBreakService;

  public CollectionTieBreakerAttackPhaseStrategy(CollectionTieBreakService collectionTieBreakService) {
    this.collectionTieBreakService = collectionTieBreakService;
  }

  @Override
  public boolean supports(String normalizedEffectiveMode, String defenderUid) {
    return TieBreakerModeIds.MINIGAME_COLLECTION.equals(normalizedEffectiveMode)
        && !"neutral".equals(defenderUid);
  }

  @Override
  public void begin(BeginContext ctx) {
    DuelState duel = ctx.duel();
    collectionTieBreakService.beginLobby(duel, ctx.bridge());
  }
}
