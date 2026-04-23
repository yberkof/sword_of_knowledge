package com.sok.backend.config;

import com.sok.backend.security.LocalJwtAuthFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, LocalJwtAuthFilter localJwtAuthFilter)
      throws Exception {
    http.csrf().disable();
    http.cors();
    http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
    http.authorizeHttpRequests(
        authz ->
            authz
                .requestMatchers(new AntPathRequestMatcher("/**", "OPTIONS"))
                .permitAll()
                .requestMatchers(PublicApiPaths.permitAllMatcher())
                .permitAll()
                .anyRequest()
                .authenticated());
    http.exceptionHandling()
        .authenticationEntryPoint(jsonAuthenticationEntryPoint())
        .accessDeniedHandler(jsonAccessDeniedHandler());
    http.addFilterBefore(localJwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  AuthenticationEntryPoint jsonAuthenticationEntryPoint() {
    return (request, response, authException) -> {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setCharacterEncoding("UTF-8");
      response.getWriter().write("{\"error\":\"Unauthorized\"}");
    };
  }

  @Bean
  AccessDeniedHandler jsonAccessDeniedHandler() {
    return (request, response, accessDeniedException) -> {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setCharacterEncoding("UTF-8");
      response.getWriter().write("{\"error\":\"Forbidden\"}");
    };
  }

  @Bean
  BCryptPasswordEncoder bCryptPasswordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /**
   * CORS for the HTTP API. Reads {@code app.cors-origins} (CSV). Special sentinel {@code *}
   * enables a permissive local-dev mode using origin patterns with credentials, intended for
   * {@code CORS_ORIGIN=*} set in a developer's shell — never commit that value.
   */
  @Bean
  CorsConfigurationSource corsConfigurationSource(
      @Value("${app.cors-origins:*}")
          String corsOriginsCsv) {
    CorsConfiguration cfg = new CorsConfiguration();
    List<String> origins = parseCsv(corsOriginsCsv);
    if (origins.contains("*")) {
      cfg.setAllowedOriginPatterns(Collections.singletonList("*"));
    } else {
      cfg.setAllowedOrigins(origins);
    }
    cfg.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
    cfg.setAllowedHeaders(Collections.singletonList("*"));
    cfg.setExposedHeaders(Arrays.asList("Authorization", "Content-Disposition"));
    cfg.setAllowCredentials(true);
    cfg.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cfg);
    return source;
  }

  private static List<String> parseCsv(String csv) {
    LinkedHashSet<String> out = new LinkedHashSet<String>();
    if (csv != null) {
      for (String s : csv.split(",")) {
        String t = s.trim();
        if (!t.isEmpty()) out.add(t);
      }
    }
    return new ArrayList<String>(out);
  }
}
