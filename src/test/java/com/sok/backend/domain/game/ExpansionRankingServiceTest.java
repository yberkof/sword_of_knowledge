package com.sok.backend.domain.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExpansionRankingServiceTest {
  private final ExpansionRankingService service = new ExpansionRankingService();

  @Test
  void rankingSortsByErrorThenTimeThenOrder() {
    List<ExpansionRankingService.ExpansionAnswer> rows =
        Arrays.asList(
            new ExpansionRankingService.ExpansionAnswer("u1", 100, 5000, 2),
            new ExpansionRankingService.ExpansionAnswer("u2", 99, 4000, 3),
            new ExpansionRankingService.ExpansionAnswer("u3", 101, 3000, 1));
    List<ExpansionRankingService.ExpansionRankedResult> ranked = service.rankByClosest(rows, 100);
    assertEquals("u1", ranked.get(0).uid());
    assertEquals("u3", ranked.get(1).uid());
    assertEquals("u2", ranked.get(2).uid());
  }
}
