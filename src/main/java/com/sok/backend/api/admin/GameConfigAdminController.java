package com.sok.backend.api.admin;

import com.sok.backend.api.ForbiddenException;
import com.sok.backend.security.SecurityUtils;
import com.sok.backend.service.AdminAccessService;
import com.sok.backend.service.config.GameRuntimeConfig;
import com.sok.backend.service.config.RuntimeGameConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/game-config")
public class GameConfigAdminController {
  private final RuntimeGameConfigService configService;
  private final AdminAccessService adminAccessService;

  public GameConfigAdminController(
      RuntimeGameConfigService configService, AdminAccessService adminAccessService) {
    this.configService = configService;
    this.adminAccessService = adminAccessService;
  }

  @GetMapping
  public GameRuntimeConfig get() {
    ensureAdmin(SecurityUtils.currentUid());
    return configService.get();
  }

  @PutMapping
  public GameRuntimeConfig put(@RequestBody GameRuntimeConfig next) {
    ensureAdmin(SecurityUtils.currentUid());
    return configService.update(next);
  }

  private void ensureAdmin(String uid) {
    if (!adminAccessService.isAdmin(uid)) throw new ForbiddenException("forbidden");
  }
}
