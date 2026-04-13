package com.sok.backend.integration.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Optional Lettuce client (no spring-data-redis) for distributed rate limits, room routing
 * registry, and lightweight snapshots.
 */
@Component
@ConditionalOnProperty(name = "app.integration.redis.enabled", havingValue = "true")
public class ScalingRedisClient implements DisposableBean, InitializingBean {
  private static final Logger log = LoggerFactory.getLogger(ScalingRedisClient.class);

  private final String url;
  private RedisClient client;
  private StatefulRedisConnection<String, String> connection;

  public ScalingRedisClient(@Value("${app.integration.redis.url}") String url) {
    this.url = url;
  }

  @Override
  public void afterPropertiesSet() {
    client = RedisClient.create(url);
    connection = client.connect();
    log.info("ScalingRedisClient connected");
  }

  private RedisCommands<String, String> sync() {
    return connection.sync();
  }

  /**
   * Fixed-window style counter: INCR key; on first increment set TTL. Returns whether count is
   * within limit.
   */
  public boolean allowIncrement(String key, int limit, long windowMs) {
    Long n = sync().incr(key);
    if (n != null && n.longValue() == 1L) {
      sync().pexpire(key, windowMs);
    }
    return n != null && n.longValue() <= (long) limit;
  }

  public void setWithTtlSeconds(String key, String value, long ttlSeconds) {
    sync().set(key, value, SetArgs.Builder.ex(ttlSeconds));
  }

  public void delete(String key) {
    sync().del(key);
  }

  public boolean pingOk() {
    try {
      return "PONG".equals(sync().ping());
    } catch (Exception e) {
      log.warn("redis ping failed: {}", e.toString());
      return false;
    }
  }

  @Override
  public void destroy() {
    if (connection != null) {
      try {
        connection.close();
      } catch (Exception ignored) {
      }
    }
    if (client != null) {
      try {
        client.shutdown(200, 200, TimeUnit.MILLISECONDS);
      } catch (Exception ignored) {
      }
    }
  }
}
