package com.example.ecommerce.api.controller;

import com.example.ecommerce.common.dto.AddToCartRequest;
import com.example.ecommerce.common.dto.CartDto;
import com.example.ecommerce.logging.audit.AuditLogger;
import com.example.ecommerce.service.CartService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;

/**
 * REST controller for Cart operations.
 * Cart is scoped to the authenticated user.
 */
@Controller("/api/cart")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class CartController {

    private final CartService cartService;
    private final AuditLogger auditLogger;

    public CartController(CartService cartService, AuditLogger auditLogger) {
        this.cartService = cartService;
        this.auditLogger = auditLogger;
    }

    @Get
    public CartDto getCart(Authentication authentication) {
        String customerId = authentication.getName();
        auditLogger.logDataAccess(customerId, "Cart", customerId, "READ");
        return cartService.getCart(customerId);
    }

    @Post("/items")
    public HttpResponse<CartDto> addItem(@Body AddToCartRequest request, Authentication authentication) {
        String customerId = authentication.getName();
        CartDto updated = cartService.addItem(customerId, request);
        auditLogger.logSuccess(customerId, "ADD_ITEM", "Cart", request.productId());
        return HttpResponse.ok(updated);
    }

    @Delete("/items/{productId}")
    public CartDto removeItem(@PathVariable String productId, Authentication authentication) {
        String customerId = authentication.getName();
        CartDto updated = cartService.removeItem(customerId, productId);
        auditLogger.logSuccess(customerId, "REMOVE_ITEM", "Cart", productId);
        return updated;
    }

    @Delete
    public HttpResponse<Void> clearCart(Authentication authentication) {
        String customerId = authentication.getName();
        cartService.clearCart(customerId);
        auditLogger.logSuccess(customerId, "CLEAR", "Cart", customerId);
        return HttpResponse.noContent();
    }
}
