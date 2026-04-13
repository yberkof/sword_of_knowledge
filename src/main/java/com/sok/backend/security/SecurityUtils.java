package com.sok.backend.security;

import com.sok.backend.api.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {
  private SecurityUtils() {}

  public static String currentUid() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser)) {
      throw new UnauthorizedException("unauthorized");
    }
    AuthenticatedUser user = (AuthenticatedUser) auth.getPrincipal();
    return user.uid();
  }
}
