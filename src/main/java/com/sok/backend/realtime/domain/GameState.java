package com.sok.backend.realtime.domain;

import com.sok.backend.domain.game.NumericQuestion;
import com.sok.backend.domain.game.McqQuestion;
import com.sok.backend.realtime.actor.RoomCommand;
import org.apache.pekko.actor.typed.ActorRef;

import java.io.Serializable;
import java.util.*;

/**
 * Purely immutable representation of a Game Match.
 */
public record GameState(
    String id,
    String phase,
    String hostUid,
    int round,
    int currentTurnIndex,
    long phaseStartedAt,
    Long phaseEndsAt,
    Map<String, Player> players,
    Map<Integer, Region> regions,
    String claimTurnUid,
    List<String> claimQueue,
    Map<String, Integer> claimPicksLeft,
    Map<String, AnswerMetric> estimationAnswers,
    NumericQuestion activeNumericQuestion,
    DuelState activeDuel,
    Map<String, ActorRef<RoomCommand.RoomEvent>> sessions
) implements Serializable {

    public static GameState empty(String id) {
        return new GameState(
            id, "waiting", null, 1, 0, System.currentTimeMillis(), null,
            Map.of(), Map.of(), null, List.of(), Map.of(), Map.of(), null, null, Map.of()
        );
    }

    public GameState withSession(String uid, ActorRef<RoomCommand.RoomEvent> ref) {
        var next = new HashMap<>(sessions);
        next.put(uid, ref);
        return withSessions(Collections.unmodifiableMap(next));
    }

    public GameState withoutSession(String uid) {
        var next = new HashMap<>(sessions);
        next.remove(uid);
        return withSessions(Collections.unmodifiableMap(next));
    }

    public GameState withSessions(Map<String, ActorRef<RoomCommand.RoomEvent>> newSessions) {
        return new GameState(
            id, phase, hostUid, round, currentTurnIndex, phaseStartedAt, phaseEndsAt,
            players, regions, claimTurnUid, claimQueue, claimPicksLeft,
            estimationAnswers, activeNumericQuestion, activeDuel, newSessions
        );
    }

    public record Player(
        String uid,
        String name,
        String color,
        String avatarUrl,
        boolean online,
        int castleHp,
        int score,
        Integer castleRegionId,
        boolean isEliminated
    ) implements Serializable {}

    public record Region(
        int id,
        String ownerUid,
        boolean isCastle,
        List<Integer> neighbors
    ) implements Serializable {}

    public record AnswerMetric(
        String uid,
        int value,
        long latencyMs
    ) implements Serializable {}

    public record DuelState(
        String attackerUid,
        String defenderUid,
        int targetRegionId,
        McqQuestion mcqQuestion,
        Map<String, DuelAnswer> answers
    ) implements Serializable {}

    public record DuelAnswer(
        int answerIndex,
        long timeTaken
    ) implements Serializable {}

    /**
     * Converts immutable state to the Map format expected by legacy RoomSnapshotFactory/Frontend.
     */
    public Map<String, Object> toSummary() {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("phase", phase);
        m.put("round", round);
        m.put("battleRound", round);
        m.put("currentTurnIndex", currentTurnIndex);
        m.put("hostUid", hostUid);
        m.put("phaseEndsAt", phaseEndsAt);
        m.put("claimTurnUid", claimTurnUid);
        m.put("claimPicksLeftByUid", claimPicksLeft);

        Map<String, Integer> scoreByUid = new HashMap<>();
        players.values().forEach(p -> scoreByUid.put(p.uid(), p.score()));
        m.put("scoreByUid", scoreByUid);

        if (activeDuel != null) {
            Map<String, Object> duel = new HashMap<>();
            duel.put("attackerUid", activeDuel.attackerUid());
            duel.put("defenderUid", activeDuel.defenderUid());
            duel.put("targetHexId", activeDuel.targetRegionId());
            m.put("activeDuel", duel);
        }

        m.put("players", players.values().stream().map(p -> {
            Map<String, Object> pm = new HashMap<>();
            pm.put("uid", p.uid); pm.put("name", p.name); pm.put("avatarUrl", p.avatarUrl);
            pm.put("color", p.color); pm.put("score", p.score); pm.put("online", p.online);
            pm.put("hp", p.castleHp); pm.put("isEliminated", p.isEliminated);
            return pm;
        }).toList());

        m.put("mapState", regions.values().stream().map(r -> {
            Map<String, Object> rm = new HashMap<>();
            rm.put("id", r.id); rm.put("ownerUid", r.ownerUid);
            rm.put("isCapital", r.isCastle); rm.put("neighbors", r.neighbors);
            return rm;
        }).toList());

        return m;
    }
}
