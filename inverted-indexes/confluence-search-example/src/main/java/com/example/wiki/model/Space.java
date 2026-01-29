package com.example.wiki.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Represents a Confluence-like Space.
 * Spaces are containers for organizing related pages.
 * Equivalent to Confluence's SPACES table.
 */
@Entity
@Table(name = "spaces")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Space {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long spaceId;

    @Column(unique = true, nullable = false, length = 255)
    private String spaceKey;  // e.g., "ENGINEERING", "HR"

    @Column(length = 255)
    private String spaceName;  // e.g., "Engineering Team"

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 50)
    @Builder.Default
    private String spaceType = "global";  // "global" or "personal"

    @Column(length = 255)
    private String creator;

    @Builder.Default
    private LocalDateTime creationDate = LocalDateTime.now();

    // Home page reference (set after page creation)
    private Long homePageId;
}
