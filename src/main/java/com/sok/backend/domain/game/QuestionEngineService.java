package com.sok.backend.domain.game;

import com.sok.backend.persistence.*;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class QuestionEngineService {
  private final QuestionRepository questionRepo;
  private final NumericQuestionRepository numericRepo;
  private final QuestionFallbackService fallback;
  private final QuestionClientMapper mapper;

  public QuestionEngineService(QuestionRepository q, NumericQuestionRepository n, QuestionFallbackService f, QuestionClientMapper m) {
    this.questionRepo = q; this.numericRepo = n; this.fallback = f; this.mapper = m;
  }

  public NumericQuestion nextNumericQuestion(String category) {
    var row = numericRepo.findRandomActiveByCategory(category).or(numericRepo::findRandomActiveAny);
    if (row.isPresent() && row.get().text() != null && !row.get().text().isBlank()) {
      var q = new NumericQuestion();
      q.id = row.get().id(); q.text = row.get().text().trim(); q.answer = row.get().correctAnswer();
      return q;
    }
    return fallback.nextNumeric();
  }

  public McqQuestion nextMcqQuestion(String category) {
    var row = questionRepo.findRandomActiveByCategory(category).or(questionRepo::findRandomActiveAny);
    if (row.isPresent() && isValidMcq(row.get())) {
      var q = new McqQuestion();
      q.id = row.get().id(); q.text = row.get().text().trim();
      var opts = row.get().options();
      q.options = opts.size() > 4 ? opts.subList(0, 4) : opts;
      q.correctIndex = Math.min(3, Math.max(0, row.get().correctIndex()));
      q.category = row.get().category() != null ? row.get().category() : "";
      return q;
    }
    return fallback.nextMcq();
  }

  private boolean isValidMcq(QuestionRecord r) {
    return r.text() != null && !r.text().isBlank() && r.options() != null && r.options().size() >= 4 && r.correctIndex() >= 0 && r.correctIndex() < r.options().size();
  }

  public Map<String, Object> toClient(NumericQuestion q, long n, long d) { return mapper.toClient(q, n, d); }
  public Map<String, Object> toClient(McqQuestion q, long n, long d) { return mapper.toClient(q, n, d); }
}
