package com.example.ecommerce.service.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Introspected;

import java.math.BigDecimal;

@ConfigurationProperties("pricing")
@Introspected
public class PricingConfig {

    private BigDecimal taxRate = new BigDecimal("0.08");
    private BigDecimal standardShippingRate = new BigDecimal("5.99");
    private BigDecimal priorityShippingRate = new BigDecimal("14.99");

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(BigDecimal taxRate) {
        this.taxRate = taxRate;
    }

    public BigDecimal getStandardShippingRate() {
        return standardShippingRate;
    }

    public void setStandardShippingRate(BigDecimal standardShippingRate) {
        this.standardShippingRate = standardShippingRate;
    }

    public BigDecimal getPriorityShippingRate() {
        return priorityShippingRate;
    }

    public void setPriorityShippingRate(BigDecimal priorityShippingRate) {
        this.priorityShippingRate = priorityShippingRate;
    }
}
