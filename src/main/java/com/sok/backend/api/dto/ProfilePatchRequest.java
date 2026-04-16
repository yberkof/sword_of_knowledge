package com.sok.backend.api.dto;

/** Partial update for {@code /api/profile} PATCH. Null fields are left unchanged. */
public class ProfilePatchRequest {
  private String name;
  private String countryCode;
  private String avatar;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getCountryCode() {
    return countryCode;
  }

  public void setCountryCode(String countryCode) {
    this.countryCode = countryCode;
  }

  public String getAvatar() {
    return avatar;
  }

  public void setAvatar(String avatar) {
    this.avatar = avatar;
  }
}
