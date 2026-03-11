package com.example.ecommerce.api.controller;

import com.example.ecommerce.common.dto.CheckoutRequest;
import com.example.ecommerce.common.dto.CheckoutResponse;
import com.example.ecommerce.logging.audit.AuditLogger;
import com.example.ecommerce.service.CheckoutService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import jakarta.validation.Valid;

/**
 * REST controller for Checkout operations.
 * Processes cart checkout with payment.
 */
@Controller("/api/checkout")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class CheckoutController {

    private final CheckoutService checkoutService;
    private final AuditLogger auditLogger;

    public CheckoutController(CheckoutService checkoutService, AuditLogger auditLogger) {
        this.checkoutService = checkoutService;
        this.auditLogger = auditLogger;
    }

    @Post
    public HttpResponse<CheckoutResponse> checkout(@Body @Valid CheckoutRequest request, Authentication authentication) {
        String customerId = authentication.getName();
        CheckoutResponse response = checkoutService.checkout(customerId, request);
        auditLogger.logSuccess(customerId, "CHECKOUT", "Order", response.order().id());
        return HttpResponse.created(response);
    }
}
