package com.example.ecommerce.api.controller;

import com.example.ecommerce.common.dto.*;
import com.example.ecommerce.logging.audit.AuditLogger;
import com.example.ecommerce.service.CategoryService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;

import jakarta.validation.Valid;
import java.util.List;

@Controller("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;
    private final AuditLogger auditLogger;

    public CategoryController(CategoryService categoryService, AuditLogger auditLogger) {
        this.categoryService = categoryService;
        this.auditLogger = auditLogger;
    }

    @Get
    @Secured(SecurityRule.IS_ANONYMOUS)
    public List<CategoryDto> getAllCategories() {
        return categoryService.getAllCategories();
    }

    @Get("/{id}")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public CategoryDto getCategory(@PathVariable String id) {
        return categoryService.getCategory(id);
    }

    @Get("/slug/{slug}")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public CategoryDto getCategoryBySlug(@PathVariable String slug) {
        return categoryService.getCategoryBySlug(slug);
    }

    @Get("/tree")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public List<CategoryTreeDto> getCategoryTree() {
        return categoryService.getCategoryTree();
    }

    @Get("/active")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public List<CategoryDto> getActiveCategories() {
        return categoryService.getActiveCategories();
    }

    @Get("/{id}/children")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public List<CategoryDto> getChildCategories(@PathVariable String id) {
        return categoryService.getChildCategories(id);
    }

    @Post
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<CategoryDto> createCategory(@Body @Valid CreateCategoryRequest request,
                                                      Authentication authentication) {
        CategoryDto created = categoryService.createCategory(request);
        auditLogger.logSuccess(authentication.getName(), "CREATE", "Category", created.id());
        return HttpResponse.created(created);
    }

    @Put("/{id}")
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public CategoryDto updateCategory(@PathVariable String id, @Body @Valid UpdateCategoryRequest request,
                                       Authentication authentication) {
        CategoryDto updated = categoryService.updateCategory(id, request);
        auditLogger.logSuccess(authentication.getName(), "UPDATE", "Category", id);
        return updated;
    }

    @Delete("/{id}")
    @Secured({"ADMIN"})
    public HttpResponse<Void> deleteCategory(@PathVariable String id, Authentication authentication) {
        categoryService.deleteCategory(id);
        auditLogger.logSuccess(authentication.getName(), "DELETE", "Category", id);
        return HttpResponse.noContent();
    }
}
