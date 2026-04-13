package com.sok.backend.persistence;

import java.util.UUID;

public class CatalogItemRecord {
  private final UUID id;
  private final String code;
  private final String name;
  private final String itemType;
  private final String metadataJson;
  private final boolean active;
  private final int version;

  public CatalogItemRecord(
      UUID id, String code, String name, String itemType, String metadataJson, boolean active, int version) {
    this.id = id;
    this.code = code;
    this.name = name;
    this.itemType = itemType;
    this.metadataJson = metadataJson;
    this.active = active;
    this.version = version;
  }

  public UUID id() { return id; }
  public String code() { return code; }
  public String name() { return name; }
  public String itemType() { return itemType; }
  public String metadataJson() { return metadataJson; }
  public boolean active() { return active; }
  public int version() { return version; }
}
