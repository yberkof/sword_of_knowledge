package com.sok.backend.domain.game.engine;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Spring-managed lookup of {@link GameMode} beans keyed by {@link GameMode#id()}. A new mode is
 * registered simply by declaring a new {@code @Component} implementing {@link GameMode}; no code
 * in {@code SocketGateway} has to change.
 *
 * <p>If a room has a {@code rulesetId} that no bean claims, the registry falls back to the mode
 * whose id matches {@code GameRuntimeConfig.defaultRulesetId} (today {@code "sok_v1"}), ensuring
 * that legacy rooms keep running exactly as before.
 */
@Component
public class GameModeRegistry {
  private static final Logger log = LoggerFactory.getLogger(GameModeRegistry.class);

  private final Map<String, GameMode> modesById;
  private final GameMode fallback;

  public GameModeRegistry(List<GameMode> modes) {
    LinkedHashMap<String, GameMode> map = new LinkedHashMap<>();
    for (GameMode m : modes) {
      if (m == null || m.id() == null || m.id().isBlank()) continue;
      map.put(m.id(), m);
    }
    this.modesById = Collections.unmodifiableMap(map);
    this.fallback = pickFallback(map.values());
    log.info(
        "GameModeRegistry loaded {} mode(s): {} (fallback={})",
        this.modesById.size(),
        this.modesById.keySet(),
        this.fallback == null ? "none" : this.fallback.id());
  }

  private static GameMode pickFallback(Collection<GameMode> all) {
    GameMode sokV1 = null;
    GameMode first = null;
    for (GameMode m : all) {
      if (first == null) first = m;
      if ("sok_v1".equals(m.id())) sokV1 = m;
    }
    return sokV1 != null ? sokV1 : first;
  }

  /** Resolve a mode by id, falling back to {@code sok_v1} (or the first registered mode). */
  public GameMode resolve(String rulesetId) {
    if (rulesetId != null && !rulesetId.isBlank()) {
      GameMode hit = modesById.get(rulesetId);
      if (hit != null) return hit;
      log.warn("Unknown rulesetId='{}', falling back to '{}'", rulesetId, fallback == null ? "none" : fallback.id());
    }
    return fallback;
  }

  public boolean has(String rulesetId) {
    return rulesetId != null && modesById.containsKey(rulesetId);
  }

  public Map<String, GameMode> all() {
    return modesById;
  }
}
