package com.sok.backend.api.dto;

public class ShopPurchaseRequest {
  private String itemId;
  private Integer costGold;

  public String getItemId() {
    return itemId;
  }

  public void setItemId(String itemId) {
    this.itemId = itemId;
  }

  public Integer getCostGold() {
    return costGold;
  }

  public void setCostGold(Integer costGold) {
    this.costGold = costGold;
  }
}
