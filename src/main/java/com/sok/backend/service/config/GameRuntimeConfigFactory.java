package com.sok.backend.service.config;

import java.util.ArrayList;
import java.util.List;

public class GameRuntimeConfigFactory {
  public static GameRuntimeConfig withDefaults() {
    GameRuntimeConfig cfg = new GameRuntimeConfig();
    for (int i = 1; i <= 8; i++) cfg.getRegionPoints().put(String.valueOf(i), 1);
    cfg.getRegionPoints().put("5", 2);
    cfg.setCastleIndices(list(3, 2));
    cfg.getNeighbors().put("1", list(3, 6));
    cfg.getNeighbors().put("3", list(1, 4, 5));
    cfg.getNeighbors().put("2", list(7, 8, 5));
    cfg.getNeighbors().put("4", list(3, 5, 8));
    cfg.getNeighbors().put("5", list(2, 3, 4, 6, 7, 8));
    cfg.getNeighbors().put("6", list(1, 5, 7));
    cfg.getNeighbors().put("7", list(2, 5, 6, 8));
    cfg.getNeighbors().put("8", list(2, 5, 4));
    return cfg;
  }

  private static List<Integer> list(int... values) {
    List<Integer> out = new ArrayList<>();
    for (int value : values) out.add(value);
    return out;
  }
}
