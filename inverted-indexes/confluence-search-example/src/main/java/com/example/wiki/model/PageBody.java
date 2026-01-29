package com.example.wiki.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Stores the actual content of a page in XHTML storage format.
 * Equivalent to Confluence's BODYCONTENT table.
 *
 * Separating body from page metadata allows:
 * - Faster queries that don't need full content
 * - Independent compression of body content
 * - Easier versioning
 */
@Entity
@Table(name = "bodycontent")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageBody {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bodyContentId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id", nullable = false)
    private Page page;

    @Column(columnDefinition = "TEXT")
    private String body;  // XHTML storage format content

    /**
     * Body type indicator:
     * 0 = Legacy wiki markup
     * 2 = XHTML storage format (current)
     */
    @Builder.Default
    private Integer bodyType = 2;
}
