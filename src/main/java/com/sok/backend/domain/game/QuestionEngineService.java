package com.sok.backend.domain.game;

import com.sok.backend.persistence.NumericQuestionRecord;
import com.sok.backend.persistence.NumericQuestionRepository;
import com.sok.backend.persistence.QuestionRecord;
import com.sok.backend.persistence.QuestionRepository;
import com.sok.backend.persistence.UserQuestionHistoryRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

@Service
public class QuestionEngineService {
  private final QuestionRepository questionRepository;
  private final NumericQuestionRepository numericQuestionRepository;
  private final UserQuestionHistoryRepository historyRepository;

  public QuestionEngineService(
      QuestionRepository questionRepository,
      NumericQuestionRepository numericQuestionRepository,
      UserQuestionHistoryRepository historyRepository) {
    this.questionRepository = questionRepository;
    this.numericQuestionRepository = numericQuestionRepository;
    this.historyRepository = historyRepository;
  }

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

  /**
   * Estimation question for claiming rounds and numeric tie-breakers. Loads from {@code
   * sword_of_knowledge.numeric_questions}; falls back to built-in samples when empty.
   */
  public NumericQuestion nextNumericQuestion(String category, List<String> excludeUserIds) {
    Optional<NumericQuestionRecord> row = Optional.empty();
    if (excludeUserIds != null && !excludeUserIds.isEmpty()) {
      row =
          numericQuestionRepository.findRandomActiveByCategoryForUser(
              category, excludeUserIds.get(0));
    }
    if (row.isEmpty()) {
      row = numericQuestionRepository.findRandomActiveByCategory(category);
    }
    if (row.isEmpty()) {
      row = numericQuestionRepository.findRandomActiveAny();
    }
    if (row.isPresent() && isValidNumeric(row.get())) {
      return fromNumericRecord(row.get());
    }
    return nextNumericQuestionFallback();
  }

  public NumericQuestion nextNumericQuestion(String category) {
    return nextNumericQuestion(category, null);
  }

  private static boolean isValidNumeric(NumericQuestionRecord r) {
    return r.text() != null && !r.text().trim().isEmpty();
  }

  private static NumericQuestion fromNumericRecord(NumericQuestionRecord r) {
    NumericQuestion q = new NumericQuestion();
    q.id = r.id();
    q.text = r.text().trim();
    q.answer = r.correctAnswer();
    return q;
  }

  private NumericQuestion nextNumericQuestionFallback() {
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

  /**
   * Loads a random MCQ from {@code sword_of_knowledge.questions}. Falls back to built-in samples
   * only when the table has no usable rows.
   */
  public McqQuestion nextMcqQuestion(String category, List<String> excludeUserIds) {
    Optional<QuestionRecord> row = Optional.empty();
    if (excludeUserIds != null && !excludeUserIds.isEmpty()) {
      row = questionRepository.findRandomActiveByCategoryForUser(category, excludeUserIds.get(0));
    }
    if (row.isEmpty()) {
      row = questionRepository.findRandomActiveByCategory(category);
    }
    if (row.isEmpty()) {
      row = questionRepository.findRandomActiveAny();
    }
    if (row.isPresent() && isValidMcq(row.get())) {
      return fromRecord(row.get());
    }
    return nextMcqQuestionFallback();
  }

  public McqQuestion nextMcqQuestion(String category) {
    return nextMcqQuestion(category, null);
  }

  public void recordQuestionSeen(List<String> userIds, String questionId, String type) {
    if (userIds == null || questionId == null || type == null) return;
    for (String uid : userIds) {
      if ("neutral".equals(uid)) continue;
      historyRepository.recordQuestion(uid, questionId, type);
    }
  }

  private static boolean isValidMcq(QuestionRecord r) {
    if (r.text() == null || r.text().trim().isEmpty()) {
      return false;
    }
    List<String> opts = r.options();
    if (opts == null || opts.size() < 4) {
      return false;
    }
    int idx = r.correctIndex();
    return idx >= 0 && idx < opts.size();
  }

  private static McqQuestion fromRecord(QuestionRecord r) {
    McqQuestion q = new McqQuestion();
    q.id = r.id();
    q.text = r.text().trim();
    List<String> opts = r.options();
    q.options = opts.size() > 4 ? opts.subList(0, 4) : opts;
    q.correctIndex = Math.min(3, Math.max(0, r.correctIndex()));
    q.category = r.category() != null ? r.category() : "";
    return q;
  }

  private McqQuestion nextMcqQuestionFallback() {
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
