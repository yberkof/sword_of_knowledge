package com.sok.backend.domain.game.tiebreaker;

import com.sok.backend.realtime.TieBreakMinigameScheduler;
import com.sok.backend.realtime.match.DuelState;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * Lobby phase for {@link TieBreakerModeIds#MINIGAME_COLLECTION}: collects votes then starts one
 * sub-minigame.
 */
@Service
public class CollectionTieBreakService {

  private static final Logger log = LoggerFactory.getLogger(CollectionTieBreakService.class);

  /** Timer key for vote deadline. */
  public static final String COLLECTION_PICK_TIMER_KEY = "collection_pick";

  private final AvoidBombsMinigameStarter avoidBombsMinigameStarter;
  private final RpsTieBreakInteractionService rpsTieBreakInteractionService;
  private final RhythmTieBreakInteractionService rhythmTieBreakInteractionService;
  private final MemoryTieBreakInteractionService memoryTieBreakInteractionService;
  private final TieBreakMinigameScheduler tieBreakMinigameScheduler;
  private final Random random = new Random();

  public CollectionTieBreakService(
      AvoidBombsMinigameStarter avoidBombsMinigameStarter,
      RpsTieBreakInteractionService rpsTieBreakInteractionService,
      RhythmTieBreakInteractionService rhythmTieBreakInteractionService,
      MemoryTieBreakInteractionService memoryTieBreakInteractionService,
      @Lazy TieBreakMinigameScheduler tieBreakMinigameScheduler) {
    this.avoidBombsMinigameStarter = avoidBombsMinigameStarter;
    this.rpsTieBreakInteractionService = rpsTieBreakInteractionService;
    this.rhythmTieBreakInteractionService = rhythmTieBreakInteractionService;
    this.memoryTieBreakInteractionService = memoryTieBreakInteractionService;
    this.tieBreakMinigameScheduler = tieBreakMinigameScheduler;
  }

  /** Initializes collection lobby fields and emits {@code tiebreaker_collection_start}. */
  public void beginLobby(DuelState duel, TieBreakerRealtimeBridge bridge) {
    duel.tiebreakKind = "collection";
    duel.collectionSubPhase = "pick";
    duel.collectionAttackerPick = null;
    duel.collectionDefenderPick = null;
    long pickMs = Math.max(3000L, bridge.configuration().getCollectionPickMs());
    long now = System.currentTimeMillis();
    duel.collectionPickDeadlineAtMs = now + pickMs;
    bridge.emitToRoom(
        "tiebreaker_collection_start",
        CollectionTieBreakPayloadFactory.collectionStartPayload(
            bridge.roomId(), duel, pickMs, now));

    bridge.scheduleRoomTimer(
        COLLECTION_PICK_TIMER_KEY,
        pickMs + 80L,
        () -> {
          try {
            onPickDeadline(duel, bridge);
          } catch (RuntimeException ex) {
            log.error("sok collection pick timer err room={}", bridge.roomId(), ex);
          }
        });
  }

  /**
   * Records one player's vote. When both have chosen, cancels the pick timer and launches the
   * resolved minigame.
   *
   * @return true when both picks were already present after this call and transition ran.
   */
  public boolean submitPick(DuelState duel, String uid, String choice, TieBreakerRealtimeBridge bridge) {
    if (duel == null || !"collection".equals(duel.tiebreakKind) || !"pick".equals(duel.collectionSubPhase)) {
      return false;
    }
    if (!CollectionMinigameIds.isValidChoice(choice)) {
      return false;
    }
    if (!uid.equals(duel.attackerUid) && !uid.equals(duel.defenderUid)) {
      return false;
    }
    if (uid.equals(duel.attackerUid)) {
      if (duel.collectionAttackerPick != null) return false;
      duel.collectionAttackerPick = choice;
    } else {
      if (duel.collectionDefenderPick != null) return false;
      duel.collectionDefenderPick = choice;
    }
    if (duel.collectionAttackerPick != null && duel.collectionDefenderPick != null) {
      bridge.cancelRoomTimer(COLLECTION_PICK_TIMER_KEY);
      resolveAndLaunch(duel, bridge);
      return true;
    }
    return false;
  }

  public void onPickDeadline(DuelState duel, TieBreakerRealtimeBridge bridge) {
    if (duel == null || !"collection".equals(duel.tiebreakKind) || !"pick".equals(duel.collectionSubPhase)) {
      return;
    }
    if (duel.collectionAttackerPick == null) {
      duel.collectionAttackerPick = randomCatalogChoice();
    }
    if (duel.collectionDefenderPick == null) {
      duel.collectionDefenderPick = randomCatalogChoice();
    }
    resolveAndLaunch(duel, bridge);
  }

  private void resolveAndLaunch(DuelState duel, TieBreakerRealtimeBridge bridge) {
    String resolved =
        resolveVote(
            duel.collectionAttackerPick, duel.collectionDefenderPick, random);
    bridge.emitToRoom(
        "tiebreaker_collection_resolved",
        CollectionTieBreakPayloadFactory.collectionResolvedPayload(
            bridge.roomId(), duel, resolved));
    clearCollectionLobby(duel);
    startResolvedMinigame(duel, bridge, resolved);
  }

  static String resolveVote(String attackerPick, String defenderPick, Random rnd) {
    String a = attackerPick;
    String d = defenderPick;
    if (a == null && d == null) {
      int i = rnd.nextInt(CollectionMinigameIds.VOTE_OPTIONS.size());
      return CollectionMinigameIds.VOTE_OPTIONS.get(i);
    }
    if (a == null) return d;
    if (d == null) return a;
    if (a.equals(d)) return a;
    return rnd.nextBoolean() ? a : d;
  }

  private String randomCatalogChoice() {
    return CollectionMinigameIds.VOTE_OPTIONS.get(
        random.nextInt(CollectionMinigameIds.VOTE_OPTIONS.size()));
  }

  private static void clearCollectionLobby(DuelState duel) {
    duel.collectionSubPhase = null;
    duel.collectionPickDeadlineAtMs = null;
  }

  public void startResolvedMinigame(DuelState duel, TieBreakerRealtimeBridge bridge, String resolved) {
    switch (resolved) {
      case CollectionMinigameIds.AVOID_BOMBS:
        avoidBombsMinigameStarter.start(duel, bridge);
        break;
      case CollectionMinigameIds.RPS:
        rpsTieBreakInteractionService.startMatch(duel, bridge);
        break;
      case CollectionMinigameIds.RHYTHM:
        rhythmTieBreakInteractionService.startMatch(duel, bridge);
        tieBreakMinigameScheduler.scheduleRhythmRoundDeadline(bridge.roomId());
        break;
      case CollectionMinigameIds.MEMORY:
        memoryTieBreakInteractionService.startMatch(duel, bridge);
        tieBreakMinigameScheduler.scheduleMemoryPeekEnd(bridge.roomId());
        break;
      default:
        avoidBombsMinigameStarter.start(duel, bridge);
    }
  }

}
