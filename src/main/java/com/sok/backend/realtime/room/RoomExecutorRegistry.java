package com.sok.backend.realtime.room;

import com.sok.backend.config.RealtimeScaleProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/**
 * Per-room serialisation: every mutation for a given {@code roomId} runs on the shared worker pool
 * under a stable lock so listeners never race each other. Also owns pool metrics and lifecycle.
 */
@Component
public class RoomExecutorRegistry implements DisposableBean {
  private static final Logger log = LoggerFactory.getLogger(RoomExecutorRegistry.class);

  private final RealtimeScaleProperties scale;
  private final Counter rejectedRoomEvents;
  private final Counter rejectedRoomQueueFull;
  private final Timer roomTaskTimer;
  private final ThreadPoolExecutor roomWorkers;
  private final ConcurrentHashMap<String, Object> roomLocks = new ConcurrentHashMap<>();

  public RoomExecutorRegistry(RealtimeScaleProperties scale, MeterRegistry meterRegistry) {
    this.scale = scale;
    this.rejectedRoomEvents =
        Counter.builder("sok.realtime.rejected_events")
            .description("Events rejected due missing room or lock")
            .register(meterRegistry);
    this.rejectedRoomQueueFull =
        Counter.builder("sok.realtime.room_tasks_rejected_queue")
            .description("Room tasks rejected because worker pool queue is full")
            .register(meterRegistry);
    this.roomTaskTimer =
        Timer.builder("sok.realtime.room_task")
            .description("Latency of serialized room mutation tasks")
            .publishPercentileHistogram()
            .register(meterRegistry);
    this.roomWorkers =
        new ThreadPoolExecutor(
            scale.getRoomWorkerCoreThreads(),
            scale.getRoomWorkerMaxThreads(),
            60L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(scale.getRoomTaskQueueCapacity()),
            new ThreadFactory() {
              private final AtomicLong n = new AtomicLong();

              @Override
              public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("room-worker-" + n.incrementAndGet());
                t.setDaemon(true);
                return t;
              }
            },
            new ThreadPoolExecutor.AbortPolicy());
    Gauge.builder("sok.realtime.room_worker_queue", roomWorkers, p -> p.getQueue().size())
        .register(meterRegistry);
    Gauge.builder("sok.realtime.room_worker_active", roomWorkers, ThreadPoolExecutor::getActiveCount)
        .register(meterRegistry);
  }

  /** Submit a mutation for {@code roomId}. Returns false if rejected (e.g. pool queue full). */
  public boolean submitToRoom(String roomId, Runnable task) {
    if (roomId == null) {
      rejectedRoomEvents.increment();
      return false;
    }
    final Object lock = roomLocks.computeIfAbsent(roomId, k -> new Object());
    Runnable wrapped =
        () -> {
          try {
            if (scale.isRecordRoomTaskLatency()) {
              roomTaskTimer.record(
                  () -> {
                    synchronized (lock) {
                      task.run();
                    }
                  });
            } else {
              synchronized (lock) {
                task.run();
              }
            }
          } catch (RuntimeException ex) {
            log.error("sok room task failed roomId={} err={}", roomId, ex.toString(), ex);
          }
        };
    try {
      roomWorkers.execute(wrapped);
      return true;
    } catch (RejectedExecutionException ex) {
      rejectedRoomQueueFull.increment();
      return false;
    }
  }

  /** Drop the per-room lock entry when the room is torn down. */
  public void removeRoomLock(String roomId) {
    roomLocks.remove(roomId);
  }

  public int activeWorkerCount() {
    return roomWorkers.getActiveCount();
  }

  public int queueDepth() {
    return roomWorkers.getQueue().size();
  }

  public boolean hasQueueHeadroom() {
    int cap = scale.getRoomTaskQueueCapacity();
    if (cap <= 0) {
      return true;
    }
    return roomWorkers.getQueue().size() < cap - 1;
  }

  @Override
  public void destroy() {
    roomWorkers.shutdown();
    try {
      if (!roomWorkers.awaitTermination(15L, TimeUnit.SECONDS)) {
        roomWorkers.shutdownNow();
      }
    } catch (InterruptedException ex) {
      roomWorkers.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}
