package com.sok.backend.api;

import com.sok.backend.config.AppInstanceProperties;
import java.util.Collections;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/routing")
public class RoutingController {
  private final AppInstanceProperties instance;

  public RoutingController(AppInstanceProperties instance) {
    this.instance = instance;
  }

  /** Exposes stable instance id for sticky routing / client-side socket host selection. */
  @GetMapping("/instance")
  public Map<String, String> instance() {
    return Collections.singletonMap("instanceId", instance.getId());
  }
}
