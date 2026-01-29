# Implementation Plan: Confluence-like Page Storage and Search

## Overview

Build a Spring Boot application that demonstrates how Confluence stores and indexes wiki pages, using:
- **H2/PostgreSQL** for page storage (similar to Confluence's database model)
- **Apache Lucene** for full-text search indexing
- **XHTML parsing** for content extraction

---

## Project Structure

```
confluence-search-example/
├── pom.xml
├── src/
│   └── main/
│       ├── java/com/example/wiki/
│       │   ├── WikiApplication.java
│       │   │
│       │   ├── model/
│       │   │   ├── Space.java                 # SPACES table equivalent
│       │   │   ├── Page.java                  # CONTENT table equivalent
│       │   │   ├── PageBody.java              # BODYCONTENT table equivalent
│       │   │   ├── PageVersion.java           # Version history
│       │   │   ├── Label.java                 # Labels/tags
│       │   │   └── Attachment.java            # Attachment metadata
│       │   │
│       │   ├── storage/
│       │   │   ├── StorageFormat.java         # XHTML storage format constants
│       │   │   ├── StorageFormatParser.java   # Parse XHTML to plain text
│       │   │   ├── StorageFormatBuilder.java  # Build XHTML from editor input
│       │   │   └── MacroProcessor.java        # Handle macro elements
│       │   │
│       │   ├── repository/
│       │   │   ├── SpaceRepository.java
│       │   │   ├── PageRepository.java
│       │   │   └── LabelRepository.java
│       │   │
│       │   ├── search/
│       │   │   ├── WikiIndexer.java           # Convert pages to Lucene docs
│       │   │   ├── WikiSearchService.java     # Search functionality
│       │   │   ├── IndexFields.java           # Field name constants
│       │   │   ├── TextExtractor.java         # Extract text from XHTML
│       │   │   └── AsyncIndexer.java          # Background indexing queue
│       │   │
│       │   ├── service/
│       │   │   ├── SpaceService.java
│       │   │   ├── PageService.java           # CRUD + versioning
│       │   │   └── LabelService.java
│       │   │
│       │   ├── controller/
│       │   │   ├── SpaceController.java
│       │   │   ├── PageController.java
│       │   │   ├── SearchController.java
│       │   │   └── IndexController.java
│       │   │
│       │   └── dto/
│       │       ├── PageCreateRequest.java
│       │       ├── PageUpdateRequest.java
│       │       ├── SearchRequest.java
│       │       └── SearchResult.java
│       │
│       └── resources/
│           ├── application.properties
│           ├── schema.sql                     # Database schema
│           └── sample-pages.json              # Sample wiki pages
│
└── data/
    └── index/                                  # Lucene index directory
```

---

## Phase 1: Database Layer (Confluence Storage Model)

### Task 1.1: Define Entity Models

Create JPA entities that mirror Confluence's database structure:

**Space.java**
```java
@Entity
@Table(name = "spaces")
public class Space {
    @Id
    @GeneratedValue
    private Long spaceId;

    @Column(unique = true, nullable = false)
    private String spaceKey;        // e.g., "ENGINEERING"

    private String spaceName;       // e.g., "Engineering Team"
    private String description;
    private String spaceType;       // "global" or "personal"

    @OneToOne
    private Page homePage;

    private String creator;
    private LocalDateTime creationDate;
}
```

**Page.java**
```java
@Entity
@Table(name = "content")
public class Page {
    @Id
    @GeneratedValue
    private Long contentId;

    private String contentType;     // "PAGE", "BLOGPOST", "COMMENT"
    private String title;
    private String lowerTitle;      // For case-insensitive search
    private Integer version;

    private String creator;
    private LocalDateTime creationDate;
    private String lastModifier;
    private LocalDateTime lastModDate;
    private String versionComment;

    private String contentStatus;   // "current", "draft", "deleted"

    @ManyToOne
    private Space space;

    @ManyToOne
    private Page parent;            // Parent page for hierarchy

    @OneToOne(cascade = CascadeType.ALL)
    private PageBody body;

    @ManyToMany
    private Set<Label> labels = new HashSet<>();
}
```

**PageBody.java**
```java
@Entity
@Table(name = "bodycontent")
public class PageBody {
    @Id
    @GeneratedValue
    private Long bodyContentId;

    @Lob
    private String body;            // XHTML storage format

    private Integer bodyType;       // 2 = XHTML storage format
}
```

### Task 1.2: Create Database Schema

```sql
-- Spaces table
CREATE TABLE spaces (
    space_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    space_key VARCHAR(255) NOT NULL UNIQUE,
    space_name VARCHAR(255),
    description TEXT,
    space_type VARCHAR(50) DEFAULT 'global',
    home_page_id BIGINT,
    creator VARCHAR(255),
    creation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Content/Pages table
CREATE TABLE content (
    content_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    content_type VARCHAR(50) NOT NULL DEFAULT 'PAGE',
    title VARCHAR(255) NOT NULL,
    lower_title VARCHAR(255),
    version INT DEFAULT 1,
    creator VARCHAR(255),
    creation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_modifier VARCHAR(255),
    last_mod_date TIMESTAMP,
    version_comment TEXT,
    content_status VARCHAR(50) DEFAULT 'current',
    space_id BIGINT REFERENCES spaces(space_id),
    parent_id BIGINT REFERENCES content(content_id),
    prev_ver BIGINT REFERENCES content(content_id)
);

-- Body content table (stores XHTML)
CREATE TABLE bodycontent (
    body_content_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    content_id BIGINT NOT NULL REFERENCES content(content_id),
    body TEXT,
    body_type INT DEFAULT 2
);

-- Labels
CREATE TABLE labels (
    label_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    owner VARCHAR(255)
);

-- Content-Label mapping
CREATE TABLE content_labels (
    content_id BIGINT REFERENCES content(content_id),
    label_id BIGINT REFERENCES labels(label_id),
    PRIMARY KEY (content_id, label_id)
);

-- Indexes for common queries
CREATE INDEX idx_content_space ON content(space_id);
CREATE INDEX idx_content_parent ON content(parent_id);
CREATE INDEX idx_content_status ON content(content_status);
CREATE INDEX idx_content_lower_title ON content(lower_title);
```

---

## Phase 2: XHTML Storage Format

### Task 2.1: Storage Format Parser

Create a parser that extracts searchable text from Confluence-style XHTML:

```java
public class StorageFormatParser {

    /**
     * Extracts plain text from Confluence storage format XHTML.
     * Strips all markup, processes macros, extracts link text.
     */
    public String extractText(String xhtml) {
        // 1. Parse XHTML using Jsoup or similar
        // 2. Remove style, script elements
        // 3. Extract text from paragraphs, headings, lists
        // 4. Process macro content (code blocks, panels, etc.)
        // 5. Extract link body text
        // 6. Handle tables (extract cell content)
        // 7. Return normalized plain text
    }

    /**
     * Extracts headings for structure analysis.
     */
    public List<Heading> extractHeadings(String xhtml) {
        // Parse h1-h6 elements with their text and levels
    }

    /**
     * Extracts links for relationship tracking.
     */
    public List<PageLink> extractLinks(String xhtml) {
        // Parse ac:link elements, extract space-key and content-title
    }
}
```

### Task 2.2: Storage Format Builder

For creating/updating pages:

```java
public class StorageFormatBuilder {

    /**
     * Converts editor content to storage format.
     */
    public String buildStorageFormat(EditorContent content) {
        StringBuilder xhtml = new StringBuilder();

        for (Block block : content.getBlocks()) {
            switch (block.getType()) {
                case PARAGRAPH -> xhtml.append(buildParagraph(block));
                case HEADING -> xhtml.append(buildHeading(block));
                case CODE_BLOCK -> xhtml.append(buildCodeMacro(block));
                case TABLE -> xhtml.append(buildTable(block));
                case LIST -> xhtml.append(buildList(block));
                // ... etc
            }
        }

        return xhtml.toString();
    }

    private String buildCodeMacro(Block block) {
        return String.format("""
            <ac:structured-macro ac:name="code">
              <ac:parameter ac:name="language">%s</ac:parameter>
              <ac:plain-text-body><![CDATA[%s]]></ac:plain-text-body>
            </ac:structured-macro>
            """, block.getLanguage(), block.getContent());
    }
}
```

---

## Phase 3: Lucene Search Integration

### Task 3.1: Index Field Definitions

```java
public class IndexFields {
    // Identifiers
    public static final String CONTENT_ID = "contentId";
    public static final String CONTENT_TYPE = "contentType";
    public static final String SPACE_KEY = "spaceKey";

    // Text fields (full-text searchable)
    public static final String TITLE = "title";
    public static final String CONTENT = "content";        // Extracted text
    public static final String SPACE_NAME = "spaceName";
    public static final String LABEL_TEXT = "labelText";

    // Exact match fields
    public static final String CONTENT_STATUS = "contentStatus";
    public static final String CREATOR = "creator";
    public static final String LAST_MODIFIER = "lastModifier";

    // Date fields
    public static final String CREATED = "created";
    public static final String MODIFIED = "modified";

    // Hierarchical
    public static final String PARENT_ID = "parentId";
    public static final String ANCESTOR_IDS = "ancestorIds";

    // Combined search field
    public static final String ALL = "all";

    // Field boosts
    public static final Map<String, Float> BOOSTS = Map.of(
        TITLE, 3.0f,
        LABEL_TEXT, 2.0f,
        SPACE_NAME, 1.5f,
        CONTENT, 1.0f
    );
}
```

### Task 3.2: Wiki Page Indexer

```java
@Component
public class WikiIndexer {

    private final IndexWriter indexWriter;
    private final StorageFormatParser parser;

    public Document createDocument(Page page) {
        Document doc = new Document();

        // Identifiers
        doc.add(new StringField(CONTENT_ID,
            String.valueOf(page.getContentId()), Field.Store.YES));
        doc.add(new StringField(CONTENT_TYPE,
            page.getContentType(), Field.Store.YES));
        doc.add(new StringField(SPACE_KEY,
            page.getSpace().getSpaceKey(), Field.Store.YES));

        // Full-text searchable fields
        doc.add(new TextField(TITLE, page.getTitle(), Field.Store.YES));

        // Extract text from XHTML body
        String plainText = parser.extractText(page.getBody().getBody());
        doc.add(new TextField(CONTENT, plainText, Field.Store.NO));

        // Labels
        String labelText = page.getLabels().stream()
            .map(Label::getName)
            .collect(Collectors.joining(" "));
        doc.add(new TextField(LABEL_TEXT, labelText, Field.Store.YES));

        // Combined "all" field for simple searches
        String allText = String.join(" ",
            page.getTitle(),
            plainText,
            labelText,
            page.getSpace().getSpaceName());
        doc.add(new TextField(ALL, allText, Field.Store.NO));

        // Filters
        doc.add(new StringField(CONTENT_STATUS,
            page.getContentStatus(), Field.Store.NO));
        doc.add(new StringField(CREATOR,
            page.getCreator(), Field.Store.NO));

        // Dates for range queries
        doc.add(new LongPoint(CREATED,
            page.getCreationDate().toEpochSecond(ZoneOffset.UTC)));
        doc.add(new LongPoint(MODIFIED,
            page.getLastModDate().toEpochSecond(ZoneOffset.UTC)));

        // For sorting by date
        doc.add(new NumericDocValuesField(MODIFIED,
            page.getLastModDate().toEpochSecond(ZoneOffset.UTC)));

        // Hierarchical - store all ancestors for filtering
        addAncestors(doc, page);

        return doc;
    }

    private void addAncestors(Document doc, Page page) {
        Page parent = page.getParent();
        while (parent != null) {
            doc.add(new StringField(ANCESTOR_IDS,
                String.valueOf(parent.getContentId()), Field.Store.NO));
            parent = parent.getParent();
        }
    }

    public void indexPage(Page page) {
        Document doc = createDocument(page);
        Term idTerm = new Term(CONTENT_ID, String.valueOf(page.getContentId()));
        indexWriter.updateDocument(idTerm, doc);
    }

    public void deletePage(Long contentId) {
        Term idTerm = new Term(CONTENT_ID, String.valueOf(contentId));
        indexWriter.deleteDocuments(idTerm);
    }
}
```

### Task 3.3: Search Service

```java
@Service
public class WikiSearchService {

    private final SearcherManager searcherManager;
    private final Analyzer analyzer;

    public SearchResult search(SearchRequest request) {
        IndexSearcher searcher = searcherManager.acquire();
        try {
            Query query = buildQuery(request);

            // Execute search
            TopDocs topDocs = searcher.search(query, request.getSize());

            // Build results with highlighting
            List<PageHit> hits = new ArrayList<>();
            Highlighter highlighter = createHighlighter(query);

            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.doc(scoreDoc.doc);
                PageHit hit = new PageHit();
                hit.setContentId(Long.parseLong(doc.get(CONTENT_ID)));
                hit.setTitle(doc.get(TITLE));
                hit.setSpaceKey(doc.get(SPACE_KEY));
                hit.setScore(scoreDoc.score);
                hit.setHighlights(generateHighlights(highlighter, doc));
                hits.add(hit);
            }

            return new SearchResult(hits, topDocs.totalHits.value);

        } finally {
            searcherManager.release(searcher);
        }
    }

    private Query buildQuery(SearchRequest request) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();

        // Main text query with boosting
        if (request.getQuery() != null) {
            MultiFieldQueryParser parser = new MultiFieldQueryParser(
                new String[]{TITLE, CONTENT, LABEL_TEXT, SPACE_NAME},
                analyzer,
                IndexFields.BOOSTS
            );
            Query textQuery = parser.parse(request.getQuery());
            builder.add(textQuery, BooleanClause.Occur.MUST);
        }

        // Space filter
        if (request.getSpaceKey() != null) {
            builder.add(
                new TermQuery(new Term(SPACE_KEY, request.getSpaceKey())),
                BooleanClause.Occur.FILTER
            );
        }

        // Content type filter
        if (request.getContentType() != null) {
            builder.add(
                new TermQuery(new Term(CONTENT_TYPE, request.getContentType())),
                BooleanClause.Occur.FILTER
            );
        }

        // Label filter
        if (request.getLabels() != null && !request.getLabels().isEmpty()) {
            BooleanQuery.Builder labelQuery = new BooleanQuery.Builder();
            for (String label : request.getLabels()) {
                labelQuery.add(
                    new TermQuery(new Term(LABEL_TEXT, label.toLowerCase())),
                    BooleanClause.Occur.SHOULD
                );
            }
            builder.add(labelQuery.build(), BooleanClause.Occur.FILTER);
        }

        // Date range filter
        if (request.getModifiedAfter() != null) {
            builder.add(
                LongPoint.newRangeQuery(MODIFIED,
                    request.getModifiedAfter().toEpochSecond(ZoneOffset.UTC),
                    Long.MAX_VALUE),
                BooleanClause.Occur.FILTER
            );
        }

        // Always filter to current content
        builder.add(
            new TermQuery(new Term(CONTENT_STATUS, "current")),
            BooleanClause.Occur.FILTER
        );

        return builder.build();
    }
}
```

### Task 3.4: Async Indexer (like Confluence's queue)

```java
@Component
public class AsyncIndexer {

    private final BlockingQueue<IndexEvent> queue = new LinkedBlockingQueue<>();
    private final WikiIndexer indexer;
    private final ScheduledExecutorService scheduler;

    @PostConstruct
    public void start() {
        // Process queue every 5 seconds (like Confluence)
        scheduler.scheduleAtFixedRate(this::processQueue, 5, 5, TimeUnit.SECONDS);
    }

    public void queueIndex(Page page) {
        queue.offer(new IndexEvent(IndexEventType.UPDATE, page));
    }

    public void queueDelete(Long contentId) {
        queue.offer(new IndexEvent(IndexEventType.DELETE, contentId));
    }

    private void processQueue() {
        List<IndexEvent> events = new ArrayList<>();
        queue.drainTo(events, 100); // Batch of up to 100

        if (events.isEmpty()) return;

        for (IndexEvent event : events) {
            switch (event.getType()) {
                case UPDATE -> indexer.indexPage(event.getPage());
                case DELETE -> indexer.deletePage(event.getContentId());
            }
        }

        indexer.commit();
        indexer.refresh(); // Make searchable
    }
}
```

---

## Phase 4: REST API

### Task 4.1: Page Controller

```java
@RestController
@RequestMapping("/api/pages")
public class PageController {

    @PostMapping
    public Page createPage(@RequestBody PageCreateRequest request) {
        // 1. Validate request
        // 2. Build storage format from content
        // 3. Save to database (CONTENT + BODYCONTENT tables)
        // 4. Queue for indexing
        // 5. Return created page
    }

    @PutMapping("/{pageId}")
    public Page updatePage(@PathVariable Long pageId,
                           @RequestBody PageUpdateRequest request) {
        // 1. Load existing page
        // 2. Create new version (copy to PREVVER)
        // 3. Update content
        // 4. Queue for re-indexing
        // 5. Return updated page
    }

    @GetMapping("/{pageId}")
    public Page getPage(@PathVariable Long pageId) {
        // Return page with body content
    }

    @GetMapping("/{pageId}/storage-format")
    public String getStorageFormat(@PathVariable Long pageId) {
        // Return raw XHTML (like Confluence's "View Storage Format")
    }

    @DeleteMapping("/{pageId}")
    public void deletePage(@PathVariable Long pageId) {
        // 1. Mark as deleted (don't actually delete)
        // 2. Queue for index removal
    }
}
```

### Task 4.2: Search Controller

```java
@RestController
@RequestMapping("/api/search")
public class SearchController {

    @GetMapping
    public SearchResult search(
            @RequestParam String q,
            @RequestParam(required = false) String spaceKey,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) List<String> labels,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        SearchRequest request = SearchRequest.builder()
            .query(q)
            .spaceKey(spaceKey)
            .contentType(type)
            .labels(labels)
            .page(page)
            .size(size)
            .build();

        return searchService.search(request);
    }

    @PostMapping
    public SearchResult searchAdvanced(@RequestBody SearchRequest request) {
        return searchService.search(request);
    }
}
```

---

## Phase 5: Sample Data

### Task 5.1: Sample Wiki Pages

Create `sample-pages.json` with realistic wiki content:

```json
{
  "spaces": [
    {
      "spaceKey": "ENGINEERING",
      "spaceName": "Engineering Team",
      "description": "Technical documentation and guides"
    },
    {
      "spaceKey": "HR",
      "spaceName": "Human Resources",
      "description": "HR policies and procedures"
    }
  ],
  "pages": [
    {
      "spaceKey": "ENGINEERING",
      "title": "Getting Started Guide",
      "labels": ["onboarding", "guide", "new-hire"],
      "body": "<h1>Welcome to Engineering</h1><p>This guide will help you...</p>..."
    },
    {
      "spaceKey": "ENGINEERING",
      "title": "Code Review Guidelines",
      "labels": ["process", "code-review", "best-practices"],
      "body": "<h1>Code Review Process</h1><p>All code changes must...</p>..."
    }
  ]
}
```

---

## Implementation Order

| Phase | Task | Description | Dependencies |
|-------|------|-------------|--------------|
| 1 | 1.1 | Entity models | None |
| 1 | 1.2 | Database schema | 1.1 |
| 2 | 2.1 | Storage format parser | None |
| 2 | 2.2 | Storage format builder | 2.1 |
| 3 | 3.1 | Index field definitions | None |
| 3 | 3.2 | Wiki indexer | 1.1, 2.1, 3.1 |
| 3 | 3.3 | Search service | 3.1, 3.2 |
| 3 | 3.4 | Async indexer | 3.2 |
| 4 | 4.1 | Page controller | 1.*, 2.*, 3.4 |
| 4 | 4.2 | Search controller | 3.3 |
| 5 | 5.1 | Sample data | 4.* |

---

## API Examples

### Create a Page

```bash
POST /api/pages
Content-Type: application/json

{
  "spaceKey": "ENGINEERING",
  "title": "Architecture Overview",
  "parentId": 12345,
  "labels": ["architecture", "design"],
  "body": {
    "type": "storage",
    "value": "<h1>System Architecture</h1><p>Our system consists of...</p>"
  }
}
```

### Search

```bash
# Simple text search
GET /api/search?q=architecture+overview

# Search in specific space
GET /api/search?q=deployment&spaceKey=ENGINEERING

# Search with label filter
GET /api/search?q=guide&labels=onboarding,new-hire

# Advanced search
POST /api/search
{
  "query": "kubernetes deployment",
  "spaceKey": "ENGINEERING",
  "contentType": "PAGE",
  "labels": ["infrastructure"],
  "modifiedAfter": "2024-01-01T00:00:00Z",
  "sort": "modified",
  "order": "desc"
}
```

### Response

```json
{
  "hits": [
    {
      "contentId": 12345,
      "title": "Kubernetes Deployment Guide",
      "spaceKey": "ENGINEERING",
      "spaceName": "Engineering Team",
      "score": 5.234,
      "highlights": {
        "title": "<mark>Kubernetes</mark> <mark>Deployment</mark> Guide",
        "content": "...how to <mark>deploy</mark> to <mark>Kubernetes</mark>..."
      },
      "labels": ["kubernetes", "deployment", "infrastructure"],
      "lastModified": "2024-03-15T10:30:00Z"
    }
  ],
  "totalHits": 42,
  "page": 0,
  "size": 20,
  "searchTimeMs": 15
}
```

---

## Key Learnings Demonstrated

1. **Two-table content model**: Separating metadata (CONTENT) from body (BODYCONTENT)
2. **XHTML storage format**: Rich content stored as XML with custom macro elements
3. **Text extraction**: Converting markup to searchable plain text
4. **Multi-field indexing**: Different field types for different search needs
5. **Async indexing**: Queue-based batch processing for performance
6. **Hierarchical search**: Ancestor tracking for filtering by parent pages
7. **Faceted search**: Labels, spaces, content types as facets
8. **Highlighting**: Showing matched terms in context
