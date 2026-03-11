package com.example.ecommerce.service.impl;

import com.example.ecommerce.common.dto.*;
import com.example.ecommerce.common.enums.ProductStatus;
import com.example.ecommerce.domain.entity.Product;
import com.example.ecommerce.domain.repository.ProductRepository;
import com.example.ecommerce.service.ProductSearchService;
import jakarta.inject.Singleton;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
public class ProductSearchServiceImpl implements ProductSearchService {

    private final ProductRepository productRepository;

    public ProductSearchServiceImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public ProductSearchResponse search(ProductSearchRequest request) {
        Stream<Product> stream = productRepository.findAll().stream();

        // Apply filters
        if (request.query() != null && !request.query().isBlank()) {
            String query = request.query().toLowerCase();
            stream = stream.filter(p ->
                    (p.getName() != null && p.getName().toLowerCase().contains(query)) ||
                    (p.getDescription() != null && p.getDescription().toLowerCase().contains(query)));
        }
        if (request.categoryId() != null) {
            stream = stream.filter(p -> request.categoryId().equals(p.getCategoryId()));
        }
        if (request.brandId() != null) {
            stream = stream.filter(p -> request.brandId().equals(p.getBrandId()));
        }
        if (request.minPrice() != null) {
            stream = stream.filter(p -> p.getBasePrice() != null && p.getBasePrice().compareTo(request.minPrice()) >= 0);
        }
        if (request.maxPrice() != null) {
            stream = stream.filter(p -> p.getBasePrice() != null && p.getBasePrice().compareTo(request.maxPrice()) <= 0);
        }
        if (request.status() != null) {
            stream = stream.filter(p -> request.status().equals(p.getStatus()));
        }

        List<Product> filtered = stream.collect(Collectors.toList());

        // Build facets
        Map<String, Map<String, Long>> facets = new LinkedHashMap<>();
        facets.put("status", filtered.stream()
                .filter(p -> p.getStatus() != null)
                .collect(Collectors.groupingBy(p -> p.getStatus().name(), Collectors.counting())));
        facets.put("category", filtered.stream()
                .filter(p -> p.getCategoryId() != null)
                .collect(Collectors.groupingBy(Product::getCategoryId, Collectors.counting())));
        facets.put("brand", filtered.stream()
                .filter(p -> p.getBrandId() != null)
                .collect(Collectors.groupingBy(Product::getBrandId, Collectors.counting())));

        // Sort
        Comparator<Product> comparator = getComparator(request.sortBy(), request.sortDirection());
        filtered.sort(comparator);

        // Paginate
        int totalItems = filtered.size();
        int page = request.page();
        int size = request.size();
        int totalPages = (int) Math.ceil((double) totalItems / size);
        int fromIndex = Math.min(page * size, totalItems);
        int toIndex = Math.min(fromIndex + size, totalItems);
        List<Product> pageItems = filtered.subList(fromIndex, toIndex);

        List<ProductSummaryDto> summaries = pageItems.stream()
                .map(this::toSummaryDto)
                .collect(Collectors.toList());

        return new ProductSearchResponse(summaries, totalItems, totalPages, page, size, facets);
    }

    private Comparator<Product> getComparator(String sortBy, String sortDirection) {
        Comparator<Product> comparator;
        if ("price".equalsIgnoreCase(sortBy)) {
            comparator = Comparator.comparing(Product::getBasePrice, Comparator.nullsLast(Comparator.naturalOrder()));
        } else if ("name".equalsIgnoreCase(sortBy)) {
            comparator = Comparator.comparing(Product::getName, Comparator.nullsLast(Comparator.naturalOrder()));
        } else {
            comparator = Comparator.comparing(Product::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
        }
        if ("DESC".equalsIgnoreCase(sortDirection)) {
            comparator = comparator.reversed();
        }
        return comparator;
    }

    private ProductSummaryDto toSummaryDto(Product p) {
        return new ProductSummaryDto(p.getId(), p.getName(), p.getSlug(),
                p.getBrandId(), p.getCategoryId(), p.getStatus(), p.getBasePrice(), p.getPrimaryImageUrl());
    }
}
