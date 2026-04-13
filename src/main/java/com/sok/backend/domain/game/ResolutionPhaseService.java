package com.sok.backend.domain.game;

import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ResolutionPhaseService {
  public String topScorer(Map<String, Integer> scoreByUid) {
    String winner = null;
    int best = Integer.MIN_VALUE;
    for (Map.Entry<String, Integer> e : scoreByUid.entrySet()) {
      int score = e.getValue() == null ? 0 : e.getValue().intValue();
      if (score > best) {
        best = score;
        winner = e.getKey();
      }
    }
    return winner;
  }
}
