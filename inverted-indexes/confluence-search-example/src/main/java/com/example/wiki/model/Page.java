package com.example.wiki.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents a wiki page (or blog post, comment).
 * Equivalent to Confluence's CONTENT table.
 *
 * The actual body content is stored separately in PageBody (BODYCONTENT table)
 * for performance and versioning reasons.
 */
@Entity
@Table(name = "content", indexes = {
    @Index(name = "idx_content_space", columnList = "space_id"),
    @Index(name = "idx_content_parent", columnList = "parent_id"),
    @Index(name = "idx_content_status", columnList = "contentStatus"),
    @Index(name = "idx_content_lower_title", columnList = "lowerTitle")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Page {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long contentId;

    @Column(length = 50, nullable = false)
    @Builder.Default
    private String contentType = "PAGE";  // PAGE, BLOGPOST, COMMENT

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 255)
    private String lowerTitle;  // For case-insensitive search

    @Builder.Default
    private Integer version = 1;

    @Column(length = 255)
    private String creator;

    @Builder.Default
    private LocalDateTime creationDate = LocalDateTime.now();

    @Column(length = 255)
    private String lastModifier;

    private LocalDateTime lastModDate;

    @Column(columnDefinition = "TEXT")
    private String versionComment;

    @Column(length = 50)
    @Builder.Default
    private String contentStatus = "current";  // current, draft, deleted

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "space_id")
    private Space space;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Page parent;

    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Page> children = new HashSet<>();

    @OneToOne(mappedBy = "page", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private PageBody body;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "content_labels",
        joinColumns = @JoinColumn(name = "content_id"),
        inverseJoinColumns = @JoinColumn(name = "label_id")
    )
    @Builder.Default
    private Set<Label> labels = new HashSet<>();

    // Previous version for version history
    private Long prevVer;

    @PrePersist
    @PreUpdate
    public void updateLowerTitle() {
        if (title != null) {
            this.lowerTitle = title.toLowerCase();
        }
        if (lastModDate == null) {
            this.lastModDate = LocalDateTime.now();
        }
    }

    public void setBody(PageBody body) {
        this.body = body;
        if (body != null) {
            body.setPage(this);
        }
    }
}
