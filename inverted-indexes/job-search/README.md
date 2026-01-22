# Job Search Service

A job listing search service powered by **Apache Lucene**, demonstrating real-world search functionality with batch and async indexing, faceted search, filtering, and highlighting.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        REST API Layer                           │
│                  (SearchController, IndexController)            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────┐    ┌─────────────────┐                     │
│  │  JobSearchService│    │   JobIndexer    │                     │
│  │  (Query/Facets)  │    │   (Documents)   │                     │
│  └────────┬────────┘    └────────┬────────┘                     │
│           │                      │                              │
│           │         ┌────────────┴────────────┐                 │
│           │         │                         │                 │
│           │    ┌────▼─────┐          ┌───────▼───────┐          │
│           │    │ BatchIndexer│        │  AsyncIndexer │          │
│           │    │ (Startup)  │        │  (Queue-based) │          │
│           │    └────────────┘        └───────────────┘          │
│           │                                                     │
├───────────┴─────────────────────────────────────────────────────┤
│                     Apache Lucene Core                          │
│        (IndexWriter, SearcherManager, Analyzers)                │
├─────────────────────────────────────────────────────────────────┤
│                     File System (Index Storage)                 │
│                         ./data/index                            │
└─────────────────────────────────────────────────────────────────┘
```

## Features

| Feature | Description |
|---------|-------------|
| **Full-text Search** | Search across title, description, company, skills |
| **Faceted Search** | Filter counts by location, job type, experience, skills |
| **Filtering** | Filter by location, salary range, job type, remote, etc. |
| **Highlighting** | Matched terms highlighted in results |
| **Sorting** | By relevance, date, salary |
| **Pagination** | Page through results |
| **Batch Indexing** | Bulk load jobs on startup |
| **Async Indexing** | Queue-based near real-time updates |

## Quick Start

### Using Docker (Recommended)

```bash
# Build and run
docker-compose up --build

# Service will be available at http://localhost:8080
```

### Using Maven

```bash
# Build
mvn clean package

# Run
java -jar target/job-search-1.0.0.jar

# Or with Maven
mvn spring-boot:run
```

## API Reference

### Search Jobs

**POST** `/api/jobs/search`

```json
{
  "query": "java developer",
  "locations": ["San Francisco", "Remote"],
  "jobTypes": ["FULL_TIME"],
  "experienceLevels": ["SENIOR", "MID"],
  "salaryMin": 100000,
  "salaryMax": 200000,
  "remote": true,
  "skills": ["Spring Boot", "Kubernetes"],
  "sortBy": "relevance",
  "page": 0,
  "size": 10
}
```

**GET** `/api/jobs/search`

```bash
curl "http://localhost:8080/api/jobs/search?q=java+developer&location=San+Francisco&remote=true&page=0&size=10"
```

### Response

```json
{
  "hits": [
    {
      "job": {
        "id": "job-001",
        "title": "Senior Java Developer",
        "company": "TechCorp Inc",
        "description": "...",
        "location": "San Francisco",
        "jobType": "FULL_TIME",
        "experienceLevel": "SENIOR",
        "salaryMin": 150000,
        "salaryMax": 200000,
        "skills": ["Java", "Spring Boot", "Kubernetes"],
        "postedDate": "2025-01-10",
        "remote": true
      },
      "score": 5.234,
      "highlights": {
        "title": "Senior <mark>Java</mark> <mark>Developer</mark>",
        "description": "...experienced <mark>Java</mark> <mark>developer</mark> to join..."
      }
    }
  ],
  "totalHits": 42,
  "page": 0,
  "size": 10,
  "totalPages": 5,
  "facets": {
    "location": [
      {"value": "San Francisco", "count": 15},
      {"value": "New York", "count": 12}
    ],
    "jobType": [
      {"value": "FULL_TIME", "count": 35},
      {"value": "CONTRACT", "count": 7}
    ],
    "experienceLevel": [
      {"value": "SENIOR", "count": 20},
      {"value": "MID", "count": 15}
    ],
    "skills": [
      {"value": "Java", "count": 25},
      {"value": "Python", "count": 18}
    ]
  },
  "searchTimeMs": 12
}
```

### Get Job by ID

**GET** `/api/jobs/{id}`

```bash
curl http://localhost:8080/api/jobs/job-001
```

### Index Management

**Index a job (sync)**
```bash
curl -X POST http://localhost:8080/api/index/job \
  -H "Content-Type: application/json" \
  -d '{
    "id": "job-new",
    "title": "New Position",
    "company": "New Company",
    "description": "Job description here",
    "location": "Remote",
    "jobType": "FULL_TIME",
    "experienceLevel": "MID",
    "salaryMin": 100000,
    "salaryMax": 150000,
    "skills": ["Java", "Spring"],
    "postedDate": "2025-01-15",
    "remote": true
  }'
```

**Index a job (async)**
```bash
curl -X POST http://localhost:8080/api/index/job/async \
  -H "Content-Type: application/json" \
  -d '{ ... }'
```

**Delete a job**
```bash
curl -X DELETE http://localhost:8080/api/index/job/job-001
```

**Reindex all**
```bash
curl -X POST http://localhost:8080/api/index/reindex
```

**Clear index**
```bash
curl -X DELETE http://localhost:8080/api/index
```

**Index status**
```bash
curl http://localhost:8080/api/index/status
```

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | 8080 | Server port |
| `lucene.index.path` | ./data/index | Lucene index directory |
| `jobsearch.data.path` | jobs.json | Sample data file |
| `jobsearch.index.on-startup` | true | Index sample data on startup |

## Project Structure

```
job-search/
├── src/main/java/com/example/jobsearch/
│   ├── model/
│   │   ├── Job.java              # Job entity
│   │   ├── SearchRequest.java    # Search query parameters
│   │   └── SearchResponse.java   # Search results with facets
│   ├── indexer/
│   │   ├── JobIndexer.java       # Core indexing logic
│   │   ├── BatchIndexer.java     # Bulk indexing
│   │   └── AsyncIndexer.java     # Queue-based indexing
│   ├── search/
│   │   └── JobSearchService.java # Search with facets/highlighting
│   ├── api/
│   │   ├── SearchController.java # Search API
│   │   └── IndexController.java  # Index management API
│   ├── config/
│   │   └── LuceneConfig.java     # Lucene beans
│   └── JobSearchApplication.java # Main application
├── src/main/resources/
│   ├── application.properties
│   └── jobs.json                 # Sample data
├── Dockerfile
├── docker-compose.yml
└── pom.xml
```

## Lucene Concepts Used

### Document Fields

| Field | Type | Purpose |
|-------|------|---------|
| `id` | StringField | Exact match, stored |
| `title` | TextField | Full-text search, stored |
| `description` | TextField | Full-text search, stored |
| `location` | TextField + StringField | Search + faceting |
| `salaryMin/Max` | IntPoint + StoredField | Range queries |
| `postedDate` | LongPoint + NumericDocValues | Range + sorting |
| `skills` | TextField (multi) | Multi-valued field |

### Key Components

- **StandardAnalyzer**: Tokenizes and lowercases text
- **IndexWriter**: Adds/updates/deletes documents
- **SearcherManager**: Thread-safe searcher management with NRT refresh
- **BooleanQuery**: Combines text search with filters
- **Highlighter**: Highlights matching terms in results

## Batch vs Async Indexing

### Batch Indexing
- Used for initial data load
- Runs on application startup
- Processes all jobs, then commits once

### Async Indexing
- Uses in-memory `BlockingQueue`
- Background thread processes events
- Commits every 100 documents or every second
- Near real-time search (< 1 second latency)

```
┌──────────┐     ┌─────────────┐     ┌──────────────┐
│ REST API │────▶│ AsyncIndexer│────▶│ BlockingQueue│
└──────────┘     └─────────────┘     └──────┬───────┘
                                            │
                                            ▼
                                    ┌───────────────┐
                                    │ Worker Thread │
                                    │ (batch commit)│
                                    └───────┬───────┘
                                            │
                                            ▼
                                    ┌───────────────┐
                                    │ Lucene Index  │
                                    └───────────────┘
```

## Docker

### Build Image

```bash
docker build -t job-search:latest .
```

### Run Container

```bash
docker run -p 8080:8080 -v job-data:/app/data job-search:latest
```

### Docker Compose

```bash
# Development (just the service)
docker-compose up

# Production (with Nginx)
docker-compose --profile production up
```

## Extending This Example

### Add More Analyzers
```java
// For specific languages
Analyzer analyzer = new EnglishAnalyzer();

// For synonyms
SynonymMap synonyms = ...;
Analyzer analyzer = new SynonymAnalyzer(synonyms);
```

### Add Spell Checking
```java
SpellChecker spellChecker = new SpellChecker(spellIndexDirectory);
String[] suggestions = spellChecker.suggestSimilar("jva", 5);
```

### Add Autocomplete
```java
AnalyzingInfixSuggester suggester = new AnalyzingInfixSuggester(dir, analyzer);
List<Lookup.LookupResult> results = suggester.lookup("java dev", 10, true, true);
```

### Use Lucene Facets Module
```java
FacetsCollector fc = new FacetsCollector();
FacetsCollector.search(searcher, query, 10, fc);
Facets facets = new FastTaxonomyFacetCounts(taxoReader, config, fc);
```
