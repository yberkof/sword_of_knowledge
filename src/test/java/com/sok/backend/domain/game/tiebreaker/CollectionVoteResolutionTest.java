package com.sok.backend.domain.game.tiebreaker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import org.junit.jupiter.api.Test;

class CollectionVoteResolutionTest {

  @Test
  void sameVoteReturnsThatGame() {
    assertThat(CollectionTieBreakService.resolveVote(
            CollectionMinigameIds.RHYTHM, CollectionMinigameIds.RHYTHM, new Random(1)))
        .isEqualTo(CollectionMinigameIds.RHYTHM);
  }

  @Test
  void disagreeIsOneOfTwo() {
    Random rnd = new Random(42);
    for (int i = 0; i < 30; i++) {
      String r =
          CollectionTieBreakService.resolveVote(
              CollectionMinigameIds.RPS, CollectionMinigameIds.MEMORY, rnd);
      assertThat(r).isIn(CollectionMinigameIds.RPS, CollectionMinigameIds.MEMORY);
    }
  }

  @Test
  void bothNullUsesCatalog() {
    String r = CollectionTieBreakService.resolveVote(null, null, new Random(0));
    assertThat(CollectionMinigameIds.VOTE_OPTIONS).contains(r);
  }
}
