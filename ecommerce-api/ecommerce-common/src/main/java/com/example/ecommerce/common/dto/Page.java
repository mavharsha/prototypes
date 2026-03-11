package com.example.ecommerce.common.dto;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
public record Page<T>(
        List<T> items,
        long totalItems,
        int totalPages,
        int currentPage,
        int pageSize,
        boolean hasNext,
        boolean hasPrevious
) {
    public static <T> Page<T> of(List<T> allItems, int page, int size) {
        int totalItems = allItems.size();
        int totalPages = (int) Math.ceil((double) totalItems / size);
        int fromIndex = Math.min(page * size, totalItems);
        int toIndex = Math.min(fromIndex + size, totalItems);
        List<T> items = allItems.subList(fromIndex, toIndex);
        return new Page<>(items, totalItems, totalPages, page, size,
                page < totalPages - 1, page > 0);
    }
}
