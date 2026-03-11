package com.example.ecommerce.api.controller;

import com.example.ecommerce.common.dto.*;
import com.example.ecommerce.logging.audit.AuditLogger;
import com.example.ecommerce.service.SkuService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@Controller("/api/products/{productId}/skus")
public class SkuController {

    private final SkuService skuService;
    private final AuditLogger auditLogger;

    public SkuController(SkuService skuService, AuditLogger auditLogger) {
        this.skuService = skuService;
        this.auditLogger = auditLogger;
    }

    @Get
    @Secured(SecurityRule.IS_ANONYMOUS)
    public List<SkuDto> getSkusByProduct(@PathVariable String productId) {
        return skuService.getSkusByProduct(productId);
    }

    @Get("/{skuId}")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public SkuDto getSku(@PathVariable String productId, @PathVariable String skuId) {
        return skuService.getSku(skuId);
    }

    @Get("/code/{skuCode}")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public SkuDto getSkuByCode(@PathVariable String productId, @PathVariable String skuCode) {
        return skuService.getSkuByCode(skuCode);
    }

    @Get("/availability/{skuCode}")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public Map<String, Object> checkAvailability(@PathVariable String productId,
                                                   @PathVariable String skuCode,
                                                   @QueryValue(defaultValue = "1") int quantity) {
        boolean available = skuService.checkAvailability(skuCode, quantity);
        return Map.of("skuCode", skuCode, "quantity", quantity, "available", available);
    }

    @Post
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<SkuDto> createSku(@PathVariable String productId,
                                            @Body @Valid CreateSkuRequest request,
                                            Authentication authentication) {
        SkuDto created = skuService.createSku(productId, request);
        auditLogger.logSuccess(authentication.getName(), "CREATE", "SKU", created.id());
        return HttpResponse.created(created);
    }

    @Put("/{skuId}")
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public SkuDto updateSku(@PathVariable String productId, @PathVariable String skuId,
                             @Body @Valid UpdateSkuRequest request, Authentication authentication) {
        SkuDto updated = skuService.updateSku(skuId, request);
        auditLogger.logSuccess(authentication.getName(), "UPDATE", "SKU", skuId);
        return updated;
    }

    @Put("/{skuId}/stock")
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public SkuDto updateStock(@PathVariable String productId, @PathVariable String skuId,
                               @Body Map<String, Integer> body, Authentication authentication) {
        SkuDto updated;
        if (body.containsKey("quantity")) {
            updated = skuService.updateStock(skuId, body.get("quantity"));
        } else if (body.containsKey("adjustment")) {
            updated = skuService.adjustStock(skuId, body.get("adjustment"));
        } else {
            throw new IllegalArgumentException("Request must contain 'quantity' or 'adjustment'");
        }
        auditLogger.logSuccess(authentication.getName(), "UPDATE_STOCK", "SKU", skuId);
        return updated;
    }

    @Delete("/{skuId}")
    @Secured({"ADMIN"})
    public HttpResponse<Void> deleteSku(@PathVariable String productId, @PathVariable String skuId,
                                          Authentication authentication) {
        skuService.deleteSku(skuId);
        auditLogger.logSuccess(authentication.getName(), "DELETE", "SKU", skuId);
        return HttpResponse.noContent();
    }
}
