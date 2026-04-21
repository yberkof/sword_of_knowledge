package com.sok.backend.domain.game.tiebreaker;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Selects the first {@link TieBreakerAttackPhaseStrategy} that {@linkplain TieBreakerAttackPhaseStrategy#supports
 * supports} the effective mode (beans are ordered via Spring {@link org.springframework.core.annotation.Order}).
 */
@Component
public class TieBreakerAttackPhaseComposer {

  private final List<TieBreakerAttackPhaseStrategy> strategies;

  public TieBreakerAttackPhaseComposer(List<TieBreakerAttackPhaseStrategy> strategies) {
    this.strategies = strategies;
  }

  public TieBreakerAttackPhaseStrategy resolve(String normalizedEffectiveMode, String defenderUid) {
    for (TieBreakerAttackPhaseStrategy s : strategies) {
      if (s.supports(normalizedEffectiveMode, defenderUid)) {
        return s;
      }
    }
    throw new IllegalStateException(
        "No TieBreakerAttackPhaseStrategy for mode=" + normalizedEffectiveMode + " defender=" + defenderUid);
  }
}
