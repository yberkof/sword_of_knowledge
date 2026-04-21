package com.sok.backend.domain.game;

import com.sok.backend.domain.game.engine.TurnOutcome;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ClaimingPhaseService {
  public static class Metric {
    public String uid;
    public int value;
    public long latencyMs;
  }

  public List<Metric> rankByDeltaThenLatency(List<Metric> rows, int correctAnswer) {
    List<Metric> ranked = new ArrayList<Metric>(rows);
    Collections.sort(
        ranked,
        (a, b) -> {
          int da = Math.abs(a.value - correctAnswer);
          int db = Math.abs(b.value - correctAnswer);
          if (da != db) return Integer.compare(da, db);
          return Long.compare(a.latencyMs, b.latencyMs);
        });
    return ranked;
  }

  public Map<String, Integer> assignClaimPicks(List<Metric> ranked, int firstPicks, int secondPicks) {
    HashMap<String, Integer> out = new HashMap<String, Integer>();
    if (!ranked.isEmpty()) out.put(ranked.get(0).uid, firstPicks);
    if (ranked.size() > 1) out.put(ranked.get(1).uid, secondPicks);
    return out;
  }

  /**
   * Engine-facing ranking: returns a {@link TurnOutcome.Ranked} whose ordering is the uids of
   * {@code rows} sorted by closeness then latency. Equivalent to calling
   * {@link #rankByDeltaThenLatency} and extracting uids — provided here so phase code can return
   * a {@link TurnOutcome} directly.
   */
  public TurnOutcome.Ranked rankAsOutcome(List<Metric> rows, int correctAnswer) {
    List<Metric> ranked = rankByDeltaThenLatency(rows, correctAnswer);
    List<String> uids = new ArrayList<String>(ranked.size());
    for (Metric m : ranked) uids.add(m.uid);
    return new TurnOutcome.Ranked(Collections.unmodifiableList(uids));
  }
}
