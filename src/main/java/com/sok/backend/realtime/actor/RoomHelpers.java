package com.sok.backend.realtime.actor;

import com.sok.backend.realtime.domain.GameState;
import com.sok.backend.service.config.GameRuntimeConfig;

import java.util.*;

/**
 * Shared state transforms and map helpers for room phase handlers.
 */
public final class RoomHelpers {

    /** Client map uses region-01 … region-08 (hex ids 1–8). */
    public static final List<Integer> BASIC_MAP_REGION_IDS = List.of(1, 2, 3, 4, 5, 6, 7, 8);

    private RoomHelpers() {}

    public static Map<String, Object> clientSummary(RoomDeps deps, GameState state) {
        var summary = new HashMap<>(state.toSummary());
        summary.put("mapId", deps.config().get().getDefaultMapId());
        return summary;
    }

    public static GameRuntimeConfig cfg(RoomDeps deps) {
        return deps.config().get();
    }

    public static Map<Integer, GameState.Region> buildBasic1v1Regions(RoomDeps deps) {
        var regions = new HashMap<Integer, GameState.Region>();
        var neighborsCfg = cfg(deps).getNeighbors();
        for (int id : BASIC_MAP_REGION_IDS) {
            List<Integer> neighbors = neighborsCfg.getOrDefault(String.valueOf(id), List.of());
            regions.put(id, new GameState.Region(id, null, false, List.copyOf(neighbors)));
        }
        return regions;
    }

    public static boolean allBasicRegionsOwned(GameState state) {
        return BASIC_MAP_REGION_IDS.stream()
            .map(id -> state.regions().get(id))
            .allMatch(r -> r != null && r.ownerUid() != null);
    }

    public static List<GameState.Region> claimableAdjacentRegions(GameState state, String uid) {
        return BASIC_MAP_REGION_IDS.stream()
            .map(id -> state.regions().get(id))
            .filter(r -> r != null && r.ownerUid() == null && isAdjacentToOwner(state, uid, r.id()))
            .toList();
    }

    public static boolean isAdjacentToOwner(GameState state, String uid, int rid) {
        var reg = state.regions().get(rid);
        if (reg == null || uid == null) return false;
        return reg.neighbors().stream()
            .map(nId -> state.regions().get(nId))
            .anyMatch(n -> n != null && uid.equals(n.ownerUid()));
    }

    public static GameState withPlayers(GameState state, Map<String, GameState.Player> players, String hostUid) {
        return new GameState(
            state.id(), state.phase(), hostUid, state.round(), state.currentTurnIndex(),
            state.phaseStartedAt(), state.phaseEndsAt(), Collections.unmodifiableMap(players),
            state.regions(), state.claimTurnUid(), state.claimQueue(), state.claimPicksLeft(),
            state.estimationAnswers(), state.activeNumericQuestion(), state.activeDuel(), state.sessions()
        );
    }

    public static GameState withEstimation(GameState state, Map<String, GameState.AnswerMetric> answers) {
        return new GameState(
            state.id(), state.phase(), state.hostUid(), state.round(), state.currentTurnIndex(),
            state.phaseStartedAt(), state.phaseEndsAt(), state.players(), state.regions(),
            state.claimTurnUid(), state.claimQueue(), state.claimPicksLeft(),
            Collections.unmodifiableMap(answers), state.activeNumericQuestion(), state.activeDuel(),
            state.sessions()
        );
    }

    public static GameState withDuel(GameState state, GameState.DuelState duel) {
        return new GameState(
            state.id(), state.phase(), state.hostUid(), state.round(), state.currentTurnIndex(),
            state.phaseStartedAt(), state.phaseEndsAt(), state.players(), state.regions(),
            state.claimTurnUid(), state.claimQueue(), state.claimPicksLeft(),
            state.estimationAnswers(), state.activeNumericQuestion(), duel, state.sessions()
        );
    }

    public static GameState.Player copyPlayer(
            GameState.Player p, int hp, int score, Integer castleRegionId, boolean eliminated) {
        return new GameState.Player(p.uid(), p.name(), p.color(), p.avatarUrl(), p.online(),
            hp, score, castleRegionId, eliminated);
    }

    public static void eliminatePlayer(
            Map<String, GameState.Player> players,
            Map<Integer, GameState.Region> regions,
            String eliminatedUid,
            String winnerUid) {

        var elim = players.get(eliminatedUid);
        players.put(eliminatedUid, copyPlayer(elim, 0, elim.score(), elim.castleRegionId(), true));

        int bonus = 0;
        for (var entry : regions.entrySet()) {
            var r = entry.getValue();
            if (eliminatedUid.equals(r.ownerUid())) {
                regions.put(entry.getKey(), new GameState.Region(r.id(), winnerUid, r.isCastle(), r.neighbors()));
                bonus++;
            }
        }
        var win = players.get(winnerUid);
        players.put(winnerUid, copyPlayer(win, win.castleHp(), win.score() + bonus, win.castleRegionId(), false));
    }

    public static List<String> playerOrder(GameState state) {
        return new ArrayList<>(state.players().keySet());
    }

    public static String turnUidAt(GameState state) {
        var order = playerOrder(state);
        if (order.isEmpty()) return null;
        int idx = Math.floorMod(state.currentTurnIndex(), order.size());
        return order.get(idx);
    }

    public static int firstAliveTurnIndex(GameState state) {
        var order = playerOrder(state);
        for (int i = 0; i < order.size(); i++) {
            var p = state.players().get(order.get(i));
            if (p != null && !p.isEliminated()) return i;
        }
        return 0;
    }

    public static int nextAliveTurnIndex(GameState state, int from) {
        var order = playerOrder(state);
        if (order.isEmpty()) return 0;
        for (int i = 1; i <= order.size(); i++) {
            int idx = Math.floorMod(from + i, order.size());
            var p = state.players().get(order.get(idx));
            if (p != null && !p.isEliminated()) return idx;
        }
        return Math.floorMod(from + 1, order.size());
    }
}
