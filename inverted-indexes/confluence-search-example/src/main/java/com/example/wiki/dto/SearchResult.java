package com.example.wiki.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Search result response.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchResult {

    /** Matching page hits */
    private List<PageHit> hits;

    /** Total number of matching documents */
    private long totalHits;

    /** Current page number */
    private int page;

    /** Results per page */
    private int size;

    /** Total number of pages */
    private int totalPages;

    /** Facet counts by field */
    private Map<String, List<FacetValue>> facets;

    /** Search execution time in milliseconds */
    private long searchTimeMs;

    /**
     * Facet value with count.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FacetValue {
        private String value;
        private int count;
    }
}
