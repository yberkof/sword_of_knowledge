package com.sok.backend.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {

  /**
   * Same origins as {@code app.cors-origins}, plus common Next/Vite dev hosts so a mis-set
   * {@code CORS_ORIGIN} (e.g. only 127.0.0.1:3000) does not block {@code http://localhost:3000}.
   *
   * <p>LAN dev (e.g. phone on {@code http://192.168.x.x:3000}) is allowed when the Origin host is
   * loopback or site-local, matching {@link DevOriginUtil}.
   */
  @Bean
  CorsConfigurationSource corsConfigurationSource(@Value("${app.cors-origins}") String corsOrigins) {
    LinkedHashSet<String> patterns = new LinkedHashSet<>();
    Arrays.stream(corsOrigins.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .forEach(patterns::add);
    patterns.add("http://localhost:3000");
    patterns.add("http://127.0.0.1:3000");
    patterns.add("http://localhost:5173");
    patterns.add("http://127.0.0.1:5173");
    patterns.add("https://71a7-91-186-250-83.ngrok-free.app");

    final ArrayList<String> patternList = new ArrayList<>(patterns);

    return new CorsConfigurationSource() {
      @Override
      public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedMethods(
            Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
        config.setAllowedHeaders(Collections.singletonList("*"));
        config.setExposedHeaders(
            Arrays.asList("Authorization", "Content-Type", "X-Requested-With"));
        config.setMaxAge(3600L);

        String origin = request.getHeader("Origin");
        if (StringUtils.hasText(origin) && DevOriginUtil.isLocalPrivateDevOrigin(origin)) {
          config.setAllowedOrigins(Collections.singletonList(origin));
        } else {
          config.setAllowedOriginPatterns(patternList);
        }
        return config;
      }
    };
  }

  /**
   * Servlet-container order: must run before {@link org.springframework.security.web.FilterChainProxy}
   * so OPTIONS preflight gets CORS headers instead of a bare 401 from Spring Security.
   */
  @Bean
  FilterRegistrationBean<CorsFilter> corsFilterRegistration(CorsConfigurationSource corsConfigurationSource) {
    FilterRegistrationBean<CorsFilter> reg = new FilterRegistrationBean<>(new CorsFilter(corsConfigurationSource));
    reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
    reg.addUrlPatterns("/*");
    return reg;
  }
}
