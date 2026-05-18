package com.sok.backend.domain.game;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class QuestionClientMapper {
  public Map<String, Object> toClient(NumericQuestion q, long nowMs, long durationMs) {
    var m = new HashMap<String, Object>();
    m.put("id", q.id); m.put("text", q.text); m.put("serverNowMs", nowMs);
    m.put("phaseEndsAt", nowMs + durationMs); m.put("durationMs", durationMs);
    return m;
  }

  public Map<String, Object> toClient(McqQuestion q, long nowMs, long durationMs) {
    var m = new HashMap<String, Object>();
    m.put("id", q.id); m.put("text", q.text); m.put("options", q.options);
    m.put("category", q.category); m.put("serverNowMs", nowMs);
    m.put("phaseEndsAt", nowMs + durationMs); m.put("durationMs", durationMs);
    return m;
  }
}
