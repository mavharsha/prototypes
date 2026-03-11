package com.example.ecommerce.common.dto;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;
import java.util.Map;

@Serdeable
public record ProductSearchResponse(
        List<ProductSummaryDto> products,
        long totalItems,
        int totalPages,
        int currentPage,
        int pageSize,
        Map<String, Map<String, Long>> facets
) {}
