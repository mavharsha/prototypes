package com.example.ecommerce.repository;

import com.example.ecommerce.domain.entity.Sku;
import com.example.ecommerce.domain.repository.ProductRepository;
import com.example.ecommerce.domain.repository.SkuRepository;
import jakarta.inject.Singleton;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Singleton
public class InMemorySkuRepository implements SkuRepository {

    private final Map<String, Sku> store = new ConcurrentHashMap<>();
    private final ProductRepository productRepository;

    public InMemorySkuRepository(ProductRepository productRepository) {
        this.productRepository = productRepository;
        seedData();
    }

    private void seedData() {
        // Create SKUs for each product
        productRepository.findAll().forEach(product -> {
            String name = product.getName();
            String pid = product.getId();

            if (name.contains("MacBook")) {
                createSku(pid, "MBP16-M3P-18-512", 50, null, Map.of("memory", "18GB", "storage", "512GB"));
                createSku(pid, "MBP16-M3P-36-1TB", 30, new BigDecimal("2999.99"), Map.of("memory", "36GB", "storage", "1TB"));
                createSku(pid, "MBP16-M3M-48-1TB", 15, new BigDecimal("3499.99"), Map.of("memory", "48GB", "storage", "1TB"));
            } else if (name.contains("Galaxy S24")) {
                createSku(pid, "GS24U-256-BLK", 80, null, Map.of("storage", "256GB", "color", "Black"));
                createSku(pid, "GS24U-512-GRY", 40, new BigDecimal("1419.99"), Map.of("storage", "512GB", "color", "Gray"));
                createSku(pid, "GS24U-1TB-BLU", 20, new BigDecimal("1659.99"), Map.of("storage", "1TB", "color", "Blue"));
            } else if (name.contains("iPhone 15")) {
                createSku(pid, "IP15P-128-NAT", 100, null, Map.of("storage", "128GB", "color", "Natural Titanium"));
                createSku(pid, "IP15P-256-BLU", 60, new BigDecimal("1099.99"), Map.of("storage", "256GB", "color", "Blue Titanium"));
                createSku(pid, "IP15P-512-BLK", 30, new BigDecimal("1299.99"), Map.of("storage", "512GB", "color", "Black Titanium"));
            } else if (name.contains("WH-1000XM5")) {
                createSku(pid, "WH1000XM5-BLK", 120, null, Map.of("color", "Black"));
                createSku(pid, "WH1000XM5-SLV", 90, null, Map.of("color", "Silver"));
            } else if (name.contains("AirPods")) {
                createSku(pid, "APP2-USBC", 200, null, Map.of("charging", "USB-C"));
                createSku(pid, "APP2-MAGSAFE", 150, new BigDecimal("269.99"), Map.of("charging", "MagSafe"));
            } else if (name.contains("Galaxy Book")) {
                createSku(pid, "GB4P-16-512-SLV", 35, null, Map.of("storage", "512GB", "color", "Silver"));
                createSku(pid, "GB4P-16-1TB-GRP", 20, new BigDecimal("1649.99"), Map.of("storage", "1TB", "color", "Graphite"));
            } else if (name.contains("Air Max")) {
                createSku(pid, "AM90-WHT-9", 40, null, Map.of("color", "White", "size", "9"));
                createSku(pid, "AM90-WHT-10", 45, null, Map.of("color", "White", "size", "10"));
                createSku(pid, "AM90-BLK-9", 35, null, Map.of("color", "Black", "size", "9"));
                createSku(pid, "AM90-BLK-10", 50, null, Map.of("color", "Black", "size", "10"));
            } else if (name.contains("Ultraboost")) {
                createSku(pid, "UB23-BLK-9", 30, null, Map.of("color", "Core Black", "size", "9"));
                createSku(pid, "UB23-BLK-10", 35, null, Map.of("color", "Core Black", "size", "10"));
                createSku(pid, "UB23-WHT-10", 25, null, Map.of("color", "Cloud White", "size", "10"));
            }
        });
    }

    private void createSku(String productId, String skuCode, int stock, BigDecimal priceOverride, Map<String, String> attrs) {
        Sku sku = new Sku(productId, skuCode, stock);
        sku.setPriceOverride(priceOverride);
        sku.setAttributes(new java.util.HashMap<>(attrs));
        store.put(sku.getId(), sku);
    }

    @Override
    public Sku save(Sku sku) {
        store.put(sku.getId(), sku);
        return sku;
    }

    @Override
    public Optional<Sku> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Sku> findBySkuCode(String skuCode) {
        return store.values().stream().filter(s -> skuCode.equals(s.getSkuCode())).findFirst();
    }

    @Override
    public List<Sku> findByProductId(String productId) {
        return store.values().stream().filter(s -> productId.equals(s.getProductId())).collect(Collectors.toList());
    }

    @Override
    public List<Sku> findByProductIdAndActive(String productId, boolean active) {
        return store.values().stream()
                .filter(s -> productId.equals(s.getProductId()) && s.isActive() == active)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(String id) {
        store.remove(id);
    }

    @Override
    public void deleteByProductId(String productId) {
        store.entrySet().removeIf(e -> productId.equals(e.getValue().getProductId()));
    }

    @Override
    public boolean existsBySkuCode(String skuCode) {
        return store.values().stream().anyMatch(s -> skuCode.equals(s.getSkuCode()));
    }
}
