package com.example.ecommerce.api.controller;

import com.example.ecommerce.common.dto.*;
import com.example.ecommerce.logging.audit.AuditLogger;
import com.example.ecommerce.service.BrandService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;

import jakarta.validation.Valid;
import java.util.List;

@Controller("/api/brands")
public class BrandController {

    private final BrandService brandService;
    private final AuditLogger auditLogger;

    public BrandController(BrandService brandService, AuditLogger auditLogger) {
        this.brandService = brandService;
        this.auditLogger = auditLogger;
    }

    @Get
    @Secured(SecurityRule.IS_ANONYMOUS)
    public List<BrandDto> getAllBrands() {
        return brandService.getAllBrands();
    }

    @Get("/{id}")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public BrandDto getBrand(@PathVariable String id) {
        return brandService.getBrand(id);
    }

    @Get("/active")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public List<BrandDto> getActiveBrands() {
        return brandService.getActiveBrands();
    }

    @Get("/search")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public List<BrandDto> searchBrands(@QueryValue String query) {
        return brandService.searchBrands(query);
    }

    @Post
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<BrandDto> createBrand(@Body @Valid CreateBrandRequest request,
                                                Authentication authentication) {
        BrandDto created = brandService.createBrand(request);
        auditLogger.logSuccess(authentication.getName(), "CREATE", "Brand", created.id());
        return HttpResponse.created(created);
    }

    @Put("/{id}")
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public BrandDto updateBrand(@PathVariable String id, @Body @Valid UpdateBrandRequest request,
                                 Authentication authentication) {
        BrandDto updated = brandService.updateBrand(id, request);
        auditLogger.logSuccess(authentication.getName(), "UPDATE", "Brand", id);
        return updated;
    }

    @Delete("/{id}")
    @Secured({"ADMIN"})
    public HttpResponse<Void> deleteBrand(@PathVariable String id, Authentication authentication) {
        brandService.deleteBrand(id);
        auditLogger.logSuccess(authentication.getName(), "DELETE", "Brand", id);
        return HttpResponse.noContent();
    }
}
