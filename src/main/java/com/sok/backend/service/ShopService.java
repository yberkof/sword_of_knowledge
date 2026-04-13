package com.sok.backend.service;

import com.sok.backend.domain.shop.ShopCatalog;
import com.sok.backend.persistence.EconomyRepository;
import com.sok.backend.persistence.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShopService {
  private final ShopCatalog shopCatalog;
  private final EconomyRepository economyRepository;
  private final Counter purchaseOk;
  private final Counter purchaseFail;

  public ShopService(
      ShopCatalog shopCatalog, EconomyRepository economyRepository, MeterRegistry meterRegistry) {
    this.shopCatalog = shopCatalog;
    this.economyRepository = economyRepository;
    this.purchaseOk = Counter.builder("sok.shop.purchase.ok").register(meterRegistry);
    this.purchaseFail = Counter.builder("sok.shop.purchase.fail").register(meterRegistry);
  }

  @Transactional
  public boolean purchase(String uid, String itemId, String idempotencyKey) {
    Integer price = shopCatalog.priceOf(itemId);
    if (price == null) {
      purchaseFail.increment();
      return false;
    }
    boolean ok =
        economyRepository.applyTransaction(
            uid,
            idempotencyKey,
            "shop_purchase",
            "shop_purchase:" + itemId,
            -price,
            0,
            0,
            0,
            itemId,
            "{\"itemId\":\"" + itemId + "\"}");
    if (ok) purchaseOk.increment();
    else purchaseFail.increment();
    return ok;
  }
}
