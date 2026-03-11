package com.example.ecommerce.domain.repository;

import com.example.ecommerce.domain.entity.Sku;
import java.util.List;
import java.util.Optional;

public interface SkuRepository {
    Sku save(Sku sku);
    Optional<Sku> findById(String id);
    Optional<Sku> findBySkuCode(String skuCode);
    List<Sku> findByProductId(String productId);
    List<Sku> findByProductIdAndActive(String productId, boolean active);
    void deleteById(String id);
    void deleteByProductId(String productId);
    boolean existsBySkuCode(String skuCode);
}
