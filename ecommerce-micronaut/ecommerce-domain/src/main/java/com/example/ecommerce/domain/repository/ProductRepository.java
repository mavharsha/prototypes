package com.example.ecommerce.domain.repository;

import com.example.ecommerce.domain.entity.Product;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Product entities.
 * Defines contract for persistence - implementations can be in-memory, JPA, etc.
 */
public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findById(String id);

    List<Product> findAll();

    void deleteById(String id);

    boolean existsById(String id);
}
