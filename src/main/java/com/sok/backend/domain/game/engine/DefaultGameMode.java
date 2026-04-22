package com.sok.backend.domain.game.engine;

import com.sok.backend.service.config.GameRuntimeConfig;
import com.sok.backend.service.config.RuntimeGameConfigService;
import org.springframework.stereotype.Component;

/**
 * The built-in game mode ({@code rulesetId="sok_v1"}) that mirrors the legacy
 * {@code SocketGateway} behaviour: waiting → castle_placement → claiming_question / claiming_pick
 * → battle (with MCQ duels + configurable tie-break) → ended.
 *
 * <p>Rule values are read live from {@link RuntimeGameConfigService} so any runtime-config reload
 * (admin screen) takes effect on the next resolve without restarting the JVM. Team policy is
 * derived from the room's {@code matchMode} via a dedicated constructor on {@link ModeRules} at
 * the caller — this mode itself remains team-agnostic and leaves the policy field on the common
 * {@code ModeRules}.
 *
 * <p>To craft a new mode, copy this class, change {@link #id()}, and override whichever
 * {@link ModeRules} field or phase bean differs.
 */
@Component
public class DefaultGameMode implements GameMode {

  public static final String ID = "sok_v1";

  private final RuntimeGameConfigService configService;

  public DefaultGameMode(RuntimeGameConfigService configService) {
    this.configService = configService;
  }

  @Override
  public String id() {
    return ID;
  }

  @Override
  public String displayName() {
    return "Sword of Knowledge v1";
  }

  @Override
  public ModeRules rules() {
    return rulesFor(null);
  }

  /**
   * Build {@link ModeRules} resolving {@link TeamPolicy} from the room's {@code matchMode} string.
   * Callers that know whether the current room is FFA vs. teams_2v2 should prefer this overload
   * so friendly-fire / start-count checks are consistent across the engine.
   */
  public ModeRules rulesFor(String matchMode) {
    GameRuntimeConfig cfg = configService.get();
    TeamPolicy policy = TeamPolicy.fromWireName(matchMode == null ? cfg.getDefaultMatchMode() : matchMode);
    return new ModeRules(
        policy,
        policy.requiredPlayersToStart(cfg.getMinPlayers()),
        cfg.getMaxPlayers(),
        cfg.getMaxRounds(),
        cfg.getInitialCastleHp(),
        cfg.getClaimFirstPicks(),
        cfg.getClaimSecondPicks(),
        cfg.getDuelDurationMs(),
        cfg.getClaimDurationMs(),
        cfg.getTiebreakDurationMs(),
        cfg.getTieBreakerMode(),
        cfg.getMaxMcqTieRetries(),
        cfg.getXoDrawMaxReplay());
  }
}
