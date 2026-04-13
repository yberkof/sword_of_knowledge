package com.sok.backend.config;

import com.sok.backend.security.LocalJwtAuthFilter;
import javax.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
  /**
   * Fully skips Spring Security (and JWT filter) for public routes. Fixes cases where
   * {@code antMatchers(PERMIT_ALL)} / {@code requestMatchers(OrRequestMatcher)} still enforced
   * {@code authenticated()} (401 with {@code Unauthorized} from entry point).
   */
  @Bean
  WebSecurityCustomizer publicPathsIgnoredBySpringSecurity() {
    return web -> web.ignoring().requestMatchers(PublicApiPaths.permitAllMatchersVarargs());
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, LocalJwtAuthFilter localJwtAuthFilter)
      throws Exception {
    http.csrf().disable();
    http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
    http.authorizeHttpRequests(
        authz ->
            authz
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
}
