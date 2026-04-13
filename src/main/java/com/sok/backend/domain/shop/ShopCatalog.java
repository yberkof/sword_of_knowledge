package com.sok.backend.domain.shop;

import com.sok.backend.persistence.CatalogItemRecord;
import com.sok.backend.persistence.CatalogPriceRecord;
import com.sok.backend.persistence.CatalogRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ShopCatalog {
  private final CatalogRepository catalogRepository;

  public ShopCatalog(CatalogRepository catalogRepository) {
    this.catalogRepository = catalogRepository;
  }

  public Integer priceOf(String itemId) {
    Optional<CatalogItemRecord> item = catalogRepository.findItemByCode(itemId);
    if (!item.isPresent() || !item.get().active()) return null;
    Optional<CatalogPriceRecord> price =
        catalogRepository.findActivePrice(item.get().id(), "internal", "global", "GOLD");
    if (!price.isPresent()) return null;
    long value = price.get().amountMinor();
    return value > Integer.MAX_VALUE ? null : (int) value;
  }
}
