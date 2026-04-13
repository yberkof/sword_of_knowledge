package com.sok.backend.config;

import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Single source of truth for HTTP routes that skip JWT parsing and are permitAll in security.
 */
public final class PublicApiPaths {
  private PublicApiPaths() {}

  public static final String[] PERMIT_ALL =
      new String[] {
        "/api/health",
        "/api/cms/questions-stats",
        "/api/cms/report",
        "/api/clans",
        "/api/iap/validate",
        "/api/routing/**",
        "/api/auth/**",
        "/api/admin/auth/**",
        "/api/admin/catalog/**",
        "/api/payments/webhook/stripe",
        "/admin/catalog-ui",
        "/error",
        "/actuator/**"
      };

  private static final AntPathRequestMatcher[] MATCHERS = buildMatchers();

  private static AntPathRequestMatcher[] buildMatchers() {
    AntPathRequestMatcher[] out = new AntPathRequestMatcher[PERMIT_ALL.length];
    for (int i = 0; i < PERMIT_ALL.length; i++) {
      out[i] = new AntPathRequestMatcher(PERMIT_ALL[i]);
    }
    return out;
  }

  private static final RequestMatcher PUBLIC_OR = new OrRequestMatcher(asMatcherList(MATCHERS));

  private static List<RequestMatcher> asMatcherList(AntPathRequestMatcher[] matchers) {
    List<RequestMatcher> list = new ArrayList<>();
    for (AntPathRequestMatcher m : matchers) {
      list.add(m);
    }
    return list;
  }

  /** Same matcher used for JWT skip + {@link SecurityConfig} permitAll. */
  public static RequestMatcher permitAllMatcher() {
    return PUBLIC_OR;
  }

  /** For {@code web.ignoring().requestMatchers(...)} — avoids single-OR edge cases in some setups. */
  public static RequestMatcher[] permitAllMatchersVarargs() {
    RequestMatcher[] r = new RequestMatcher[MATCHERS.length];
    System.arraycopy(MATCHERS, 0, r, 0, MATCHERS.length);
    return r;
  }

  public static boolean isPublic(HttpServletRequest request) {
    return PUBLIC_OR.matches(request);
  }
}
