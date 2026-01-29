package com.example.wiki.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request to update an existing page.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageUpdateRequest {

    /** New title (optional) */
    private String title;

    /** New body content */
    private String body;

    /** Whether body is already in storage format (XHTML) */
    @Builder.Default
    private boolean storageFormat = false;

    /** Labels to set (replaces existing) */
    private List<String> labels;

    /** Version comment */
    private String versionComment;

    /** Modifier username */
    private String modifier;
}
