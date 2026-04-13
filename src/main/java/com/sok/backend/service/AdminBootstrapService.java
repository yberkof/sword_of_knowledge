package com.sok.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AdminBootstrapService implements InitializingBean {
  private static final Logger log = LoggerFactory.getLogger(AdminBootstrapService.class);
  private final AdminAuthService adminAuthService;
  private final boolean enabled;
  private final String email;
  private final String password;

  public AdminBootstrapService(
      AdminAuthService adminAuthService,
      @Value("${app.admin-bootstrap.enabled:false}") boolean enabled,
      @Value("${app.admin-bootstrap.email:admin@sof.com}") String email,
      @Value("${app.admin-bootstrap.password:}") String password) {
    this.adminAuthService = adminAuthService;
    this.enabled = enabled;
    this.email = email;
    this.password = password;
  }

  @Override
  public void afterPropertiesSet() {
    if (!enabled) return;
    if (password == null || password.trim().isEmpty()) {
      log.warn("Admin bootstrap enabled but password missing.");
      return;
    }
    adminAuthService.bootstrap(email.trim(), password);
    log.warn("Admin bootstrap user ensured for {}", email);
  }
}
