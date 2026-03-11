package com.example.ecommerce.common.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record PageRequest(
        int page,
        int size,
        String sortBy,
        String sortDirection
) {
    public PageRequest {
        if (page < 0) page = 0;
        if (size <= 0) size = 20;
        if (sortDirection == null) sortDirection = "ASC";
    }

    public static PageRequest of(int page, int size) {
        return new PageRequest(page, size, null, "ASC");
    }
}
