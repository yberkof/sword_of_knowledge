package com.sok.backend.api;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cms")
public class CmsController {
  @GetMapping("/questions-stats")
  public Map<String, Integer> questionsStats() {
    return Collections.singletonMap("count", -1);
  }

  @PostMapping("/report")
  public Map<String, Object> report(@RequestBody(required = false) Map<String, Object> body) {
    Object reporterUid = body == null ? null : body.get("reporterUid");
    Object reason = body == null ? null : body.get("reason");
    if (!(reporterUid instanceof String) || ((String) reporterUid).trim().isEmpty()) {
      throw new BadRequestException(Collections.singletonMap("ok", false));
    }
    if (!(reason instanceof String) || ((String) reason).trim().isEmpty()) {
      throw new BadRequestException(Collections.singletonMap("ok", false));
    }
    Map<String, Object> out = new HashMap<>();
    out.put("ok", true);
    out.put("queued", true);
    return out;
  }
}
