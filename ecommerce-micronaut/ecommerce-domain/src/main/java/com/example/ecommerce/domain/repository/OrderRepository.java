package com.example.ecommerce.domain.repository;

import com.example.ecommerce.domain.entity.Order;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Order entities.
 */
public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(String id);

    List<Order> findAll();

    List<Order> findByCustomerId(String customerId);

    void deleteById(String id);
}
