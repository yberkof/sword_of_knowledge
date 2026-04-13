package com.sok.backend.api.dto;

public class AdminCatalogItemUpsertRequest {
  private String code;
  private String name;
  private String itemType;
  private String metadataJson;
  private Boolean active;

  public String getCode() { return code; }
  public void setCode(String code) { this.code = code; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getItemType() { return itemType; }
  public void setItemType(String itemType) { this.itemType = itemType; }
  public String getMetadataJson() { return metadataJson; }
  public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
  public Boolean getActive() { return active; }
  public void setActive(Boolean active) { this.active = active; }
}
