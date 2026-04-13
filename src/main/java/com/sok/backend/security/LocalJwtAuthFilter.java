package com.sok.backend.security;

import com.sok.backend.config.PublicApiPaths;
import com.sok.backend.service.AuthTokenService;
import java.io.IOException;
import java.util.Collections;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class LocalJwtAuthFilter extends OncePerRequestFilter {
  private final AuthTokenService authTokenService;

  public LocalJwtAuthFilter(AuthTokenService authTokenService) {
    this.authTokenService = authTokenService;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return PublicApiPaths.isPublic(request);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      filterChain.doFilter(request, response);
      return;
    }
    try {
      String token = authHeader.substring(7);
      LocalJwtService.LocalClaims claims = authTokenService.verifyAccessToken(token);
      AuthenticatedUser principal = new AuthenticatedUser(claims.userId());
      UsernamePasswordAuthenticationToken auth =
          new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
      auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
      SecurityContextHolder.getContext().setAuthentication(auth);
      filterChain.doFilter(request, response);
    } catch (IllegalStateException ex) {
      unauthorized(response, "Unauthorized");
    }
  }

  private void unauthorized(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write("{\"error\":\"" + message + "\"}");
  }
}
