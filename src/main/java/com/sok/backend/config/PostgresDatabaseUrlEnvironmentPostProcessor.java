package com.sok.backend.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Accepts {@code DATABASE_URL} in libpq form ({@code postgresql://user:pass@host:port/db}) and maps it
 * to JDBC properties so Spring Boot can create a {@link javax.sql.DataSource}.
 */
public class PostgresDatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

  private static final String SOURCE = "postgresDatabaseUrl";
  private static final String DATABASE_URL = "DATABASE_URL";
  private static final String SPRING_DATASOURCE_URL = "spring.datasource.url";
  private static final String SPRING_DATASOURCE_USERNAME = "spring.datasource.username";
  private static final String SPRING_DATASOURCE_PASSWORD = "spring.datasource.password";

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
    String raw = environment.getProperty(DATABASE_URL);
    if (raw == null || raw.trim().isEmpty()) {
      return;
    }
    String trimmed = raw.trim();
    if (trimmed.startsWith("jdbc:")) {
      return;
    }
    if (!trimmed.startsWith("postgresql://") && !trimmed.startsWith("postgres://")) {
      return;
    }
    try {
      environment.getPropertySources().addFirst(new MapPropertySource(SOURCE, toJdbcProperties(trimmed)));
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Invalid DATABASE_URL: " + raw, e);
    }
  }

  private static Map<String, Object> toJdbcProperties(String trimmed) throws URISyntaxException {
    URI uri = new URI(trimmed);
    String host = uri.getHost();
    if (host == null || host.isEmpty()) {
      throw new IllegalArgumentException("DATABASE_URL missing host");
    }
    int port = uri.getPort() > 0 ? uri.getPort() : 5432;
    String path = uri.getPath();
    if (path == null || path.isEmpty() || "/".equals(path)) {
      throw new IllegalArgumentException("DATABASE_URL missing database name in path");
    }
    String database = path.startsWith("/") ? path.substring(1) : path;
    String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database;

    Map<String, Object> map = new HashMap<>();
    map.put(DATABASE_URL, jdbcUrl);
    map.put(SPRING_DATASOURCE_URL, jdbcUrl);

    String userInfo = uri.getUserInfo();
    if (userInfo != null && !userInfo.isEmpty()) {
      String user;
      String password = "";
      int colon = userInfo.indexOf(':');
      if (colon >= 0) {
        user = userInfo.substring(0, colon);
        password = userInfo.substring(colon + 1);
      } else {
        user = userInfo;
      }
      map.put(SPRING_DATASOURCE_USERNAME, user);
      map.put(SPRING_DATASOURCE_PASSWORD, password);
    }
    return map;
  }
}
