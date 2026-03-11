package com.example.ecommerce.api.controller;

import com.example.ecommerce.common.dto.*;
import com.example.ecommerce.common.enums.ProductStatus;
import com.example.ecommerce.logging.audit.AuditLogger;
import com.example.ecommerce.service.ProductService;
import com.example.ecommerce.service.ProductSearchService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;

import jakarta.validation.Valid;
import java.util.List;

@Controller("/api/products")
public class ProductController {

    private final ProductService productService;
    private final ProductSearchService productSearchService;
    private final AuditLogger auditLogger;

    public ProductController(ProductService productService, ProductSearchService productSearchService,
                              AuditLogger auditLogger) {
        this.productService = productService;
        this.productSearchService = productSearchService;
        this.auditLogger = auditLogger;
    }

    @Get
    @Secured(SecurityRule.IS_ANONYMOUS)
    public List<ProductSummaryDto> getAllProducts() {
        return productService.getAllProducts();
    }

    @Get("/{id}")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public ProductDto getProduct(@PathVariable String id) {
        return productService.getProduct(id);
    }

    @Get("/slug/{slug}")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public ProductDto getProductBySlug(@PathVariable String slug) {
        return productService.getProductBySlug(slug);
    }

    @Get("/{id}/full")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public ProductDto getProductWithSkus(@PathVariable String id) {
        return productService.getProductWithSkus(id);
    }

    @Get("/category/{categoryId}")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public List<ProductSummaryDto> getProductsByCategory(@PathVariable String categoryId) {
        return productService.getProductsByCategory(categoryId);
    }

    @Get("/brand/{brandId}")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public List<ProductSummaryDto> getProductsByBrand(@PathVariable String brandId) {
        return productService.getProductsByBrand(brandId);
    }

    @Post("/search")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public ProductSearchResponse searchProducts(@Body ProductSearchRequest request) {
        return productSearchService.search(request);
    }

    @Post
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<ProductDto> createProduct(@Body @Valid CreateProductRequest request,
                                                    Authentication authentication) {
        ProductDto created = productService.createProduct(request);
        auditLogger.logSuccess(authentication.getName(), "CREATE", "Product", created.id());
        return HttpResponse.created(created);
    }

    @Put("/{id}")
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public ProductDto updateProduct(@PathVariable String id, @Body @Valid UpdateProductRequest request,
                                     Authentication authentication) {
        ProductDto updated = productService.updateProduct(id, request);
        auditLogger.logSuccess(authentication.getName(), "UPDATE", "Product", id);
        return updated;
    }

    @Put("/{id}/status")
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public ProductDto updateProductStatus(@PathVariable String id, @Body ProductStatus status,
                                           Authentication authentication) {
        ProductDto updated = productService.updateProductStatus(id, status);
        auditLogger.logSuccess(authentication.getName(), "UPDATE_STATUS", "Product", id);
        return updated;
    }

    @Post("/{id}/images")
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public ProductDto addImage(@PathVariable String id, @Body @Valid AddImageRequest request,
                                Authentication authentication) {
        ProductDto updated = productService.addImage(id, request);
        auditLogger.logSuccess(authentication.getName(), "ADD_IMAGE", "Product", id);
        return updated;
    }

    @Delete("/{productId}/images/{imageId}")
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public ProductDto removeImage(@PathVariable String productId, @PathVariable String imageId,
                                   Authentication authentication) {
        ProductDto updated = productService.removeImage(productId, imageId);
        auditLogger.logSuccess(authentication.getName(), "REMOVE_IMAGE", "Product", productId);
        return updated;
    }

    @Put("/{productId}/images/{imageId}/primary")
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public ProductDto setPrimaryImage(@PathVariable String productId, @PathVariable String imageId,
                                      Authentication authentication) {
        ProductDto updated = productService.setPrimaryImage(productId, imageId);
        auditLogger.logSuccess(authentication.getName(), "SET_PRIMARY_IMAGE", "Product", productId);
        return updated;
    }

    @Delete("/{id}")
    @Secured({"ADMIN"})
    public HttpResponse<Void> deleteProduct(@PathVariable String id, Authentication authentication) {
        productService.deleteProduct(id);
        auditLogger.logSuccess(authentication.getName(), "DELETE", "Product", id);
        return HttpResponse.noContent();
    }
}
