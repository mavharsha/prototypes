package com.example.ecommerce.api.controller;

import com.example.ecommerce.common.dto.ProductDto;
import com.example.ecommerce.logging.audit.AuditLogger;
import com.example.ecommerce.service.ProductService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;

import java.util.List;

/**
 * REST controller for Product operations.
 * GET operations are public, modifications require authentication.
 */
@Controller("/api/products")
public class ProductController {

    private final ProductService productService;
    private final AuditLogger auditLogger;

    public ProductController(ProductService productService, AuditLogger auditLogger) {
        this.productService = productService;
        this.auditLogger = auditLogger;
    }

    @Get
    @Secured(SecurityRule.IS_ANONYMOUS)
    public List<ProductDto> getAllProducts() {
        return productService.getAllProducts();
    }

    @Get("/{id}")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public ProductDto getProduct(@PathVariable String id) {
        return productService.getProduct(id);
    }

    @Post
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<ProductDto> createProduct(@Body ProductDto productDto, Authentication authentication) {
        ProductDto created = productService.createProduct(productDto);
        auditLogger.logSuccess(authentication.getName(), "CREATE", "Product", created.id());
        return HttpResponse.created(created);
    }

    @Put("/{id}")
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public ProductDto updateProduct(@PathVariable String id, @Body ProductDto productDto, Authentication authentication) {
        ProductDto updated = productService.updateProduct(id, productDto);
        auditLogger.logSuccess(authentication.getName(), "UPDATE", "Product", id);
        return updated;
    }

    @Delete("/{id}")
    @Secured({"ADMIN"})  // Only admins can delete products
    public HttpResponse<Void> deleteProduct(@PathVariable String id, Authentication authentication) {
        productService.deleteProduct(id);
        auditLogger.logSuccess(authentication.getName(), "DELETE", "Product", id);
        return HttpResponse.noContent();
    }
}
