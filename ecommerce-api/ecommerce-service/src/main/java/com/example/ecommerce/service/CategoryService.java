package com.example.ecommerce.service;

import com.example.ecommerce.common.dto.*;
import java.util.List;

public interface CategoryService {
    CategoryDto createCategory(CreateCategoryRequest request);
    CategoryDto getCategory(String id);
    CategoryDto getCategoryBySlug(String slug);
    List<CategoryDto> getAllCategories();
    List<CategoryDto> getActiveCategories();
    List<CategoryDto> getChildCategories(String parentId);
    List<CategoryTreeDto> getCategoryTree();
    CategoryDto updateCategory(String id, UpdateCategoryRequest request);
    CategoryDto moveCategory(String id, String newParentId);
    void deleteCategory(String id);
}
