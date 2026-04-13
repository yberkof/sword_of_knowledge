package com.sok.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.instance")
public class AppInstanceProperties {
  /** Logical pod id for room routing / Redis registry (e.g. k8s pod name). */
  private String id = "local-1";

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }
}
