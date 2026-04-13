package com.sok.backend.realtime;

import com.sok.backend.config.AppInstanceProperties;
import com.sok.backend.integration.redis.ScalingRedisClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

/**
 * Publishes {@code roomId -> instanceId} in Redis for cross-pod routing / ops. Keys expire unless
 * refreshed by the realtime tick loop.
 */
@Service
@ConditionalOnBean(ScalingRedisClient.class)
public class RoomRegistryService {
  private static final int TTL_SECONDS = 120;

  private final ScalingRedisClient redis;
  private final AppInstanceProperties instance;

  public RoomRegistryService(ScalingRedisClient redis, AppInstanceProperties instance) {
    this.redis = redis;
    this.instance = instance;
  }

  private static String key(String roomId) {
    return "sok:room:" + roomId;
  }

  public void refresh(String roomId) {
    redis.setWithTtlSeconds(key(roomId), instance.getId(), TTL_SECONDS);
  }

  public void remove(String roomId) {
    redis.delete(key(roomId));
  }
}
