package com.example.ecommerce.api.controller;

import com.example.ecommerce.common.dto.CalculatePriceRequest;
import com.example.ecommerce.common.dto.PriceBreakdownDto;
import com.example.ecommerce.logging.audit.AuditLogger;
import com.example.ecommerce.service.PricingService;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;

import jakarta.validation.Valid;

@Controller("/api/pricing")
public class PricingController {

    private final PricingService pricingService;
    private final AuditLogger auditLogger;

    public PricingController(PricingService pricingService, AuditLogger auditLogger) {
        this.pricingService = pricingService;
        this.auditLogger = auditLogger;
    }

    @Post("/calculate")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public PriceBreakdownDto calculatePrice(@Body @Valid CalculatePriceRequest request) {
        PriceBreakdownDto breakdown = pricingService.calculatePrice(request);
        auditLogger.logDataAccess("anonymous", "PriceCalculation", null, "CALCULATE");
        return breakdown;
    }
}
