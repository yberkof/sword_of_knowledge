package com.sok.backend.domain.game.engine;

import com.sok.backend.realtime.match.RoomState;

/**
 * Everything a {@link Phase} / {@link Turn} needs to do its job, wrapped in one value object.
 *
 * <p>Passing a context (vs. injecting services into the phase bean) keeps phases stateless and
 * thread-safe while letting tests plug in a fake {@link RealtimeBridge} / {@link MatchClock}.
 */
public record MatchContext(
    RoomState room,
    GameMode mode,
    RealtimeBridge bridge,
    MatchClock clock) {

  public ModeRules rules() {
    return mode.rules();
  }

  public long nowMs() {
    return clock.nowMs();
  }
}
