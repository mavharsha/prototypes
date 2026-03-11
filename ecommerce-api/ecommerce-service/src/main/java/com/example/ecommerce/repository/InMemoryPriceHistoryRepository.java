package com.example.ecommerce.repository;

import com.example.ecommerce.domain.entity.PriceHistory;
import com.example.ecommerce.domain.repository.PriceHistoryRepository;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Singleton
public class InMemoryPriceHistoryRepository implements PriceHistoryRepository {

    private final Map<String, PriceHistory> store = new ConcurrentHashMap<>();

    @Override
    public PriceHistory save(PriceHistory priceHistory) {
        store.put(priceHistory.getId(), priceHistory);
        return priceHistory;
    }

    @Override
    public List<PriceHistory> findByEntityTypeAndEntityId(String entityType, String entityId) {
        return store.values().stream()
                .filter(ph -> entityType.equals(ph.getEntityType()) && entityId.equals(ph.getEntityId()))
                .collect(Collectors.toList());
    }
}
