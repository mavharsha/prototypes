package com.example.ecommerce.repository;

import com.example.ecommerce.domain.entity.Product;
import com.example.ecommerce.domain.repository.ProductRepository;
import jakarta.inject.Singleton;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of ProductRepository.
 * For demonstration purposes - replace with JPA/JDBC in production.
 */
@Singleton
public class InMemoryProductRepository implements ProductRepository {

    private final Map<String, Product> store = new ConcurrentHashMap<>();

    public InMemoryProductRepository() {
        // Seed with sample data
        seedData();
    }

    private void seedData() {
        Product laptop = new Product("Laptop", "High-performance laptop", new BigDecimal("999.99"), 50);
        Product phone = new Product("Smartphone", "Latest smartphone model", new BigDecimal("699.99"), 100);
        Product headphones = new Product("Wireless Headphones", "Noise-cancelling headphones", new BigDecimal("249.99"), 75);

        store.put(laptop.getId(), laptop);
        store.put(phone.getId(), phone);
        store.put(headphones.getId(), headphones);
    }

    @Override
    public Product save(Product product) {
        store.put(product.getId(), product);
        return product;
    }

    @Override
    public Optional<Product> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Product> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void deleteById(String id) {
        store.remove(id);
    }

    @Override
    public boolean existsById(String id) {
        return store.containsKey(id);
    }
}
