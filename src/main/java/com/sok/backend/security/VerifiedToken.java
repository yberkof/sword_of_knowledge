package com.sok.backend.security;

public record VerifiedToken(String uid, String email, String name) {}
