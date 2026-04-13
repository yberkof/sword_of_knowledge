package com.sok.backend.domain.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ExpansionRankingService {
  public static class ExpansionAnswer {
    private final String uid;
    private final int value;
    private final long timeTaken;
    private final int submitOrder;

    public ExpansionAnswer(String uid, int value, long timeTaken, int submitOrder) {
      this.uid = uid;
      this.value = value;
      this.timeTaken = timeTaken;
      this.submitOrder = submitOrder;
    }

    public String uid() { return uid; }
    public int value() { return value; }
    public long timeTaken() { return timeTaken; }
    public int submitOrder() { return submitOrder; }
  }

  public static class ExpansionRankedResult {
    private final String uid;
    private final int rank;
    private final int error;

    public ExpansionRankedResult(String uid, int rank, int error) {
      this.uid = uid;
      this.rank = rank;
      this.error = error;
    }

    public String uid() { return uid; }
    public int rank() { return rank; }
    public int error() { return error; }
  }

  public List<ExpansionRankedResult> rankByClosest(List<ExpansionAnswer> answers, int correctAnswer) {
    List<ExpansionAnswer> sorted = new ArrayList<>(answers);
    sorted.sort(
        Comparator.comparingInt((ExpansionAnswer a) -> Math.abs(a.value() - correctAnswer))
            .thenComparingLong(ExpansionAnswer::timeTaken)
            .thenComparingInt(ExpansionAnswer::submitOrder));
    List<ExpansionRankedResult> out = new ArrayList<>();
    for (int i = 0; i < sorted.size(); i++) {
      ExpansionAnswer row = sorted.get(i);
      out.add(new ExpansionRankedResult(row.uid(), i + 1, Math.abs(row.value() - correctAnswer)));
    }
    return out;
  }
}
