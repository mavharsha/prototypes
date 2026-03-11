package com.example.ecommerce.domain.repository;

import com.example.ecommerce.domain.entity.Cart;

import java.util.Optional;

/**
 * Repository interface for Cart persistence.
 */
public interface CartRepository {

    Cart save(Cart cart);

    Optional<Cart> findById(String id);

    Optional<Cart> findByCustomerId(String customerId);

    void deleteById(String id);

    void deleteByCustomerId(String customerId);
}
