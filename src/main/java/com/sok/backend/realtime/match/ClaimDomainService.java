package com.sok.backend.realtime.match;

import com.sok.backend.domain.game.ClaimingPhaseService;
import com.sok.backend.service.config.GameRuntimeConfig;
import com.sok.backend.service.config.RuntimeGameConfigService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Core business logic for the claim phase.
 */
@Component
public class ClaimDomainService {

  private final ClaimingPhaseService claimingPhaseService;
  private final RuntimeGameConfigService runtimeConfigService;

  public ClaimDomainService(
      ClaimingPhaseService claimingPhaseService,
      RuntimeGameConfigService runtimeConfigService) {
    this.claimingPhaseService = claimingPhaseService;
    this.runtimeConfigService = runtimeConfigService;
  }

  public List<AnswerMetric> rankEstimationAnswers(RoomState room) {
    if (room.estimationAnswers.isEmpty()) {
      autoFillMissingAnswers(room);
    }

    List<ClaimingPhaseService.Metric> rows = room.estimationAnswers.values().stream()
        .map(metric -> {
          ClaimingPhaseService.Metric m = new ClaimingPhaseService.Metric();
          m.uid = metric.uid;
          m.value = metric.value;
          m.latencyMs = metric.latencyMs;
          return m;
        })
        .collect(Collectors.toList());

    List<ClaimingPhaseService.Metric> rankedRows =
        claimingPhaseService.rankByDeltaThenLatency(rows, room.activeNumericQuestion.answer);

    return rankedRows.stream()
        .map(m -> {
          AnswerMetric out = new AnswerMetric();
          out.uid = m.uid;
          out.value = m.value;
          out.latencyMs = m.latencyMs;
          return out;
        })
        .collect(Collectors.toList());
  }

  public void assignPicks(RoomState room, List<AnswerMetric> ranked) {
    GameRuntimeConfig cfg = runtimeConfigService.get();
    
    List<ClaimingPhaseService.Metric> rows = ranked.stream()
        .map(metric -> {
          ClaimingPhaseService.Metric m = new ClaimingPhaseService.Metric();
          m.uid = metric.uid;
          m.value = metric.value;
          m.latencyMs = metric.latencyMs;
          return m;
        })
        .collect(Collectors.toList());

    room.claimPicksLeftByUid.clear();
    room.claimPicksLeftByUid.putAll(
        claimingPhaseService.assignClaimPicks(
            rows, cfg.getClaimFirstPicks(), cfg.getClaimSecondPicks()));

    room.claimQueue.clear();
    for (AnswerMetric m : ranked) {
      Integer left = room.claimPicksLeftByUid.get(m.uid);
      if (left != null && left > 0) {
        room.claimQueue.add(m.uid);
      }
    }
  }

  public List<Map<String, Object>> buildRankedPayload(List<AnswerMetric> ranked, int correctAnswer) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (int i = 0; i < ranked.size(); i++) {
      AnswerMetric m = ranked.get(i);
      HashMap<String, Object> row = new HashMap<>();
      row.put("uid", m.uid);
      row.put("rank", i + 1);
      row.put("value", m.value);
      row.put("delta", Math.abs(m.value - correctAnswer));
      row.put("latencyMs", m.latencyMs);
      out.add(row);
    }
    return out;
  }

  private void autoFillMissingAnswers(RoomState room) {
    long duration = runtimeConfigService.get().getClaimDurationMs();
    for (PlayerState p : room.players) {
      if (!p.isEliminated && p.online) {
        AnswerMetric m = new AnswerMetric();
        m.uid = p.uid;
        m.value = 0;
        m.latencyMs = duration;
        room.estimationAnswers.put(p.uid, m);
      }
    }
  }
}
