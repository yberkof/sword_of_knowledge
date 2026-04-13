package com.sok.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sok.backend.api.dto.ProfileCreateRequest;
import com.sok.backend.api.dto.ProfileResponse;
import com.sok.backend.persistence.UserRecord;
import com.sok.backend.persistence.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.Collections;
import org.springframework.stereotype.Service;

@Service
public class ProfileService {
  private final UserRepository userRepository;
  private final ObjectMapper objectMapper;

  public ProfileService(UserRepository userRepository, ObjectMapper objectMapper) {
    this.userRepository = userRepository;
    this.objectMapper = objectMapper;
  }

  public Optional<ProfileResponse> getProfile(String uid) {
    Optional<UserRecord> row = userRepository.findById(uid);
    row.ifPresent(u -> userRepository.touchLogin(u.id()));
    return row.map(this::toResponse);
  }

  public ProfileResponse createIfMissing(String uid, ProfileCreateRequest request) {
    Optional<UserRecord> existing = userRepository.findById(uid);
    if (existing.isPresent()) {
      return toResponse(existing.get());
    }
    String name = request != null && request.getName() != null ? request.getName() : "Warrior";
    String username =
        request != null && request.getUsername() != null ? request.getUsername() : name;
    String avatar = request != null && request.getAvatar() != null ? request.getAvatar() : "";
    UserRecord created = userRepository.create(uid, name, username, avatar);
    userRepository.touchLogin(uid);
    return toResponse(created);
  }

  private ProfileResponse toResponse(UserRecord row) {
    return new ProfileResponse(
        row.id(),
        row.displayName(),
        row.username() == null ? row.displayName() : row.username(),
        row.countryFlag(),
        row.title(),
        row.level(),
        row.xp(),
        row.gold(),
        row.gems(),
        row.avatarUrl(),
        row.rank(),
        row.trophies(),
        decodeInventory(row.inventoryJson()));
  }

  private List<String> decodeInventory(String raw) {
    if (raw == null || raw.trim().isEmpty()) {
      return Collections.emptyList();
    }
    try {
      return objectMapper.readValue(raw, new TypeReference<List<String>>() {});
    } catch (Exception ignored) {
      return Collections.emptyList();
    }
  }
}
