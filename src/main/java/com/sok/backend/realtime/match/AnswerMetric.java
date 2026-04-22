package com.sok.backend.realtime.match;

/**
 * Numeric estimation answer with submission latency (claiming or tie-break rounds).
 */
public class AnswerMetric {
  public String uid;
  public int value;
  public long latencyMs;
}
