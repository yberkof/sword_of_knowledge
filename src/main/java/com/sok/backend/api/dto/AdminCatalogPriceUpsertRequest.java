package com.sok.backend.api.dto;

public class AdminCatalogPriceUpsertRequest {
  private String itemCode;
  private String provider;
  private String region;
  private String currency;
  private Long amountMinor;
  private Boolean active;

  public String getItemCode() { return itemCode; }
  public void setItemCode(String itemCode) { this.itemCode = itemCode; }
  public String getProvider() { return provider; }
  public void setProvider(String provider) { this.provider = provider; }
  public String getRegion() { return region; }
  public void setRegion(String region) { this.region = region; }
  public String getCurrency() { return currency; }
  public void setCurrency(String currency) { this.currency = currency; }
  public Long getAmountMinor() { return amountMinor; }
  public void setAmountMinor(Long amountMinor) { this.amountMinor = amountMinor; }
  public Boolean getActive() { return active; }
  public void setActive(Boolean active) { this.active = active; }
}
