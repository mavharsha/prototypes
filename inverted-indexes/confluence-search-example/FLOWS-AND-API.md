# Wiki Search: Flows and API Documentation

This document describes the data flows and API endpoints for the Confluence-like wiki search system.

---

## Table of Contents

1. [System Overview](#system-overview)
2. [Data Flows](#data-flows)
   - [Page Creation Flow](#1-page-creation-flow)
   - [Page Update Flow](#2-page-update-flow)
   - [Search Flow](#3-search-flow)
   - [Async Indexing Flow](#4-async-indexing-flow)
   - [Reindex Flow](#5-reindex-flow)
3. [API Reference](#api-reference)
   - [Spaces API](#spaces-api)
   - [Pages API](#pages-api)
   - [Search API](#search-api)
   - [Index API](#index-api)
4. [Data Models](#data-models)

---

## System Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Client Request                                  │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           REST Controllers                                   │
│  ┌─────────────┐  ┌─────────────┐  ┌───────────────┐  ┌─────────────────┐  │
│  │   Space     │  │    Page     │  │    Search     │  │     Index       │  │
│  │ Controller  │  │ Controller  │  │  Controller   │  │   Controller    │  │
│  └─────────────┘  └─────────────┘  └───────────────┘  └─────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
                    │                       │                    │
                    ▼                       │                    │
┌───────────────────────────────────┐       │                    │
│          Service Layer            │       │                    │
│  ┌───────────┐  ┌──────────────┐ │       │                    │
│  │  Space    │  │    Page      │ │       │                    │
│  │  Service  │  │   Service    │ │       │                    │
│  └───────────┘  └──────────────┘ │       │                    │
└───────────────────────────────────┘       │                    │
         │              │                   │                    │
         │              │                   ▼                    ▼
         │              │         ┌─────────────────────────────────────────┐
         │              │         │           Search Layer                  │
         │              │         │  ┌─────────────┐  ┌──────────────────┐ │
         │              │         │  │   Wiki      │  │   Wiki Search    │ │
         │              │         │  │  Indexer    │  │    Service       │ │
         │              │         │  └─────────────┘  └──────────────────┘ │
         │              │         │         │                   │          │
         │              │         │         ▼                   │          │
         │              │         │  ┌─────────────┐            │          │
         │              │         │  │   Async     │            │          │
         │              │         │  │  Indexer    │            │          │
         │              │         │  │  (Queue)    │            │          │
         │              │         │  └─────────────┘            │          │
         │              │         └─────────────────────────────────────────┘
         │              │                   │                   │
         ▼              ▼                   ▼                   ▼
┌────────────────────────────┐    ┌────────────────────────────────────────┐
│      PostgreSQL            │    │         Lucene Index                    │
│  ┌────────┐  ┌───────────┐│    │      ./data/index/                      │
│  │ SPACES │  │  CONTENT  ││    │  ┌─────────────────────────────────┐   │
│  └────────┘  └───────────┘│    │  │  Inverted Index (segments)      │   │
│             ┌─────────────┐│    │  │  - title (TextField, boost 3x) │   │
│             │ BODYCONTENT ││    │  │  - content (TextField)         │   │
│             └─────────────┘│    │  │  - labels (TextField, boost 2x)│   │
│  ┌────────┐  ┌───────────┐│    │  │  - spaceKey (StringField)      │   │
│  │ LABELS │  │  CONTENT  ││    │  └─────────────────────────────────┘   │
│  └────────┘  │  _LABELS  ││    └────────────────────────────────────────┘
│              └───────────┘│
└────────────────────────────┘
```

---

## Data Flows

### 1. Page Creation Flow

When a user creates a new wiki page:

```
┌──────────┐     POST /api/pages      ┌────────────────┐
│  Client  │ ──────────────────────▶  │ PageController │
└──────────┘                          └────────────────┘
                                              │
                                              ▼
                                      ┌────────────────┐
                                      │  PageService   │
                                      │  createPage()  │
                                      └────────────────┘
                                              │
                    ┌─────────────────────────┼─────────────────────────┐
                    │                         │                         │
                    ▼                         ▼                         ▼
           ┌────────────────┐        ┌────────────────┐        ┌────────────────┐
           │  SpaceService  │        │  LabelService  │        │ StorageFormat  │
           │  getSpace()    │        │ getOrCreate()  │        │   Builder      │
           └────────────────┘        └────────────────┘        └────────────────┘
                    │                         │                         │
                    │                         │            (if plain text input)
                    │                         │                         │
                    └─────────────────────────┼─────────────────────────┘
                                              │
                                              ▼
                                      ┌────────────────┐
                                      │ PageRepository │
                                      │    save()      │
                                      └────────────────┘
                                              │
                    ┌─────────────────────────┼─────────────────────────┐
                    │                         │                         │
                    ▼                         ▼                         ▼
           ┌────────────────┐        ┌────────────────┐        ┌────────────────┐
           │    SPACES      │        │    CONTENT     │        │  BODYCONTENT   │
           │    (lookup)    │        │   (INSERT)     │        │   (INSERT)     │
           └────────────────┘        └────────────────┘        └────────────────┘
                                              │
                                              ▼
                                      ┌────────────────┐
                                      │  AsyncIndexer  │
                                      │  queueIndex()  │
                                      └────────────────┘
                                              │
                                              ▼
                                      ┌────────────────┐
                                      │  Index Queue   │
                                      │ (in-memory)    │
                                      └────────────────┘
                                              │
                                              ▼
                                        Response: Page
```

**Detailed Steps:**

| Step | Component | Action |
|------|-----------|--------|
| 1 | PageController | Receives POST request with `PageCreateRequest` |
| 2 | PageService | Validates request, checks for duplicate title |
| 3 | SpaceService | Looks up space by `spaceKey` |
| 4 | LabelService | Creates any new labels, returns label set |
| 5 | StorageFormatBuilder | Converts plain text to XHTML (if needed) |
| 6 | PageRepository | Saves Page entity (cascades to PageBody) |
| 7 | PostgreSQL | INSERTs into CONTENT and BODYCONTENT tables |
| 8 | AsyncIndexer | Queues page for background indexing |
| 9 | Response | Returns created Page with ID |

---

### 2. Page Update Flow

When a user updates an existing page:

```
┌──────────┐    PUT /api/pages/{id}   ┌────────────────┐
│  Client  │ ──────────────────────▶  │ PageController │
└──────────┘                          └────────────────┘
                                              │
                                              ▼
                                      ┌────────────────┐
                                      │  PageService   │
                                      │  updatePage()  │
                                      └────────────────┘
                                              │
                                              ▼
                                      ┌────────────────┐
                                      │ PageRepository │
                                      │ findById       │
                                      │ WithBodyAnd    │
                                      │ Labels()       │
                                      └────────────────┘
                                              │
                                              ▼
                                      ┌────────────────┐
                                      │  Update Page:  │
                                      │  - title       │
                                      │  - body.body   │
                                      │  - labels      │
                                      │  - version++   │
                                      │  - lastModDate │
                                      └────────────────┘
                                              │
                                              ▼
                                      ┌────────────────┐
                                      │ PageRepository │
                                      │    save()      │
                                      └────────────────┘
                                              │
                    ┌─────────────────────────┴─────────────────────────┐
                    ▼                                                   ▼
           ┌────────────────┐                                  ┌────────────────┐
           │    CONTENT     │                                  │  BODYCONTENT   │
           │    (UPDATE)    │                                  │   (UPDATE)     │
           │  version: 2    │                                  │  new XHTML     │
           └────────────────┘                                  └────────────────┘
                                              │
                                              ▼
                                      ┌────────────────┐
                                      │  AsyncIndexer  │
                                      │  queueIndex()  │──▶ Re-index updated page
                                      └────────────────┘
```

**Version Tracking:**

```sql
-- Before update
content_id: 5, title: "API Design", version: 1

-- After update
content_id: 5, title: "API Design Standards", version: 2
```

---

### 3. Search Flow

When a user performs a search:

```
┌──────────┐   GET /api/search?q=kubernetes   ┌──────────────────┐
│  Client  │ ──────────────────────────────▶  │ SearchController │
└──────────┘                                  └──────────────────┘
                                                       │
                                                       ▼
                                              ┌──────────────────┐
                                              │ WikiSearchService│
                                              │    search()      │
                                              └──────────────────┘
                                                       │
                          ┌────────────────────────────┼────────────────────────────┐
                          │                            │                            │
                          ▼                            ▼                            ▼
                 ┌──────────────────┐        ┌──────────────────┐        ┌──────────────────┐
                 │   buildQuery()   │        │   buildSort()    │        │SearcherManager   │
                 │                  │        │                  │        │   acquire()      │
                 │ BooleanQuery:    │        │ Sort by:         │        └──────────────────┘
                 │ - text query     │        │ - relevance      │                  │
                 │ - space filter   │        │ - modified       │                  │
                 │ - type filter    │        │ - title          │                  │
                 │ - label filter   │        └──────────────────┘                  │
                 │ - date filter    │                                              │
                 │ - status=current │                                              │
                 └──────────────────┘                                              │
                          │                                                        │
                          └────────────────────────────┬───────────────────────────┘
                                                       │
                                                       ▼
                                              ┌──────────────────┐
                                              │  IndexSearcher   │
                                              │    search()      │
                                              └──────────────────┘
                                                       │
                                                       ▼
                                              ┌──────────────────┐
                                              │   Lucene Index   │
                                              │   (segments)     │
                                              └──────────────────┘
                                                       │
                                                       ▼
                                              ┌──────────────────┐
                                              │    TopDocs       │
                                              │  (scored hits)   │
                                              └──────────────────┘
                                                       │
                          ┌────────────────────────────┼────────────────────────────┐
                          │                            │                            │
                          ▼                            ▼                            ▼
                 ┌──────────────────┐        ┌──────────────────┐        ┌──────────────────┐
                 │  Load stored     │        │  Highlighter     │        │  Build facets    │
                 │  fields from     │        │  getBestFragment │        │  (space, type)   │
                 │  documents       │        │  <mark>term</mark>│        │                  │
                 └──────────────────┘        └──────────────────┘        └──────────────────┘
                          │                            │                            │
                          └────────────────────────────┼────────────────────────────┘
                                                       │
                                                       ▼
                                              ┌──────────────────┐
                                              │  SearchResult    │
                                              │  - hits[]        │
                                              │  - totalHits     │
                                              │  - facets{}      │
                                              │  - searchTimeMs  │
                                              └──────────────────┘
```

**Query Building Detail:**

```java
// Input: q=kubernetes&spaceKey=DEVOPS&labels=deployment

BooleanQuery:
├── MUST: MultiFieldQuery("kubernetes")
│         ├── title (boost: 3.0)
│         ├── content (boost: 1.0)
│         ├── labelText (boost: 2.0)
│         └── spaceName (boost: 1.5)
│
├── FILTER: TermQuery(spaceKey="DEVOPS")
│
├── FILTER: TermQuery(label_facet="deployment")
│
└── FILTER: TermQuery(contentStatus="current")
```

**Scoring Formula (simplified):**

```
score = Σ (tf × idf × boost × norm)

Where:
- tf    = term frequency in document
- idf   = inverse document frequency (rarer terms score higher)
- boost = field boost (title: 3x, labels: 2x, spaceName: 1.5x, content: 1x)
- norm  = length normalization
```

---

### 4. Async Indexing Flow

Background indexing processes changes every 5 seconds:

```
┌─────────────────────────────────────────────────────────────────┐
│                        Index Queue                               │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐   │
│  │ UPDATE  │ │ UPDATE  │ │ DELETE  │ │ UPDATE  │ │ UPDATE  │   │
│  │ Page 1  │ │ Page 2  │ │ Page 5  │ │ Page 3  │ │ Page 7  │   │
│  └─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ @Scheduled(fixedDelay = 5000ms)
                              ▼
                     ┌─────────────────┐
                     │  AsyncIndexer   │
                     │  processQueue() │
                     └─────────────────┘
                              │
                              │ drainTo(events, 100. max batch)
                              ▼
                     ┌─────────────────┐
                     │  For each event │
                     └─────────────────┘
                              │
              ┌───────────────┴───────────────┐
              │                               │
              ▼                               ▼
     ┌─────────────────┐             ┌─────────────────┐
     │ UPDATE event    │             │ DELETE event    │
     │                 │             │                 │
     │ WikiIndexer     │             │ WikiIndexer     │
     │  .indexPage()   │             │  .deletePage()  │
     └─────────────────┘             └─────────────────┘
              │                               │
              └───────────────┬───────────────┘
                              │
                              ▼
                     ┌─────────────────┐
                     │  WikiIndexer    │
                     │   .commit()     │──▶ Write to disk
                     │   .refresh()    │──▶ Make searchable
                     └─────────────────┘
                              │
                              ▼
                     ┌─────────────────┐
                     │  Lucene Index   │
                     │  (updated)      │
                     └─────────────────┘
```

**Timing:**

| Event | Time |
|-------|------|
| Page created | T+0ms |
| Added to queue | T+1ms |
| Response returned | T+5ms |
| Queue processed | T+5000ms (next batch) |
| Searchable | T+5050ms |

---

### 5. Reindex Flow

Full reindex of all pages:

```
┌──────────┐   POST /api/index/reindex   ┌─────────────────┐
│  Client  │ ─────────────────────────▶  │ IndexController │
└──────────┘                             └─────────────────┘
                                                  │
                                                  ▼
                                         ┌─────────────────┐
                                         │  WikiIndexer    │
                                         │  clearIndex()   │
                                         └─────────────────┘
                                                  │
                                                  ▼
                                         ┌─────────────────┐
                                         │ PageRepository  │
                                         │ findAllCurrent  │
                                         │ PagesWithDetails│
                                         └─────────────────┘
                                                  │
                                                  ▼
                                         ┌─────────────────┐
                                         │   PostgreSQL    │
                                         │                 │
                                         │ SELECT p.*,     │
                                         │   b.body,       │
                                         │   l.name        │
                                         │ FROM content p  │
                                         │ JOIN bodycontent│
                                         │ JOIN labels     │
                                         └─────────────────┘
                                                  │
                                                  ▼
                                         ┌─────────────────┐
                                         │ For each page:  │
                                         │ WikiIndexer     │
                                         │  .indexPage()   │
                                         └─────────────────┘
                                                  │
                              ┌───────────────────┴───────────────────┐
                              │                                       │
                              ▼                                       ▼
                     ┌─────────────────┐                     ┌─────────────────┐
                     │ StorageFormat   │                     │ createDocument()│
                     │ Parser          │                     │                 │
                     │ .extractText()  │                     │ - StringFields  │
                     │                 │                     │ - TextFields    │
                     │ XHTML ──▶ plain │                     │ - LongPoints    │
                     └─────────────────┘                     └─────────────────┘
                              │                                       │
                              └───────────────────┬───────────────────┘
                                                  │
                                                  ▼
                                         ┌─────────────────┐
                                         │  IndexWriter    │
                                         │ .updateDocument │
                                         └─────────────────┘
                                                  │
                                                  ▼
                                         ┌─────────────────┐
                                         │  .commit()      │
                                         │  .refresh()     │
                                         └─────────────────┘
                                                  │
                                                  ▼
                                         ┌─────────────────┐
                                         │  Response:      │
                                         │  {indexed: 15,  │
                                         │   durationMs:   │
                                         │   134}          │
                                         └─────────────────┘
```

---

## API Reference

### Spaces API

#### Create Space

```http
POST /api/spaces
Content-Type: application/json

{
  "spaceKey": "ENGINEERING",
  "spaceName": "Engineering Team",
  "description": "Technical documentation",
  "spaceType": "global",
  "creator": "admin"
}
```

**Response:** `201 Created`
```json
{
  "spaceId": 1,
  "spaceKey": "ENGINEERING",
  "spaceName": "Engineering Team",
  "description": "Technical documentation",
  "spaceType": "global",
  "creator": "admin",
  "creationDate": "2024-01-15T10:30:00"
}
```

**Validation:**
- `spaceKey`: Required, must match `^[A-Z][A-Z0-9_]*$`
- `spaceName`: Required

---

#### List Spaces

```http
GET /api/spaces
```

**Response:** `200 OK`
```json
[
  {
    "spaceId": 1,
    "spaceKey": "ENGINEERING",
    "spaceName": "Engineering Team",
    ...
  },
  {
    "spaceId": 2,
    "spaceKey": "HR",
    "spaceName": "Human Resources",
    ...
  }
]
```

---

#### Get Space

```http
GET /api/spaces/{spaceKey}
```

**Example:** `GET /api/spaces/ENGINEERING`

**Response:** `200 OK`
```json
{
  "spaceId": 1,
  "spaceKey": "ENGINEERING",
  "spaceName": "Engineering Team",
  "description": "Technical documentation",
  "spaceType": "global",
  "creator": "admin",
  "creationDate": "2024-01-15T10:30:00"
}
```

**Errors:**
- `400 Bad Request`: Space not found

---

#### Update Space

```http
PUT /api/spaces/{spaceKey}?spaceName=New Name&description=New description
```

**Response:** `200 OK` with updated space

---

#### Delete Space

```http
DELETE /api/spaces/{spaceKey}
```

**Response:** `204 No Content`

---

### Pages API

#### Create Page

```http
POST /api/pages
Content-Type: application/json

{
  "spaceKey": "ENGINEERING",
  "title": "Getting Started Guide",
  "parentId": null,
  "body": "Welcome to the engineering team...",
  "storageFormat": false,
  "labels": ["onboarding", "guide"],
  "contentType": "PAGE",
  "creator": "jsmith"
}
```

**Parameters:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| spaceKey | string | Yes | Space to create page in |
| title | string | Yes | Page title |
| parentId | long | No | Parent page ID for hierarchy |
| body | string | No | Page content |
| storageFormat | boolean | No | If true, body is XHTML; if false, plain text converted to XHTML |
| labels | string[] | No | Labels to attach |
| contentType | string | No | "PAGE" (default) or "BLOGPOST" |
| creator | string | No | Username of creator |

**Response:** `201 Created`
```json
{
  "contentId": 5,
  "contentType": "PAGE",
  "title": "Getting Started Guide",
  "version": 1,
  "creator": "jsmith",
  "creationDate": "2024-01-15T10:30:00",
  "lastModifier": "jsmith",
  "lastModDate": "2024-01-15T10:30:00",
  "contentStatus": "current",
  "space": {
    "spaceId": 1,
    "spaceKey": "ENGINEERING"
  },
  "labels": [
    {"labelId": 1, "name": "onboarding"},
    {"labelId": 2, "name": "guide"}
  ]
}
```

---

#### Get Page

```http
GET /api/pages/{pageId}
```

**Example:** `GET /api/pages/5`

**Response:** `200 OK`
```json
{
  "contentId": 5,
  "title": "Getting Started Guide",
  "contentType": "PAGE",
  "version": 1,
  "contentStatus": "current",
  "body": {
    "bodyContentId": 5,
    "body": "<h1>Welcome</h1><p>...</p>",
    "bodyType": 2
  },
  "labels": [...],
  "space": {...}
}
```

---

#### Get Storage Format

Returns the raw XHTML storage format (like Confluence's "View Storage Format").

```http
GET /api/pages/{pageId}/storage-format
```

**Response:** `200 OK`
```xml
<h1>Getting Started Guide</h1>
<p>Welcome to the engineering team!</p>
<ac:structured-macro ac:name="code" ac:schema-version="1">
  <ac:parameter ac:name="language">bash</ac:parameter>
  <ac:plain-text-body><![CDATA[git clone repo.git]]></ac:plain-text-body>
</ac:structured-macro>
```

---

#### Update Page

```http
PUT /api/pages/{pageId}
Content-Type: application/json

{
  "title": "Updated Title",
  "body": "Updated content...",
  "storageFormat": false,
  "labels": ["updated", "guide"],
  "versionComment": "Fixed typos",
  "modifier": "mjohnson"
}
```

**Response:** `200 OK` with updated page (version incremented)

---

#### Delete Page

Soft delete (sets `contentStatus` to "deleted").

```http
DELETE /api/pages/{pageId}
```

**Response:** `204 No Content`

---

#### List Pages by Space

```http
GET /api/pages/space/{spaceKey}
```

**Example:** `GET /api/pages/space/ENGINEERING`

**Response:** `200 OK`
```json
[
  {"contentId": 1, "title": "Engineering Home", ...},
  {"contentId": 2, "title": "Getting Started Guide", ...}
]
```

---

#### Get Child Pages

```http
GET /api/pages/{pageId}/children
```

**Response:** `200 OK` with list of child pages

---

### Search API

#### Simple Search (GET)

```http
GET /api/search?q={query}&spaceKey={space}&type={type}&labels={labels}&sort={field}&order={dir}&page={n}&size={n}
```

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| q | string | No | Full-text search query |
| spaceKey | string | No | Filter by space |
| type | string | No | Filter by content type (PAGE, BLOGPOST) |
| labels | string[] | No | Filter by labels (comma-separated) |
| creator | string | No | Filter by creator |
| sort | string | No | Sort field: `relevance`, `modified`, `created`, `title` |
| order | string | No | Sort order: `asc`, `desc` |
| page | int | No | Page number (0-based, default: 0) |
| size | int | No | Results per page (default: 20) |

**Examples:**

```http
# Simple text search
GET /api/search?q=kubernetes

# Search in specific space
GET /api/search?q=deployment&spaceKey=DEVOPS

# Search with multiple filters
GET /api/search?q=guide&spaceKey=ENGINEERING&labels=onboarding,new-hire

# Sort by modification date
GET /api/search?q=policy&sort=modified&order=desc

# Pagination
GET /api/search?q=documentation&page=1&size=10
```

---

#### Advanced Search (POST)

```http
POST /api/search
Content-Type: application/json

{
  "query": "kubernetes deployment",
  "spaceKey": "DEVOPS",
  "contentType": "PAGE",
  "labels": ["infrastructure", "k8s"],
  "creator": "dpark",
  "createdAfter": "2024-01-01T00:00:00",
  "modifiedAfter": "2024-06-01T00:00:00",
  "ancestorId": 7,
  "sort": "modified",
  "order": "desc",
  "page": 0,
  "size": 20
}
```

**Additional Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| createdAfter | datetime | Filter pages created after this date |
| modifiedAfter | datetime | Filter pages modified after this date |
| ancestorId | long | Search within subtree of this page |

---

#### Search Response

```json
{
  "hits": [
    {
      "contentId": 8,
      "title": "Kubernetes Deployment Guide",
      "spaceKey": "DEVOPS",
      "spaceName": "DevOps",
      "contentType": "PAGE",
      "labels": "kubernetes deployment k8s infrastructure",
      "score": 6.264635,
      "created": 1705312200,
      "modified": 1705312200,
      "highlights": {
        "title": "<mark>Kubernetes</mark> <mark>Deployment</mark> Guide"
      }
    }
  ],
  "totalHits": 3,
  "page": 0,
  "size": 20,
  "totalPages": 1,
  "facets": {
    "space": [
      {"value": "DEVOPS", "count": 2},
      {"value": "ENGINEERING", "count": 1}
    ],
    "contentType": [
      {"value": "PAGE", "count": 3}
    ]
  },
  "searchTimeMs": 12
}
```

**Response Fields:**

| Field | Description |
|-------|-------------|
| hits | Array of matching pages |
| hits[].score | Relevance score (higher = better match) |
| hits[].highlights | HTML fragments with `<mark>` tags on matched terms |
| totalHits | Total number of matching documents |
| page | Current page number |
| size | Results per page |
| totalPages | Total number of pages |
| facets | Counts by space and content type |
| searchTimeMs | Search execution time in milliseconds |

---

### Index API

#### Get Index Status

```http
GET /api/index/status
```

**Response:** `200 OK`
```json
{
  "indexedDocuments": 15,
  "pendingIndexEvents": 0,
  "totalPages": 15
}
```

---

#### Reindex All Pages

Clears the index and reindexes all current pages from the database.

```http
POST /api/index/reindex
```

**Response:** `200 OK`
```json
{
  "indexed": 15,
  "durationMs": 134,
  "status": "completed"
}
```

---

#### Clear Index

Removes all documents from the index.

```http
DELETE /api/index
```

**Response:** `200 OK`
```json
{
  "status": "cleared",
  "indexedDocuments": 0
}
```

---

#### Process Queue

Manually triggers processing of the async indexing queue.

```http
POST /api/index/process-queue
```

**Response:** `200 OK`
```json
{
  "processed": 5,
  "remainingInQueue": 0
}
```

---

## Data Models

### Database Schema

```sql
-- Spaces
CREATE TABLE spaces (
    space_id BIGSERIAL PRIMARY KEY,
    space_key VARCHAR(255) UNIQUE NOT NULL,
    space_name VARCHAR(255),
    description TEXT,
    space_type VARCHAR(50) DEFAULT 'global',
    home_page_id BIGINT,
    creator VARCHAR(255),
    creation_date TIMESTAMP
);

-- Content (Pages)
CREATE TABLE content (
    content_id BIGSERIAL PRIMARY KEY,
    content_type VARCHAR(50) NOT NULL DEFAULT 'PAGE',
    title VARCHAR(255) NOT NULL,
    lower_title VARCHAR(255),
    version INT DEFAULT 1,
    creator VARCHAR(255),
    creation_date TIMESTAMP,
    last_modifier VARCHAR(255),
    last_mod_date TIMESTAMP,
    version_comment TEXT,
    content_status VARCHAR(50) DEFAULT 'current',
    space_id BIGINT REFERENCES spaces(space_id),
    parent_id BIGINT REFERENCES content(content_id),
    prev_ver BIGINT
);

-- Body Content (XHTML)
CREATE TABLE bodycontent (
    body_content_id BIGSERIAL PRIMARY KEY,
    content_id BIGINT NOT NULL REFERENCES content(content_id),
    body TEXT,
    body_type INT DEFAULT 2  -- 2 = XHTML storage format
);

-- Labels
CREATE TABLE labels (
    label_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    owner VARCHAR(255)
);

-- Content-Label mapping
CREATE TABLE content_labels (
    content_id BIGINT REFERENCES content(content_id),
    label_id BIGINT REFERENCES labels(label_id),
    PRIMARY KEY (content_id, label_id)
);
```

### Lucene Index Fields

| Field | Type | Stored | Purpose |
|-------|------|--------|---------|
| contentId | StringField | Yes | Unique identifier |
| contentType | StringField | Yes | PAGE, BLOGPOST |
| spaceKey | StringField | Yes | Space filter |
| spaceName | TextField | Yes | Full-text search |
| title | TextField | Yes | Full-text search (boost 3x) |
| content | TextField | No | Full-text search (extracted from XHTML) |
| labelText | TextField | Yes | Full-text search (boost 2x) |
| all | TextField | No | Combined field for simple searches |
| contentStatus | StringField | No | Filter (always "current") |
| creator | StringField | No | Filter by creator |
| lastModifier | StringField | No | Filter by modifier |
| created | LongPoint | Yes* | Date range queries |
| modified | LongPoint + DocValues | Yes* | Date range queries + sorting |
| parentId | StringField | No | Hierarchy filter |
| ancestorIds | StringField (multi) | No | Subtree filter |
| spaceKey_facet | StringField | No | Faceting |
| contentType_facet | StringField | No | Faceting |
| label_facet | StringField (multi) | No | Faceting |

*Stored in separate `_stored` field
