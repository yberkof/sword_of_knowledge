package com.sok.backend.service;

import com.sok.backend.api.PasswordChangeRequiredException;
import com.sok.backend.api.UnauthorizedException;
import com.sok.backend.persistence.AdminAccountRecord;
import com.sok.backend.persistence.AdminAccountRepository;
import com.sok.backend.persistence.AdminSessionRecord;
import com.sok.backend.persistence.AdminSessionRepository;
import com.sok.backend.security.RefreshTokenHasher;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuthService {
  private final AdminAccountRepository adminAccountRepository;
  private final AdminSessionRepository adminSessionRepository;
  private final BCryptPasswordEncoder passwordEncoder;
  private final RefreshTokenHasher tokenHasher;
  private final long sessionTtlSeconds;

  public AdminAuthService(
      AdminAccountRepository adminAccountRepository,
      AdminSessionRepository adminSessionRepository,
      BCryptPasswordEncoder passwordEncoder,
      RefreshTokenHasher tokenHasher,
      @Value("${app.admin-session-ttl-seconds:28800}") long sessionTtlSeconds) {
    this.adminAccountRepository = adminAccountRepository;
    this.adminSessionRepository = adminSessionRepository;
    this.passwordEncoder = passwordEncoder;
    this.tokenHasher = tokenHasher;
    this.sessionTtlSeconds = sessionTtlSeconds;
  }

  @Transactional
  public void bootstrap(String email, String plainPassword) {
    if (email == null || email.trim().isEmpty() || plainPassword == null || plainPassword.trim().isEmpty()) {
      return;
    }
    String normalized = email.trim().toLowerCase();
    if (adminAccountRepository.findByEmail(normalized).isPresent()) {
      return;
    }
    String hash = passwordEncoder.encode(plainPassword);
    adminAccountRepository.upsertBootstrap(normalized, hash);
  }

  @Transactional
  public LoginResult login(String email, String password) {
    if (email == null || password == null) throw new UnauthorizedException("unauthorized");
    Optional<AdminAccountRecord> row = adminAccountRepository.findByEmail(email.trim().toLowerCase());
    if (!row.isPresent() || !row.get().active()) throw new UnauthorizedException("unauthorized");
    AdminAccountRecord account = row.get();
    if (!passwordEncoder.matches(password, account.passwordHash())) throw new UnauthorizedException("unauthorized");
    String raw = UUID.randomUUID().toString() + "." + UUID.randomUUID().toString();
    String hash = tokenHasher.sha256(raw);
    AdminSessionRecord session =
        adminSessionRepository.create(account.id(), hash, OffsetDateTime.now().plusSeconds(sessionTtlSeconds));
    return new LoginResult(raw, account.mustChangePassword(), account.email(), session.id().toString());
  }

  @Transactional
  public void changePassword(String sessionToken, String newPassword) {
    AdminAccountRecord account = requireAccount(sessionToken, false);
    String hash = passwordEncoder.encode(newPassword);
    adminAccountRepository.updatePassword(account.id(), hash);
  }

  public AdminAccountRecord requireAccount(String sessionToken, boolean requirePasswordChanged) {
    if (sessionToken == null || sessionToken.trim().isEmpty()) throw new UnauthorizedException("unauthorized");
    String hash = tokenHasher.sha256(sessionToken.trim());
    Optional<AdminSessionRecord> session = adminSessionRepository.findActiveByHash(hash);
    if (!session.isPresent()) throw new UnauthorizedException("unauthorized");
    Optional<AdminAccountRecord> account = adminAccountRepository.findById(session.get().adminId());
    if (!account.isPresent() || !account.get().active()) throw new UnauthorizedException("unauthorized");
    if (requirePasswordChanged && account.get().mustChangePassword()) throw new PasswordChangeRequiredException();
    return account.get();
  }

  public static class LoginResult {
    private final String token;
    private final boolean mustChangePassword;
    private final String email;
    private final String sessionId;

    public LoginResult(String token, boolean mustChangePassword, String email, String sessionId) {
      this.token = token;
      this.mustChangePassword = mustChangePassword;
      this.email = email;
      this.sessionId = sessionId;
    }

    public String token() { return token; }
    public boolean mustChangePassword() { return mustChangePassword; }
    public String email() { return email; }
    public String sessionId() { return sessionId; }
  }
}
