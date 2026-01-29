# Confluence-like Wiki Search Example

A demonstration of how Confluence stores wiki pages and implements search functionality using:
- **PostgreSQL** - Database storage (mirroring Confluence's CONTENT/BODYCONTENT tables)
- **Apache Lucene** - Full-text search indexing
- **XHTML Storage Format** - Confluence-style content markup

## Architecture

This project demonstrates the key architectural patterns used by Confluence:

```
┌─────────────────────────────────────────────────────────────────────┐
│                        REST API Layer                                │
│              (SpaceController, PageController, SearchController)     │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        Service Layer                                 │
│                  (PageService, SpaceService)                         │
└─────────────────────────────────────────────────────────────────────┘
                    │                               │
                    ▼                               ▼
┌───────────────────────────────┐   ┌─────────────────────────────────┐
│      PostgreSQL Database      │   │      Lucene Search Index        │
│  ┌─────────┐   ┌───────────┐ │   │  ┌────────────┐  ┌───────────┐ │
│  │ SPACES  │   │  CONTENT  │ │   │  │ WikiIndexer│  │SearchSvc  │ │
│  └─────────┘   └───────────┘ │   │  └────────────┘  └───────────┘ │
│                ┌───────────┐ │   │         │              │        │
│                │BODYCONTENT│ │   │         ▼              │        │
│                └───────────┘ │   │  ┌────────────────────┐│        │
└───────────────────────────────┘   │  │  ./data/index     ││        │
                                    │  └────────────────────┘│        │
                                    └─────────────────────────────────┘
```

## Key Concepts Demonstrated

### 1. Two-Table Content Model
- **CONTENT table** - Page metadata (title, creator, dates, version)
- **BODYCONTENT table** - Actual XHTML content (stored separately for performance)

### 2. XHTML Storage Format
Pages are stored as XML with Confluence-like elements:
```xml
<h1>Page Title</h1>
<p>Regular paragraph with <strong>bold</strong> text.</p>

<ac:structured-macro ac:name="code">
  <ac:parameter ac:name="language">java</ac:parameter>
  <ac:plain-text-body><![CDATA[System.out.println("Hello");]]></ac:plain-text-body>
</ac:structured-macro>

<ac:structured-macro ac:name="info">
  <ac:rich-text-body>
    <p>This is an info panel.</p>
  </ac:rich-text-body>
</ac:structured-macro>
```

### 3. Lucene Indexing
- Multi-field indexing with boosts (title: 3x, labels: 2x, content: 1x)
- Async batch indexing (queue processed every 5 seconds)
- Text extraction from XHTML for searchable content

### 4. Search Features
- Full-text search across multiple fields
- Filtering by space, content type, labels, dates
- Faceted search results
- Highlighting of matched terms

## Running the Application

### Prerequisites
- Java 17+
- Maven 3.9+
- PostgreSQL 15+ (or use Docker)
- Docker (optional)

### Option 1: Docker Compose (Recommended)

```bash
# Start PostgreSQL and the application
docker-compose up -d

# View logs
docker-compose logs -f wiki-search
```

### Option 2: Local Development

1. Start PostgreSQL:
```bash
# Create database
createdb wikidb

# Or using Docker for just PostgreSQL
docker run -d --name wiki-postgres \
  -e POSTGRES_DB=wikidb \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:16-alpine
```

2. Run the application:
```bash
mvn spring-boot:run
```

## API Endpoints

### Spaces

```bash
# Create space
curl -X POST http://localhost:8080/api/spaces \
  -H "Content-Type: application/json" \
  -d '{"spaceKey": "DOCS", "spaceName": "Documentation"}'

# List spaces
curl http://localhost:8080/api/spaces

# Get space
curl http://localhost:8080/api/spaces/ENGINEERING
```

### Pages

```bash
# Create page
curl -X POST http://localhost:8080/api/pages \
  -H "Content-Type: application/json" \
  -d '{
    "spaceKey": "ENGINEERING",
    "title": "My New Page",
    "body": "This is the content of my page.",
    "labels": ["documentation", "guide"]
  }'

# Get page
curl http://localhost:8080/api/pages/1

# Get page storage format (raw XHTML)
curl http://localhost:8080/api/pages/1/storage-format

# Update page
curl -X PUT http://localhost:8080/api/pages/1 \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Updated Title",
    "body": "Updated content",
    "versionComment": "Fixed typo"
  }'

# List pages in space
curl http://localhost:8080/api/pages/space/ENGINEERING
```

### Search

```bash
# Simple search
curl "http://localhost:8080/api/search?q=kubernetes+deployment"

# Search in specific space
curl "http://localhost:8080/api/search?q=guide&spaceKey=ENGINEERING"

# Search with label filter
curl "http://localhost:8080/api/search?q=policy&labels=hr,benefits"

# Advanced search (POST)
curl -X POST http://localhost:8080/api/search \
  -H "Content-Type: application/json" \
  -d '{
    "query": "deployment kubernetes",
    "spaceKey": "DEVOPS",
    "labels": ["infrastructure"],
    "sort": "modified",
    "order": "desc",
    "page": 0,
    "size": 10
  }'
```

### Index Management

```bash
# Get index status
curl http://localhost:8080/api/index/status

# Reindex all pages
curl -X POST http://localhost:8080/api/index/reindex

# Clear index
curl -X DELETE http://localhost:8080/api/index
```

## Sample Data

The application loads sample data on startup from `sample-pages.json`:

| Space | Pages |
|-------|-------|
| ENGINEERING | Engineering Home, Getting Started Guide, Code Review Guidelines, Architecture Overview, Database Schema Guidelines, API Design Standards |
| DEVOPS | DevOps Home, Kubernetes Deployment Guide, CI/CD Pipeline Configuration, Monitoring and Alerting |
| HR | HR Home, Employee Handbook, Time Off Policy, Benefits Overview, Performance Review Process |

## Search Response Example

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
      "score": 5.234,
      "highlights": {
        "title": "<mark>Kubernetes</mark> <mark>Deployment</mark> Guide"
      },
      "modified": 1705312200
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

## Project Structure

```
confluence-search-example/
├── src/main/java/com/example/wiki/
│   ├── WikiApplication.java         # Main application
│   ├── DataLoader.java              # Sample data loader
│   │
│   ├── model/                       # JPA Entities
│   │   ├── Space.java               # SPACES table
│   │   ├── Page.java                # CONTENT table
│   │   ├── PageBody.java            # BODYCONTENT table
│   │   └── Label.java               # Labels
│   │
│   ├── repository/                  # Spring Data repositories
│   │
│   ├── storage/                     # XHTML storage format
│   │   ├── StorageFormatParser.java # Parse XHTML → plain text
│   │   └── StorageFormatBuilder.java# Build XHTML content
│   │
│   ├── search/                      # Lucene integration
│   │   ├── IndexFields.java         # Field definitions
│   │   ├── LuceneConfig.java        # Lucene setup
│   │   ├── WikiIndexer.java         # Document indexing
│   │   ├── WikiSearchService.java   # Search execution
│   │   └── AsyncIndexer.java        # Background indexing queue
│   │
│   ├── service/                     # Business logic
│   │   ├── SpaceService.java
│   │   ├── PageService.java
│   │   └── LabelService.java
│   │
│   ├── controller/                  # REST endpoints
│   │   ├── SpaceController.java
│   │   ├── PageController.java
│   │   ├── SearchController.java
│   │   └── IndexController.java
│   │
│   └── dto/                         # Request/Response objects
│
└── src/main/resources/
    ├── application.properties
    └── sample-pages.json            # Sample wiki content
```

## Key Differences from Real Confluence

This is a simplified demonstration. Real Confluence includes:

- **Version history** - Full page version tracking with diffs
- **Permissions** - Space and page-level access control
- **Attachments** - File storage and indexing
- **Plugins/Macros** - Extensive macro system
- **Cluster support** - Index synchronization across nodes
- **CQL** - Confluence Query Language for advanced search
- **Real-time collaboration** - Concurrent editing

## References

- [Confluence Storage Format](https://confluence.atlassian.com/doc/confluence-storage-format-790796544.html)
- [Confluence Data Model](https://confluence.atlassian.com/doc/confluence-data-model-127369837.html)
- [Confluence Search Fields](https://confluence.atlassian.com/doc/confluence-search-fields-161188.html)
- [Apache Lucene](https://lucene.apache.org/)
