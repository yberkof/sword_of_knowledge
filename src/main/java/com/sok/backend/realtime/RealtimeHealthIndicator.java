package com.sok.backend.realtime;

import com.sok.backend.integration.redis.ScalingRedisClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class RealtimeHealthIndicator implements HealthIndicator {
  private final SocketGateway socketGateway;
  private final boolean socketEnabled;
  private final ObjectProvider<ScalingRedisClient> redis;

  public RealtimeHealthIndicator(
      SocketGateway socketGateway,
      @Value("${app.socket.enabled:true}") boolean socketEnabled,
      ObjectProvider<ScalingRedisClient> redis) {
    this.socketGateway = socketGateway;
    this.socketEnabled = socketEnabled;
    this.redis = redis;
  }

  @Override
  public Health health() {
    if (!socketEnabled) {
      return Health.up().withDetail("socket", "disabled").build();
    }
    int rooms = socketGateway.roomCount();
    int activeWorkers = socketGateway.roomExecutorCount();
    int queueDepth = socketGateway.roomWorkerQueueDepth();
    int online = socketGateway.currentOnlinePlayers();
    ScalingRedisClient client = redis.getIfAvailable();
    boolean redisOk = client == null || client.pingOk();
    if (!socketGateway.isHealthy()) {
      return Health.down()
          .withDetail("roomCount", rooms)
          .withDetail("roomWorkerActive", activeWorkers)
          .withDetail("roomWorkerQueue", queueDepth)
          .withDetail("onlinePlayers", online)
          .withDetail("reason", "room_worker_queue_saturated")
          .build();
    }
    Health.Builder b =
        Health.up()
            .withDetail("roomCount", rooms)
            .withDetail("roomWorkerActive", activeWorkers)
            .withDetail("roomWorkerQueue", queueDepth)
            .withDetail("onlinePlayers", online);
    if (client != null) {
      b.withDetail("redisOk", redisOk);
    }
    return b.build();
  }
}
