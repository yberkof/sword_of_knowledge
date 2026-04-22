package com.sok.backend.realtime;

import com.sok.backend.integration.redis.ScalingRedisClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

/** Minimal JSON snapshots for reconnect / ops tooling when Redis is enabled. */
@Service
@ConditionalOnBean(ScalingRedisClient.class)
public class RoomSnapshotPublisher {
  private static final int TTL_SECONDS = 600;

  private final ScalingRedisClient redis;

  public RoomSnapshotPublisher(ScalingRedisClient redis) {
    this.redis = redis;
  }

  private static String snapKey(String roomId) {
    return "sok:snap:" + roomId;
  }

  public void publish(String roomId, String json) {
    redis.setWithTtlSeconds(snapKey(roomId), json, TTL_SECONDS);
  }

  public void remove(String roomId) {
    redis.delete(snapKey(roomId));
  }
}
