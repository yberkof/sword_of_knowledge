package com.sok.backend.api.dto;

public class AuthTokensResponse {
  private final String accessToken;
  private final String refreshToken;
  private final String tokenType;

  public AuthTokensResponse(String accessToken, String refreshToken) {
    this.accessToken = accessToken;
    this.refreshToken = refreshToken;
    this.tokenType = "Bearer";
  }

  public String getAccessToken() {
    return accessToken;
  }

  public String getRefreshToken() {
    return refreshToken;
  }

  public String getTokenType() {
    return tokenType;
  }
}
