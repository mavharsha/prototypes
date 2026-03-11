package com.example.ecommerce.domain.repository;

import com.example.ecommerce.domain.entity.Brand;
import java.util.List;
import java.util.Optional;

public interface BrandRepository {
    Brand save(Brand brand);
    Optional<Brand> findById(String id);
    Optional<Brand> findByName(String name);
    List<Brand> findAll();
    List<Brand> findByActive(boolean active);
    List<Brand> searchByName(String query);
    void deleteById(String id);
    boolean existsByName(String name);
}
