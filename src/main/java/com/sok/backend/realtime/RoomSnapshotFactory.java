package com.sok.backend.realtime;

import com.sok.backend.realtime.model.*;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class RoomSnapshotFactory {
    public Map<String, Object> roomToClient(RoomState r) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", r.id); m.put("phase", r.phase); m.put("round", r.round);
        m.put("players", r.players.stream().map(this::playerToClient).collect(Collectors.toList()));
        m.put("mapState", r.regions.values().stream().map(this::regionToClient).collect(Collectors.toList()));
        m.put("scoreByUid", r.scoreByUid);
        m.put("phaseEndsAt", r.phaseEndsAt);
        m.put("claimTurnUid", r.claimTurnUid);
        m.put("claimPicksLeftByUid", r.claimPicksLeftByUid);
        m.put("currentTurnIndex", r.currentTurnIndex);
        if (r.activeDuel != null) m.put("activeDuel", Map.of("attackerUid", r.activeDuel.attackerUid, "defenderUid", r.activeDuel.defenderUid, "targetHexId", r.activeDuel.targetRegionId));
        return m;
    }

    public Map<String, Object> playerToClient(PlayerState p) {
        Map<String, Object> m = new HashMap<>();
        m.put("uid", p.uid); m.put("name", p.name); m.put("avatarUrl", p.avatarUrl);
        m.put("color", p.color); m.put("score", p.score); m.put("online", p.online);
        m.put("hp", p.castleHp); m.put("castleHp", p.castleHp);
        m.put("isEliminated", p.isEliminated); m.put("teamId", p.teamId);
        return m;
    }

    public Map<String, Object> regionToClient(RegionState r) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", r.id); m.put("ownerUid", r.ownerUid);
        m.put("isCapital", r.isCastle); m.put("neighbors", r.neighbors);
        return m;
    }

    public Map<String, Object> rankingsPayload(List<AnswerMetric> ranked, Map<String, Integer> claimPicks, int correct) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            AnswerMetric m = ranked.get(i);
            list.add(Map.of(
                "uid", m.uid,
                "rank", i + 1,
                "value", m.value,
                "delta", Math.abs(m.value - correct),
                "latencyMs", m.latencyMs
            ));
        }
        return Map.of("rankings", list, "correctAnswer", correct, "claimPicks", claimPicks);
    }

    /** Pick-phase handoff — full rankings live in {@code answer_event}. */
    public Map<String, Object> claimPickTransitionPayload(
            String claimTurnUid, Map<String, Integer> claimPicks, int correctAnswer) {
        Map<String, Object> m = new HashMap<>();
        m.put("claimTurnUid", claimTurnUid);
        m.put("claimPicks", claimPicks);
        m.put("correctAnswer", correctAnswer);
        return m;
    }

    public Map<String, Object> mcqDuelPayload(DuelState d, long now, int dur, Map<String, Object> q) {
        var p = new HashMap<>(q);
        p.put("attackerUid", d.attackerUid); p.put("defenderUid", d.defenderUid); p.put("targetHexId", d.targetRegionId);
        return p;
    }
}
