package com.example.wiki.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * A single search result hit.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageHit {

    private Long contentId;
    private String title;
    private String spaceKey;
    private String spaceName;
    private String contentType;
    private String labels;
    private float score;
    private Long created;
    private Long modified;

    /** Highlighted fragments by field name */
    private Map<String, String> highlights;
}
