package com.example.wiki.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request to create a new page.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageCreateRequest {

    @NotBlank(message = "Space key is required")
    private String spaceKey;

    @NotBlank(message = "Title is required")
    private String title;

    /** Parent page ID (optional) */
    private Long parentId;

    /** Page content - can be plain text or XHTML storage format */
    private String body;

    /** Whether body is already in storage format (XHTML) */
    @Builder.Default
    private boolean storageFormat = false;

    /** Labels to attach */
    private List<String> labels;

    /** Content type: PAGE (default), BLOGPOST */
    @Builder.Default
    private String contentType = "PAGE";

    /** Creator username */
    private String creator;
}
