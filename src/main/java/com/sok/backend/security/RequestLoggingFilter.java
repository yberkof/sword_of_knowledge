package com.sok.backend.security;

import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Logs every HTTP request metadata including method, URI, status, and duration.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    long startTime = System.currentTimeMillis();
    String method = request.getMethod();
    String uri = request.getRequestURI();
    String query = request.getQueryString();
    String fullUri = (query != null) ? uri + "?" + query : uri;
    String remoteAddr = request.getRemoteAddr();

    try {
      filterChain.doFilter(request, response);
    } finally {
      long duration = System.currentTimeMillis() - startTime;
      int status = response.getStatus();
      
      String userId = "anonymous";
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser) {
        userId = ((AuthenticatedUser) auth.getPrincipal()).uid();
      }

      log.info("API Request: {} {} | Status: {} | User: {} | IP: {} | Duration: {}ms",
          method, fullUri, status, userId, remoteAddr, duration);
    }
  }
}
