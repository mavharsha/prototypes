package com.example.ecommerce.domain.repository;

import com.example.ecommerce.domain.entity.Category;
import java.util.List;
import java.util.Optional;

public interface CategoryRepository {
    Category save(Category category);
    Optional<Category> findById(String id);
    Optional<Category> findBySlug(String slug);
    List<Category> findAll();
    List<Category> findByParentId(String parentId);
    List<Category> findRootCategories();
    List<Category> findByActive(boolean active);
    void deleteById(String id);
    boolean existsBySlug(String slug);
}
