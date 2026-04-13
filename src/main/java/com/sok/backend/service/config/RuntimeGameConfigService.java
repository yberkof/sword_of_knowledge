package com.sok.backend.service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RuntimeGameConfigService {
  private final GameRuntimeConfigRepository repository;
  private final ObjectMapper objectMapper;
  private final AtomicReference<GameRuntimeConfig> cache =
      new AtomicReference<GameRuntimeConfig>(GameRuntimeConfig.withDefaults());

  public RuntimeGameConfigService(GameRuntimeConfigRepository repository, ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
    refresh();
  }

  public GameRuntimeConfig get() {
    return cache.get();
  }

  public GameRuntimeConfig update(GameRuntimeConfig next) {
    validate(next);
    try {
      repository.savePayload(objectMapper.writeValueAsString(next));
    } catch (Exception ex) {
      throw new IllegalArgumentException("invalid_config_payload", ex);
    }
    cache.set(next);
    return next;
  }

  @Scheduled(fixedDelayString = "${app.game-config-refresh-ms:5000}")
  public void refresh() {
    try {
      GameRuntimeConfig next =
          repository
              .findPayload()
              .map(this::decode)
              .orElseGet(
                  new java.util.function.Supplier<GameRuntimeConfig>() {
                    @Override
                    public GameRuntimeConfig get() {
                      return GameRuntimeConfig.withDefaults();
                    }
                  });
      validate(next);
      cache.set(next);
    } catch (Exception ignored) {
      // keep last valid value
    }
  }

  private GameRuntimeConfig decode(String raw) {
    try {
      return objectMapper.readValue(raw, GameRuntimeConfig.class);
    } catch (Exception ex) {
      return GameRuntimeConfig.withDefaults();
    }
  }

  private void validate(GameRuntimeConfig cfg) {
    if (cfg.getMinPlayers() < 2) throw new IllegalArgumentException("minPlayers must be >= 2");
    if (cfg.getMaxPlayers() < cfg.getMinPlayers())
      throw new IllegalArgumentException("maxPlayers must be >= minPlayers");
    if (cfg.getInitialCastleHp() < 1) throw new IllegalArgumentException("initialCastleHp must be >= 1");
    if (cfg.getDuelDurationMs() < 1000) throw new IllegalArgumentException("duelDurationMs too small");
    if (cfg.getMaxRounds() < 1) throw new IllegalArgumentException("maxRounds must be >= 1");
    if (cfg.getMaxMatchDurationSeconds() < 60)
      throw new IllegalArgumentException("maxMatchDurationSeconds must be >= 60");
  }
}
