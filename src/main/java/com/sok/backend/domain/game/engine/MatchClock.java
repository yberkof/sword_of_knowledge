package com.sok.backend.domain.game.engine;

import java.time.Duration;

/**
 * Scheduler port. Phases must schedule timers through this interface so they stay unit-testable
 * (the test impl advances time synchronously; the production impl wraps the gateway's
 * {@code ScheduledExecutorService}).
 *
 * <p>Timers are identified by a logical key. Re-scheduling with the same key implicitly cancels
 * the prior timer — matches the existing {@code scheduleRoomTimer} semantics used by
 * {@code SocketGateway} today.
 */
public interface MatchClock {

  /** Current wall-clock in millis. Phases should never call {@code System.currentTimeMillis()} directly. */
  long nowMs();

  /** Schedule (or reschedule) a logical timer. {@code task} runs on the match's room worker. */
  void schedule(String timerKey, Duration after, Runnable task);

  /** Cancel a pending timer. No-op when unknown. */
  void cancel(String timerKey);
}
