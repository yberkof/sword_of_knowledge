package com.sok.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sok.backend.api.dto.ProfileCreateRequest;
import com.sok.backend.api.dto.ProfilePatchRequest;
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

  public Optional<ProfileResponse> patchProfile(String uid, ProfilePatchRequest request) {
    if (request == null) {
      return userRepository.findById(uid).map(this::toResponse);
    }
    Optional<UserRecord> row = userRepository.findById(uid);
    if (!row.isPresent()) {
      return Optional.empty();
    }
    UserRecord u = row.get();
    String name =
        request.getName() != null && !request.getName().trim().isEmpty()
            ? request.getName().trim()
            : u.displayName();
    String country = resolveCountryCode(request, u.countryCode());
    String avatar =
        request.getAvatar() != null ? request.getAvatar().trim() : nullSafe(u.avatarUrl());
    userRepository.updateProfileFields(
        uid, name, u.username() == null ? name : u.username(), country, avatar);
    return userRepository.findById(uid).map(this::toResponse);
  }

  private static String nullSafe(String s) {
    return s == null ? "" : s;
  }

  private static String resolveCountryCode(ProfilePatchRequest request, String current) {
    String code = normalizeCountryCode(request.getCountryCode());
    if (!code.isEmpty()) return code;
    return nullSafe(current);
  }

  private static String normalizeCountryCode(String raw) {
    if (raw == null) return "";
    String code = raw.trim().toUpperCase();
    if (code.length() != 2) return "";
    for (int i = 0; i < code.length(); i++) {
      char c = code.charAt(i);
      if (c < 'A' || c > 'Z') return "";
    }
    return code;
  }

  private ProfileResponse toResponse(UserRecord row) {
    return new ProfileResponse(
        row.id(),
        row.displayName(),
        row.username() == null ? row.displayName() : row.username(),
        row.countryCode(),
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
