package com.sok.backend.security;

import java.util.UUID;

public record LocalClaims(String userId, UUID sessionId, long expEpochSeconds) {}
