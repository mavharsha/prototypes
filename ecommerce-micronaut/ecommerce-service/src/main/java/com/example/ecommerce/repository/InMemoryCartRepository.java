package com.example.ecommerce.repository;

import com.example.ecommerce.domain.entity.Cart;
import com.example.ecommerce.domain.repository.CartRepository;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of CartRepository.
 */
@Singleton
public class InMemoryCartRepository implements CartRepository {

    private final Map<String, Cart> store = new ConcurrentHashMap<>();

    @Override
    public Cart save(Cart cart) {
        store.put(cart.getId(), cart);
        return cart;
    }

    @Override
    public Optional<Cart> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Cart> findByCustomerId(String customerId) {
        return store.values().stream()
                .filter(cart -> cart.getCustomerId().equals(customerId))
                .findFirst();
    }

    @Override
    public void deleteById(String id) {
        store.remove(id);
    }

    @Override
    public void deleteByCustomerId(String customerId) {
        store.values().removeIf(cart -> cart.getCustomerId().equals(customerId));
    }
}
