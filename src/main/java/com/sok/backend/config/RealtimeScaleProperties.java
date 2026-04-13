package com.sok.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.realtime")
public class RealtimeScaleProperties {
  /** Max JVM-local rooms before rejecting new matchmaking. */
  private int maxRooms = 10000;

  /** Max concurrently online players across all rooms (socket join gate). */
  private int maxOnlinePlayers = 12000;

  /** Bounded queue for room work; excess submissions rejected. */
  private int roomTaskQueueCapacity = 50000;

  /** Core threads for shared room worker pool. */
  private int roomWorkerCoreThreads = 32;

  /** Max threads for shared room worker pool. */
  private int roomWorkerMaxThreads = 256;

  /** Micrometer summary window for room task latency (optional). */
  private boolean recordRoomTaskLatency = true;

  /** Publish minimal room snapshots to Redis when integration enabled. */
  private boolean snapshotToRedis = true;

  /** Snapshot interval ms (only when Redis + snapshot enabled). */
  private long snapshotIntervalMs = 15000L;

  public int getMaxRooms() {
    return maxRooms;
  }

  public void setMaxRooms(int maxRooms) {
    this.maxRooms = maxRooms;
  }

  public int getMaxOnlinePlayers() {
    return maxOnlinePlayers;
  }

  public void setMaxOnlinePlayers(int maxOnlinePlayers) {
    this.maxOnlinePlayers = maxOnlinePlayers;
  }

  public int getRoomTaskQueueCapacity() {
    return roomTaskQueueCapacity;
  }

  public void setRoomTaskQueueCapacity(int roomTaskQueueCapacity) {
    this.roomTaskQueueCapacity = roomTaskQueueCapacity;
  }

  public int getRoomWorkerCoreThreads() {
    return roomWorkerCoreThreads;
  }

  public void setRoomWorkerCoreThreads(int roomWorkerCoreThreads) {
    this.roomWorkerCoreThreads = roomWorkerCoreThreads;
  }

  public int getRoomWorkerMaxThreads() {
    return roomWorkerMaxThreads;
  }

  public void setRoomWorkerMaxThreads(int roomWorkerMaxThreads) {
    this.roomWorkerMaxThreads = roomWorkerMaxThreads;
  }

  public boolean isRecordRoomTaskLatency() {
    return recordRoomTaskLatency;
  }

  public void setRecordRoomTaskLatency(boolean recordRoomTaskLatency) {
    this.recordRoomTaskLatency = recordRoomTaskLatency;
  }

  public boolean isSnapshotToRedis() {
    return snapshotToRedis;
  }

  public void setSnapshotToRedis(boolean snapshotToRedis) {
    this.snapshotToRedis = snapshotToRedis;
  }

  public long getSnapshotIntervalMs() {
    return snapshotIntervalMs;
  }

  public void setSnapshotIntervalMs(long snapshotIntervalMs) {
    this.snapshotIntervalMs = snapshotIntervalMs;
  }
}
