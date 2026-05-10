package com.sok.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.openai")
public class AiConfigProperties {
  private String apiKey = "";
  private String model = "gpt-4o";

  public String getApiKey() { return apiKey; }
  public void setApiKey(String apiKey) { this.apiKey = apiKey; }
  public String getModel() { return model; }
  public void setModel(String model) { this.model = model; }
}
