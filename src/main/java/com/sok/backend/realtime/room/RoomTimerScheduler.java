package com.sok.backend.realtime.room;

import com.sok.backend.realtime.match.RoomState;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/**
 * Shared scheduled executor plus per-room named timer bookkeeping. Timers are stored on
 * {@link RoomState#timers} so every concern (phase orchestrators, lifecycle) can cancel them by key
 * without needing to share a handle.
 */
@Component
public class RoomTimerScheduler implements DisposableBean {
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(8);

  /** Replace (if present) a named timer for {@code room}; cancels the previous one. */
  public void scheduleTimer(RoomState room, String key, long delayMs, Runnable task) {
    cancelTimer(room, key);
    ScheduledFuture<?> future = scheduler.schedule(task, delayMs, TimeUnit.MILLISECONDS);
    room.timers.put(key, future);
  }

  public void cancelTimer(RoomState room, String key) {
    ScheduledFuture<?> f = room.timers.remove(key);
    if (f != null) f.cancel(false);
  }

  /** Cancel every outstanding timer for {@code room}. Called when a room is shutdown. */
  public void cancelAll(RoomState room) {
    for (ScheduledFuture<?> f : room.timers.values()) f.cancel(false);
    room.timers.clear();
  }

  /** Schedule a one-off task that is not tracked against a specific room. */
  public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
    return scheduler.schedule(task, delay, unit);
  }

  public ScheduledFuture<?> scheduleAtFixedRate(
      Runnable task, long initialDelay, long period, TimeUnit unit) {
    return scheduler.scheduleAtFixedRate(task, initialDelay, period, unit);
  }

  @Override
  public void destroy() {
    scheduler.shutdownNow();
  }
}
