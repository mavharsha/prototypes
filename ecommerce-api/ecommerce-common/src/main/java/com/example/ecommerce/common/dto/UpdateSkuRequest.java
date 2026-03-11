package com.example.ecommerce.common.dto;

import io.micronaut.serde.annotation.Serdeable;
import java.math.BigDecimal;
import java.util.Map;

@Serdeable
public record UpdateSkuRequest(
        BigDecimal priceOverride,
        Integer lowStockThreshold,
        Map<String, String> attributes,
        String barcode,
        Double weight,
        Double dimensionLength,
        Double dimensionWidth,
        Double dimensionHeight,
        Boolean active
) {}
