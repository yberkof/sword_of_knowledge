package com.sok.backend.domain.game;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

@Service
public class QuestionEngineService {
  public static class NumericQuestion {
    public String id;
    public String text;
    public int answer;
  }

  public static class McqQuestion {
    public String id;
    public String text;
    public List<String> options;
    public int correctIndex;
    public String category;
  }

  public NumericQuestion nextNumericQuestion() {
    NumericQuestion q = new NumericQuestion();
    int rand = ThreadLocalRandom.current().nextInt(3);
    if (rand == 0) {
      q.id = "num-1453";
      q.text = "In which year did Constantinople fall?";
      q.answer = 1453;
    } else if (rand == 1) {
      q.id = "num-1440";
      q.text = "How many minutes are in one day?";
      q.answer = 1440;
    } else {
      q.id = "num-366";
      q.text = "How many days are in leap year?";
      q.answer = 366;
    }
    return q;
  }

  public McqQuestion nextMcqQuestion(String ignoredCategory) {
    McqQuestion q = new McqQuestion();
    int rand = ThreadLocalRandom.current().nextInt(3);
    if (rand == 0) {
      q.id = "mcq-eg";
      q.text = "Capital of Egypt?";
      q.options = Arrays.asList("Cairo", "Alexandria", "Luxor", "Aswan");
      q.correctIndex = 0;
    } else if (rand == 1) {
      q.id = "mcq-ocean";
      q.text = "Largest ocean?";
      q.options = Arrays.asList("Atlantic", "Indian", "Pacific", "Arctic");
      q.correctIndex = 2;
    } else {
      q.id = "mcq-math";
      q.text = "2 + 2 equals?";
      q.options = Arrays.asList("1", "2", "3", "4");
      q.correctIndex = 3;
    }
    q.category = "general";
    return q;
  }

  public Map<String, Object> toClient(NumericQuestion q, long nowMs, long durationMs) {
    HashMap<String, Object> out = new HashMap<String, Object>();
    out.put("id", q.id);
    out.put("text", q.text);
    out.put("serverNowMs", nowMs);
    out.put("phaseEndsAt", nowMs + durationMs);
    out.put("durationMs", durationMs);
    return out;
  }

  public Map<String, Object> toClient(McqQuestion q, long nowMs, long durationMs) {
    HashMap<String, Object> out = new HashMap<String, Object>();
    out.put("id", q.id);
    out.put("text", q.text);
    out.put("options", q.options);
    out.put("category", q.category);
    out.put("serverNowMs", nowMs);
    out.put("phaseEndsAt", nowMs + durationMs);
    out.put("durationMs", durationMs);
    return out;
  }
}
