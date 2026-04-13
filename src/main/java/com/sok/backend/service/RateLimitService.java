package com.sok.backend.service;

import com.sok.backend.integration.redis.ScalingRedisClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {
  private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

  private final Map<String, List<Long>> buckets = new ConcurrentHashMap<String, List<Long>>();
  private final ObjectProvider<ScalingRedisClient> redis;

  public RateLimitService(ObjectProvider<ScalingRedisClient> redis) {
    this.redis = redis;
  }

  public boolean allow(String key, int limit, long windowMs) {
    ScalingRedisClient client = redis.getIfAvailable();
    if (client != null) {
      try {
        return client.allowIncrement("sok:rl:" + key, limit, windowMs);
      } catch (Exception ex) {
        log.warn("redis rate limit failed for {}, falling back to in-memory: {}", key, ex.toString());
      }
    }
    return allowInMemory(key, limit, windowMs);
  }

  private boolean allowInMemory(String key, int limit, long windowMs) {
    long now = System.currentTimeMillis();
    List<Long> bucket = buckets.computeIfAbsent(key, k -> new ArrayList<Long>());
    synchronized (bucket) {
      bucket.removeIf(ts -> now - ts > windowMs);
      if (bucket.size() >= limit) return false;
      bucket.add(now);
      return true;
    }
  }
}
