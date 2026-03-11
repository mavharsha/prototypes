package com.example.ecommerce.domain.repository;

import com.example.ecommerce.domain.entity.Product;
import com.example.ecommerce.common.enums.ProductStatus;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Product save(Product product);
    Optional<Product> findById(String id);
    Optional<Product> findBySlug(String slug);
    List<Product> findAll();
    List<Product> findByCategoryId(String categoryId);
    List<Product> findByBrandId(String brandId);
    List<Product> findByStatus(ProductStatus status);
    long count();
    void deleteById(String id);
    boolean existsBySlug(String slug);
}
