package com.example.wiki.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Search request parameters.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchRequest {

    /** Full-text search query */
    private String query;

    /** Filter by space key */
    private String spaceKey;

    /** Filter by content type (PAGE, BLOGPOST, COMMENT) */
    private String contentType;

    /** Filter by labels (OR - any matching label) */
    private List<String> labels;

    /** Filter by creator username */
    private String creator;

    /** Filter pages created after this date */
    private LocalDateTime createdAfter;

    /** Filter pages modified after this date */
    private LocalDateTime modifiedAfter;

    /** Filter by ancestor page ID (search within subtree) */
    private Long ancestorId;

    /** Sort field: relevance (default), modified, created, title */
    private String sort;

    /** Sort order: asc or desc (default for dates) */
    private String order;

    /** Page number (0-based) */
    @Builder.Default
    private int page = 0;

    /** Results per page */
    @Builder.Default
    private int size = 20;
}
