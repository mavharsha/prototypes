package com.example.ecommerce.service.impl;

import com.example.ecommerce.common.dto.*;
import com.example.ecommerce.common.exception.DuplicateResourceException;
import com.example.ecommerce.common.exception.NotFoundException;
import com.example.ecommerce.domain.entity.Category;
import com.example.ecommerce.domain.repository.CategoryRepository;
import com.example.ecommerce.service.CategoryService;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryServiceImpl(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    public CategoryDto createCategory(CreateCategoryRequest request) {
        String slug = Category.generateSlug(request.name());
        if (categoryRepository.existsBySlug(slug)) {
            throw new DuplicateResourceException("Category", "slug", slug);
        }
        Category category = new Category(request.name(), request.description());
        category.setParentId(request.parentId());
        if (request.displayOrder() != null) {
            category.setDisplayOrder(request.displayOrder());
        }
        return toDto(categoryRepository.save(category));
    }

    @Override
    public CategoryDto getCategory(String id) {
        return toDto(categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Category", id)));
    }

    @Override
    public CategoryDto getCategoryBySlug(String slug) {
        return toDto(categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Category", slug)));
    }

    @Override
    public List<CategoryDto> getAllCategories() {
        return categoryRepository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public List<CategoryDto> getActiveCategories() {
        return categoryRepository.findByActive(true).stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public List<CategoryDto> getChildCategories(String parentId) {
        return categoryRepository.findByParentId(parentId).stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public List<CategoryTreeDto> getCategoryTree() {
        List<Category> roots = categoryRepository.findRootCategories();
        return roots.stream().map(this::toTreeDto).collect(Collectors.toList());
    }

    @Override
    public CategoryDto updateCategory(String id, UpdateCategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Category", id));
        if (request.name() != null) category.setName(request.name());
        if (request.description() != null) category.setDescription(request.description());
        if (request.active() != null) category.setActive(request.active());
        if (request.displayOrder() != null) category.setDisplayOrder(request.displayOrder());
        return toDto(categoryRepository.save(category));
    }

    @Override
    public CategoryDto moveCategory(String id, String newParentId) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Category", id));
        category.setParentId(newParentId);
        return toDto(categoryRepository.save(category));
    }

    @Override
    public void deleteCategory(String id) {
        if (categoryRepository.findById(id).isEmpty()) {
            throw new NotFoundException("Category", id);
        }
        categoryRepository.deleteById(id);
    }

    private CategoryDto toDto(Category c) {
        return new CategoryDto(c.getId(), c.getName(), c.getDescription(), c.getSlug(),
                c.getParentId(), c.isActive(), c.getDisplayOrder(), c.getCreatedAt(), c.getUpdatedAt());
    }

    private CategoryTreeDto toTreeDto(Category c) {
        List<Category> children = categoryRepository.findByParentId(c.getId());
        List<CategoryTreeDto> childDtos = children.stream().map(this::toTreeDto).collect(Collectors.toList());
        return new CategoryTreeDto(c.getId(), c.getName(), c.getSlug(), c.isActive(), c.getDisplayOrder(), childDtos);
    }
}
