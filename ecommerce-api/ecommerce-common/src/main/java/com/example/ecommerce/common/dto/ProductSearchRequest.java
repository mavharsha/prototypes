package com.example.ecommerce.common.dto;

import com.example.ecommerce.common.enums.ProductStatus;
import io.micronaut.serde.annotation.Serdeable;
import java.math.BigDecimal;

@Serdeable
public record ProductSearchRequest(
        String query,
        String categoryId,
        String brandId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        ProductStatus status,
        int page,
        int size,
        String sortBy,
        String sortDirection
) {
    public ProductSearchRequest {
        if (page < 0) page = 0;
        if (size <= 0) size = 20;
        if (sortDirection == null) sortDirection = "ASC";
    }
}
