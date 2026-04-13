package com.sok.backend.service;

import com.sok.backend.api.ResourceNotFoundException;
import com.sok.backend.persistence.CatalogItemRecord;
import com.sok.backend.persistence.CatalogPriceRecord;
import com.sok.backend.persistence.CatalogRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class CatalogAdminService {
  private final CatalogRepository catalogRepository;

  public CatalogAdminService(CatalogRepository catalogRepository) {
    this.catalogRepository = catalogRepository;
  }

  public List<CatalogItemRecord> listItems() {
    return catalogRepository.listItems();
  }

  public CatalogItemRecord upsertItem(String code, String name, String itemType, String metadataJson, boolean active) {
    return catalogRepository.upsertItem(code, name, itemType, metadataJson, active);
  }

  public CatalogPriceRecord upsertPrice(
      String itemCode, String provider, String region, String currency, long amountMinor, boolean active) {
    Optional<CatalogItemRecord> item = catalogRepository.findItemByCode(itemCode);
    if (!item.isPresent()) throw new ResourceNotFoundException("item_not_found");
    return catalogRepository.upsertPrice(item.get().id(), provider, region, currency, amountMinor, active);
  }
}
