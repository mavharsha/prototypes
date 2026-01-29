package com.example.wiki.search;

import java.util.Map;

/**
 * Lucene index field definitions.
 * Mirrors the fields that Confluence indexes for search.
 */
public final class IndexFields {

    private IndexFields() {}

    // ===== Identifier Fields (StringField - exact match) =====

    /** Unique page/content identifier */
    public static final String CONTENT_ID = "contentId";

    /** Content type: PAGE, BLOGPOST, COMMENT */
    public static final String CONTENT_TYPE = "contentType";

    /** Space key for filtering */
    public static final String SPACE_KEY = "spaceKey";

    /** Content status: current, draft, deleted */
    public static final String CONTENT_STATUS = "contentStatus";

    /** Creator user identifier */
    public static final String CREATOR = "creator";

    /** Last modifier user identifier */
    public static final String LAST_MODIFIER = "lastModifier";

    /** Parent page ID for hierarchy */
    public static final String PARENT_ID = "parentId";

    /** All ancestor page IDs (multi-valued) */
    public static final String ANCESTOR_IDS = "ancestorIds";

    // ===== Text Fields (TextField - full-text searchable) =====

    /** Page title - highest boost */
    public static final String TITLE = "title";

    /** Extracted text from page body */
    public static final String CONTENT = "content";

    /** Space display name */
    public static final String SPACE_NAME = "spaceName";

    /** Labels/tags combined text */
    public static final String LABEL_TEXT = "labelText";

    /** Combined field for simple searches */
    public static final String ALL = "all";

    // ===== Date Fields (LongPoint - range queries) =====

    /** Creation timestamp */
    public static final String CREATED = "created";

    /** Last modification timestamp */
    public static final String MODIFIED = "modified";

    // ===== Facet Fields (for faceted search) =====

    public static final String SPACE_KEY_FACET = "spaceKey_facet";
    public static final String CONTENT_TYPE_FACET = "contentType_facet";
    public static final String LABEL_FACET = "label_facet";

    // ===== Field Boosts =====

    /** Default boost values for multi-field search */
    public static final Map<String, Float> BOOSTS = Map.of(
        TITLE, 3.0f,          // Title matches are most important
        LABEL_TEXT, 2.0f,     // Label matches are highly relevant
        SPACE_NAME, 1.5f,     // Space name provides context
        CONTENT, 1.0f         // Body content is baseline
    );

    /** Fields to search by default */
    public static final String[] DEFAULT_SEARCH_FIELDS = {
        TITLE, CONTENT, LABEL_TEXT, SPACE_NAME
    };
}
