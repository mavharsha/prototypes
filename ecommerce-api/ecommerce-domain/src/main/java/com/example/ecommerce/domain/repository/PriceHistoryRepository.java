package com.example.ecommerce.domain.repository;

import com.example.ecommerce.domain.entity.PriceHistory;
import java.util.List;

public interface PriceHistoryRepository {
    PriceHistory save(PriceHistory priceHistory);
    List<PriceHistory> findByEntityTypeAndEntityId(String entityType, String entityId);
}
