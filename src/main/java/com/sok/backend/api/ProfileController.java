package com.sok.backend.api;

import com.sok.backend.api.dto.ProfileCreateRequest;
import com.sok.backend.api.dto.ProfileResponse;
import com.sok.backend.security.SecurityUtils;
import com.sok.backend.service.ProfileService;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
  private final ProfileService profileService;

  public ProfileController(ProfileService profileService) {
    this.profileService = profileService;
  }

  @GetMapping
  public ResponseEntity<?> getProfile() {
    String uid = SecurityUtils.currentUid();
    Optional<ProfileResponse> profile = profileService.getProfile(uid);
    if (!profile.isPresent()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(Collections.singletonMap("error", "Not found"));
    }
    return ResponseEntity.ok(profile.get());
  }

  @PostMapping
  public ResponseEntity<ProfileResponse> createProfile(
      @RequestBody(required = false) ProfileCreateRequest request) {
    String uid = SecurityUtils.currentUid();
    ProfileResponse profile = profileService.createIfMissing(uid, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(profile);
  }

  @PatchMapping
  public ResponseEntity<?> patchProfile() {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
        .body(Collections.singletonMap("error", "Not found"));
  }
}
