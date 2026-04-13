package com.sok.backend;

import com.sok.backend.config.AppInstanceProperties;
import com.sok.backend.config.RealtimeScaleProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({RealtimeScaleProperties.class, AppInstanceProperties.class})
public class SpringBackendApplication {
  public static void main(String[] args) {
    SpringApplication.run(SpringBackendApplication.class, args);
  }
}
