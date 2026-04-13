package com.sok.backend.api;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MiscController {
  @GetMapping("/clans")
  public Map<String, Object> clans() {
    return Collections.<String, Object>singletonMap("clans", Collections.emptyList());
  }

  @PostMapping("/iap/validate")
  public Map<String, Object> iapValidate() {
    Map<String, Object> out = new HashMap<>();
    out.put("ok", true);
    out.put("note", "Validate receipts server-side with store APIs before granting currency.");
    return out;
  }
}
