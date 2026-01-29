package com.example.wiki.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to create a new space.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpaceCreateRequest {

    @NotBlank(message = "Space key is required")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "Space key must start with uppercase letter and contain only uppercase letters, numbers, and underscores")
    private String spaceKey;

    @NotBlank(message = "Space name is required")
    private String spaceName;

    private String description;

    @Builder.Default
    private String spaceType = "global";

    private String creator;
}
