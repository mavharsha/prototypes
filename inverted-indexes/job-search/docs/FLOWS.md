# Job Search Service - Detailed Flow Documentation

This document explains how the Job Search Service works, designed for developers familiar with Spring Boot but new to Apache Lucene.

## Table of Contents

1. [Lucene Fundamentals](#1-lucene-fundamentals)
2. [Application Startup Flow](#2-application-startup-flow)
3. [Batch Indexing Flow](#3-batch-indexing-flow)
4. [Async Indexing Flow](#4-async-indexing-flow)
5. [Search Flow](#5-search-flow)
6. [Document-to-Index Mapping](#6-document-to-index-mapping)
7. [Query Building Flow](#7-query-building-flow)
8. [Faceting Flow](#8-faceting-flow)
9. [Highlighting Flow](#9-highlighting-flow)

---

## 1. Lucene Fundamentals

Before diving into flows, let's understand key Lucene concepts.

### What is Lucene?

Lucene is a **Java library** (not a server) that provides:
- Full-text indexing
- Search capabilities
- Relevance scoring

Think of it as an embedded search engine you include in your application, similar to how you might use H2 as an embedded database.

### Key Concepts

| Concept | Spring/JPA Equivalent | Description |
|---------|----------------------|-------------|
| **Document** | Entity | A single searchable item (one job listing) |
| **Field** | Column/Property | A named piece of data within a document |
| **Index** | Database Table | Collection of documents stored on disk |
| **IndexWriter** | EntityManager (write) | Writes documents to the index |
| **IndexSearcher** | EntityManager (read) | Searches the index |
| **Analyzer** | - | Breaks text into searchable tokens |
| **Query** | Criteria/JPQL | Describes what to search for |

### Lucene vs Database

```
┌─────────────────────────────────────────────────────────────────┐
│                        Database (SQL)                           │
│  SELECT * FROM jobs WHERE description LIKE '%java developer%'  │
│                                                                 │
│  Problems:                                                      │
│  - Scans every row (slow)                                       │
│  - No relevance ranking                                         │
│  - "java developer" won't match "Java Developer" or "develops"  │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                        Lucene                                   │
│  Query: "java developer"                                        │
│                                                                 │
│  Benefits:                                                      │
│  - Uses inverted index (instant lookup)                         │
│  - Ranks by relevance (TF-IDF/BM25)                             │
│  - Handles stemming ("develops" → "develop")                    │
│  - Case-insensitive by default                                  │
└─────────────────────────────────────────────────────────────────┘
```

### The Inverted Index

This is the core data structure. Instead of storing "document → words", it stores "word → documents":

```
Original Documents:
  Doc 1: "Senior Java Developer"
  Doc 2: "Java Backend Engineer"
  Doc 3: "Python Developer"

Inverted Index:
  "senior"    → [Doc 1]
  "java"      → [Doc 1, Doc 2]
  "developer" → [Doc 1, Doc 3]
  "backend"   → [Doc 2]
  "engineer"  → [Doc 2]
  "python"    → [Doc 3]

Search "java developer":
  "java"      → [Doc 1, Doc 2]
  "developer" → [Doc 1, Doc 3]
  Intersection/Union → Doc 1 scores highest (matches both)
```

---

## 2. Application Startup Flow

When the Spring Boot application starts, Lucene components are initialized.

### Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                    Application Startup                          │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  1. Spring Context Initialization                               │
│     - Scans @Configuration, @Service, @RestController           │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  2. LuceneConfig @Bean Methods Execute (in order)               │
│                                                                 │
│     a) indexDirectory()  → Creates ./data/index folder          │
│     b) analyzer()        → Creates StandardAnalyzer             │
│     c) directory()       → Opens MMapDirectory                  │
│     d) indexWriterConfig() → Configures write settings          │
│     e) indexWriter()     → Opens IndexWriter                    │
│     f) searcherManager() → Creates SearcherManager              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  3. Service Beans Created                                       │
│     - JobIndexer (depends on IndexWriter, SearcherManager)      │
│     - AsyncIndexer (depends on JobIndexer)                      │
│     - BatchIndexer (depends on JobIndexer)                      │
│     - JobSearchService (depends on SearcherManager, Analyzer)   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  4. @PostConstruct Methods                                      │
│     - AsyncIndexer.start() → Starts background worker thread    │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  5. ApplicationReadyEvent                                       │
│     - BatchIndexer.onApplicationReady()                         │
│     - Loads jobs.json and indexes all documents                 │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  6. Application Ready                                           │
│     - HTTP server accepting requests on port 8080               │
└─────────────────────────────────────────────────────────────────┘
```

### Code Walkthrough: LuceneConfig.java

```java
@Configuration
public class LuceneConfig {

    @Value("${lucene.index.path:./data/index}")
    private String indexPath;

    // STEP 1: Create directory path
    @Bean
    public Path indexDirectory() throws IOException {
        Path path = Paths.get(indexPath);
        if (!Files.exists(path)) {
            Files.createDirectories(path);  // mkdir -p ./data/index
        }
        return path;
    }

    // STEP 2: Create analyzer
    // The analyzer determines how text is broken into tokens
    @Bean
    public Analyzer analyzer() {
        // StandardAnalyzer:
        // - Lowercases text: "Java" → "java"
        // - Removes punctuation: "Hello!" → "hello"
        // - Splits on whitespace: "java developer" → ["java", "developer"]
        return new StandardAnalyzer();
    }

    // STEP 3: Open the index directory
    @Bean
    public Directory directory(Path indexDirectory) throws IOException {
        // MMapDirectory uses memory-mapped files for fast I/O
        // This is the recommended Directory for 64-bit systems
        // Think of it as: new File("./data/index") but optimized for Lucene
        this.directory = MMapDirectory.open(indexDirectory);
        return this.directory;
    }

    // STEP 4: Configure the IndexWriter
    @Bean
    public IndexWriterConfig indexWriterConfig(Analyzer analyzer) {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);

        // CREATE_OR_APPEND:
        // - If index exists: open it and append
        // - If index doesn't exist: create new one
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

        return config;
    }

    // STEP 5: Create the IndexWriter
    @Bean
    public IndexWriter indexWriter(Directory directory, IndexWriterConfig config)
            throws IOException {
        // IndexWriter is the main class for adding/updating/deleting documents
        // Only ONE IndexWriter can be open per index at a time
        // Think of it like a database connection with exclusive write lock
        this.indexWriter = new IndexWriter(directory, config);
        return this.indexWriter;
    }

    // STEP 6: Create SearcherManager
    @Bean
    public SearcherManager searcherManager(IndexWriter indexWriter) throws IOException {
        // SearcherManager provides thread-safe access to IndexSearcher
        // It handles:
        // - Refreshing to see newly indexed documents
        // - Reference counting (acquire/release pattern)
        // - Sharing searchers across threads
        this.searcherManager = new SearcherManager(indexWriter, null);
        return this.searcherManager;
    }
}
```

### Why SearcherManager?

In Lucene, `IndexSearcher` is immutable - it sees a snapshot of the index at creation time. To see new documents, you need a new searcher. `SearcherManager` handles this:

```java
// WITHOUT SearcherManager (manual, error-prone):
IndexSearcher searcher = new IndexSearcher(DirectoryReader.open(directory));
// ... use searcher ...
// Problem: How do you refresh? When do you close?

// WITH SearcherManager (recommended):
IndexSearcher searcher = searcherManager.acquire();
try {
    // ... use searcher ...
} finally {
    searcherManager.release(searcher);  // Returns to pool, doesn't close
}

// To see new documents:
searcherManager.maybeRefresh();  // Only refreshes if there are changes
```

---

## 3. Batch Indexing Flow

Batch indexing loads all documents at once, typically at startup or during a scheduled reindex.

### Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│            ApplicationReadyEvent Fired                          │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  BatchIndexer.onApplicationReady()                              │
│  - Checks if jobsearch.index.on-startup=true                    │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  BatchIndexer.indexFromClasspath()                              │
│  - Loads jobs.json from classpath                               │
│  - Parses JSON into List<Job>                                   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  For each Job in list:                                          │
│                                                                 │
│    JobIndexer.indexJob(job)                                     │
│    │                                                            │
│    ├─► createDocument(job)                                      │
│    │   - Creates Lucene Document                                │
│    │   - Adds Fields (title, description, salary, etc.)         │
│    │                                                            │
│    └─► indexWriter.updateDocument(term, doc)                    │
│        - If doc with same ID exists: replace                    │
│        - If doc doesn't exist: add                              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  JobIndexer.commit()                                            │
│  │                                                              │
│  ├─► indexWriter.commit()                                       │
│  │   - Flushes in-memory buffer to disk                         │
│  │   - Makes documents durable (survives crash)                 │
│  │                                                              │
│  └─► searcherManager.maybeRefresh()                             │
│      - Opens new IndexSearcher to see committed docs            │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Indexing Complete                                              │
│  - 20 jobs indexed                                              │
│  - Index files created in ./data/index                          │
│  - SearcherManager can now find all documents                   │
└─────────────────────────────────────────────────────────────────┘
```

### Code Walkthrough: Document Creation

The most important part is converting a Job to a Lucene Document:

```java
private Document createDocument(Job job) {
    Document doc = new Document();

    // ┌─────────────────────────────────────────────────────────────┐
    // │ FIELD TYPE: StringField                                     │
    // │ - Stored as-is (no analysis/tokenization)                   │
    // │ - Good for: IDs, enums, exact-match fields                  │
    // │ - Searchable only by exact match                            │
    // └─────────────────────────────────────────────────────────────┘
    doc.add(new StringField("id", job.getId(), Field.Store.YES));
    // Field.Store.YES = Store original value, retrieve in search results
    // Field.Store.NO  = Don't store, only use for searching

    // ┌─────────────────────────────────────────────────────────────┐
    // │ FIELD TYPE: TextField                                       │
    // │ - Analyzed (tokenized, lowercased, etc.)                    │
    // │ - Good for: titles, descriptions, any searchable text       │
    // │ - "Senior Java Developer" → ["senior", "java", "developer"] │
    // └─────────────────────────────────────────────────────────────┘
    doc.add(new TextField("title", job.getTitle(), Field.Store.YES));
    doc.add(new TextField("description", job.getDescription(), Field.Store.YES));

    // ┌─────────────────────────────────────────────────────────────┐
    // │ Dual-purpose field: search + filter                         │
    // │ - TextField for full-text search ("san francisco")          │
    // │ - StringField for exact facet matching                      │
    // └─────────────────────────────────────────────────────────────┘
    doc.add(new TextField("location", job.getLocation(), Field.Store.YES));
    doc.add(new StringField("location_facet",
            job.getLocation().toLowerCase(), Field.Store.NO));

    // ┌─────────────────────────────────────────────────────────────┐
    // │ FIELD TYPE: IntPoint                                        │
    // │ - For numeric range queries                                 │
    // │ - NOT stored by default, need separate StoredField          │
    // │ - Enables: salaryMin:[100000 TO 200000]                     │
    // └─────────────────────────────────────────────────────────────┘
    doc.add(new IntPoint("salaryMin", job.getSalaryMin()));
    doc.add(new StoredField("salaryMin", job.getSalaryMin())); // To retrieve value

    doc.add(new IntPoint("salaryMax", job.getSalaryMax()));
    doc.add(new StoredField("salaryMax", job.getSalaryMax()));

    // ┌─────────────────────────────────────────────────────────────┐
    // │ FIELD TYPE: NumericDocValuesField                           │
    // │ - For sorting and aggregations                              │
    // │ - Stored in columnar format (fast for sorting)              │
    // │ - One value per document per field                          │
    // └─────────────────────────────────────────────────────────────┘
    doc.add(new NumericDocValuesField("salaryMin_sort", job.getSalaryMin()));
    doc.add(new NumericDocValuesField("postedDate_sort",
            job.getPostedDate().toEpochDay()));

    // ┌─────────────────────────────────────────────────────────────┐
    // │ Multi-valued field                                          │
    // │ - Same field name, multiple values                          │
    // │ - skills: ["Java", "Spring Boot", "Kubernetes"]             │
    // │ - Search "Java" will match this document                    │
    // └─────────────────────────────────────────────────────────────┘
    if (job.getSkills() != null) {
        for (String skill : job.getSkills()) {
            doc.add(new TextField("skills", skill, Field.Store.YES));
            doc.add(new StringField("skills_facet",
                    skill.toLowerCase(), Field.Store.NO));
        }
    }

    return doc;
}
```

### Field Types Summary

| Field Type | Analyzed | Stored | Use Case |
|------------|----------|--------|----------|
| `StringField` | No | Optional | IDs, enums, exact filters |
| `TextField` | Yes | Optional | Searchable text content |
| `IntPoint` | N/A | No | Numeric range queries |
| `LongPoint` | N/A | No | Long range queries (dates) |
| `StoredField` | N/A | Yes | Store value for retrieval only |
| `NumericDocValuesField` | N/A | Yes | Sorting, aggregations |

---

## 4. Async Indexing Flow

Async indexing handles real-time updates using a background worker thread.

### Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                     REST Request                                │
│  POST /api/index/job/async                                      │
│  Body: { "id": "new-job", "title": "New Position", ... }        │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  IndexController.indexJobAsync()                                │
│  - Receives Job from request body                               │
│  - Calls asyncIndexer.submitForIndexing(job)                    │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  AsyncIndexer.submitForIndexing(job)                            │
│  │                                                              │
│  └─► eventQueue.offer(IndexEvent.index(job))                    │
│      - Non-blocking add to BlockingQueue                        │
│      - Returns immediately (true=queued, false=queue full)      │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  HTTP Response (Immediate)                                      │
│  Status: 202 Accepted                                           │
│  Body: { "status": "queued", "id": "new-job" }                  │
└─────────────────────────────────────────────────────────────────┘


═══════════════════════════════════════════════════════════════════
                    BACKGROUND WORKER THREAD
═══════════════════════════════════════════════════════════════════

┌─────────────────────────────────────────────────────────────────┐
│  Worker Thread (started at @PostConstruct)                      │
│  while (running) {                                              │
│      event = eventQueue.poll(100ms)  // Waits for events        │
│      if (event != null) processEvent(event)                     │
│      if (shouldCommit) commit()                                 │
│  }                                                              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  processEvent(IndexEvent)                                       │
│  │                                                              │
│  ├─► If INDEX: jobIndexer.indexJob(event.job)                   │
│  │                                                              │
│  └─► If DELETE: jobIndexer.deleteJob(event.jobId)               │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Commit Decision                                                │
│  │                                                              │
│  │  Commit if:                                                  │
│  │  - batchSize >= 100 (accumulated 100 documents)              │
│  │  - OR time since last commit > 1 second                      │
│  │                                                              │
│  └─► jobIndexer.commit()                                        │
│      - indexWriter.commit()                                     │
│      - searcherManager.maybeRefresh()                           │
│      - Document now visible in search!                          │
└─────────────────────────────────────────────────────────────────┘
```

### Code Walkthrough: AsyncIndexer.java

```java
@Service
public class AsyncIndexer {

    // Thread-safe queue with max capacity
    // If queue is full, new events are rejected (backpressure)
    private final BlockingQueue<IndexEvent> eventQueue =
            new LinkedBlockingQueue<>(10000);

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread workerThread;

    @PostConstruct
    public void start() {
        running.set(true);

        // Create daemon thread (won't prevent JVM shutdown)
        workerThread = new Thread(this::processEvents, "async-indexer");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    // Called from REST controller
    public boolean submitForIndexing(Job job) {
        // offer() is non-blocking:
        // - Returns true if added
        // - Returns false if queue is full (backpressure)
        return eventQueue.offer(IndexEvent.index(job));
    }

    private void processEvents() {
        int batchSize = 0;
        long lastCommit = System.currentTimeMillis();

        while (running.get()) {
            try {
                // poll() with timeout:
                // - Waits up to 100ms for an event
                // - Returns null if no event available
                // - This prevents busy-waiting
                IndexEvent event = eventQueue.poll(100, TimeUnit.MILLISECONDS);

                if (event != null) {
                    processEvent(event);
                    batchSize++;
                }

                // Commit strategy: balance latency vs throughput
                // - Commit every 100 docs (throughput)
                // - Or every 1 second (latency guarantee)
                boolean shouldCommit =
                    batchSize >= 100 ||
                    (batchSize > 0 &&
                     System.currentTimeMillis() - lastCommit > 1000);

                if (shouldCommit) {
                    jobIndexer.commit();
                    batchSize = 0;
                    lastCommit = System.currentTimeMillis();
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
```

### Near Real-Time (NRT) Search

The key concept here is **Near Real-Time** search:

```
Timeline:
────────────────────────────────────────────────────────────────►
│                                                              │
│  t=0      t=50ms    t=100ms   t=500ms   t=1000ms            │
│   │         │          │         │          │                │
│   ▼         ▼          ▼         ▼          ▼                │
│ Event    Event      Event     Event     COMMIT               │
│ queued   queued     queued    queued    + Refresh            │
│                                              │                │
│                                              ▼                │
│                                    Documents now              │
│                                    searchable!                │
│                                                              │
│  ◄──────── ~1 second latency ─────────────►                  │
```

This is different from:
- **Real-time**: Every document immediately searchable (expensive)
- **Batch**: Documents only searchable after full reindex (high latency)

---

## 5. Search Flow

The search flow handles queries, filtering, faceting, and highlighting.

### Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                     REST Request                                │
│  POST /api/jobs/search                                          │
│  {                                                              │
│    "query": "java developer",                                   │
│    "locations": ["San Francisco"],                              │
│    "experienceLevels": ["SENIOR"],                              │
│    "salaryMin": 100000,                                         │
│    "sortBy": "relevance",                                       │
│    "page": 0,                                                   │
│    "size": 10                                                   │
│  }                                                              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  SearchController.search(SearchRequest)                         │
│  - Delegates to JobSearchService.search()                       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Acquire IndexSearcher                                          │
│  │                                                              │
│  └─► IndexSearcher searcher = searcherManager.acquire()         │
│      - Gets thread-safe searcher from pool                      │
│      - Reference counted (must release later)                   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Build Query (see Section 7 for details)                        │
│  │                                                              │
│  └─► BooleanQuery combining:                                    │
│      - Text query (MUST): "java developer"                      │
│      - Location filter (FILTER): "san francisco"                │
│      - Experience filter (FILTER): "SENIOR"                     │
│      - Salary filter (FILTER): salaryMax >= 100000              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Execute Search                                                 │
│  │                                                              │
│  └─► TopDocs topDocs = searcher.search(query, numHits, sort)    │
│      │                                                          │
│      │  Lucene internally:                                      │
│      │  1. Finds matching documents via inverted index          │
│      │  2. Applies filters (location, experience, salary)       │
│      │  3. Scores documents (TF-IDF / BM25)                     │
│      │  4. Sorts by score (or specified field)                  │
│      │  5. Returns top N document IDs + scores                  │
│      │                                                          │
│      └─► Returns: TopDocs {                                     │
│            totalHits: 42,                                       │
│            scoreDocs: [{doc: 5, score: 4.2}, {doc: 12, ...}]    │
│          }                                                      │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Extract Hits (with pagination)                                 │
│  │                                                              │
│  │  For page=0, size=10:                                        │
│  │  - start = 0 * 10 = 0                                        │
│  │  - Process scoreDocs[0] through scoreDocs[9]                 │
│  │                                                              │
│  │  For each scoreDoc:                                          │
│  │  │                                                           │
│  │  ├─► Document doc = searcher.doc(scoreDoc.doc)               │
│  │  │   - Retrieves stored fields from index                    │
│  │  │                                                           │
│  │  ├─► Job job = documentToJob(doc)                            │
│  │  │   - Converts Document back to Job object                  │
│  │  │                                                           │
│  │  └─► highlights = highlighter.getBestFragment(...)           │
│  │      - Highlights matching terms (see Section 9)             │
│  │                                                              │
│  └─► Return List<JobHit>                                        │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Calculate Facets (see Section 8)                               │
│  │                                                              │
│  └─► Count documents per:                                       │
│      - Location: {"San Francisco": 15, "New York": 12, ...}     │
│      - Job Type: {"FULL_TIME": 35, "CONTRACT": 7, ...}          │
│      - Experience: {"SENIOR": 20, "MID": 15, ...}               │
│      - Skills: {"Java": 25, "Python": 18, ...}                  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Release Searcher                                               │
│  │                                                              │
│  └─► searcherManager.release(searcher)                          │
│      - Returns searcher to pool                                 │
│      - MUST be in finally block to prevent leaks                │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Build Response                                                 │
│  │                                                              │
│  └─► SearchResponse {                                           │
│        hits: [...],                                             │
│        totalHits: 42,                                           │
│        page: 0,                                                 │
│        totalPages: 5,                                           │
│        facets: {...},                                           │
│        searchTimeMs: 12                                         │
│      }                                                          │
└─────────────────────────────────────────────────────────────────┘
```

### Acquire/Release Pattern

This is critical for thread safety:

```java
public SearchResponse search(SearchRequest request) throws IOException {
    IndexSearcher searcher = searcherManager.acquire();
    try {
        // All search operations here...
        Query query = buildQuery(request);
        TopDocs topDocs = searcher.search(query, numHits);
        // ...
        return response;
    } finally {
        // ALWAYS release, even if exception occurs
        searcherManager.release(searcher);
    }
}
```

Think of it like a database connection pool - acquire when needed, release when done.

---

## 6. Document-to-Index Mapping

Understanding how Job fields map to Lucene index structure.

### Visual Mapping

```
┌─────────────────────────────────────────────────────────────────┐
│                          Job Object                             │
├─────────────────────────────────────────────────────────────────┤
│  id: "job-001"                                                  │
│  title: "Senior Java Developer"                                 │
│  company: "TechCorp Inc"                                        │
│  description: "We are looking for an experienced..."            │
│  location: "San Francisco"                                      │
│  jobType: FULL_TIME                                             │
│  experienceLevel: SENIOR                                        │
│  salaryMin: 150000                                              │
│  salaryMax: 200000                                              │
│  skills: ["Java", "Spring Boot", "Kubernetes"]                  │
│  postedDate: 2025-01-10                                         │
│  remote: true                                                   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │  createDocument(job)
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Lucene Document                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  StringField("id", "job-001", STORED)                           │
│       └─► Inverted Index: "job-001" → [doc_0]                   │
│                                                                 │
│  TextField("title", "Senior Java Developer", STORED)            │
│       └─► Inverted Index:                                       │
│             "senior" → [doc_0]                                  │
│             "java" → [doc_0]                                    │
│             "developer" → [doc_0]                               │
│                                                                 │
│  TextField("company", "TechCorp Inc", STORED)                   │
│  StringField("company_exact", "techcorp inc", NOT_STORED)       │
│                                                                 │
│  TextField("description", "We are looking...", STORED)          │
│       └─► Inverted Index: many terms...                         │
│                                                                 │
│  TextField("location", "San Francisco", STORED)                 │
│  StringField("location_facet", "san francisco", NOT_STORED)     │
│       └─► Inverted Index: "san francisco" → [doc_0]             │
│                                                                 │
│  StringField("jobType", "FULL_TIME", STORED)                    │
│       └─► Inverted Index: "FULL_TIME" → [doc_0]                 │
│                                                                 │
│  StringField("experienceLevel", "SENIOR", STORED)               │
│       └─► Inverted Index: "SENIOR" → [doc_0]                    │
│                                                                 │
│  IntPoint("salaryMin", 150000)      ─┐                          │
│  StoredField("salaryMin", 150000)    │ Numeric index            │
│  IntPoint("salaryMax", 200000)       │ for range queries        │
│  StoredField("salaryMax", 200000)   ─┘                          │
│                                                                 │
│  NumericDocValuesField("salaryMin_sort", 150000)  ← For sorting │
│  NumericDocValuesField("postedDate_sort", 19368)  ← Epoch days  │
│                                                                 │
│  TextField("skills", "Java", STORED)        ─┐                  │
│  StringField("skills_facet", "java")         │                  │
│  TextField("skills", "Spring Boot", STORED)  │ Multi-valued     │
│  StringField("skills_facet", "spring boot")  │                  │
│  TextField("skills", "Kubernetes", STORED)   │                  │
│  StringField("skills_facet", "kubernetes")  ─┘                  │
│                                                                 │
│  LongPoint("postedDate", 19368)                                 │
│  StoredField("postedDate", "2025-01-10")                        │
│                                                                 │
│  StringField("remote", "true", STORED)                          │
│                                                                 │
│  TextField("all", "Senior Java Developer TechCorp...", NO)      │
│       └─► Combined field for simple queries                     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Why Multiple Fields for the Same Data?

Take `location` as an example:

```java
// For full-text search: "Find jobs in San Francisco area"
// Analyzer splits "San Francisco" → ["san", "francisco"]
// User can search "san" or "francisco" or "San Francisco"
doc.add(new TextField("location", job.getLocation(), Field.Store.YES));

// For exact facet matching: "Filter by location = San Francisco"
// No analysis, exact match only
// Used for: facets, filters with exact values
doc.add(new StringField("location_facet",
        job.getLocation().toLowerCase(), Field.Store.NO));
```

Similarly for numeric fields:

```java
// IntPoint: For range queries (WHERE salary BETWEEN x AND y)
doc.add(new IntPoint("salaryMin", job.getSalaryMin()));

// StoredField: To retrieve the value in search results
doc.add(new StoredField("salaryMin", job.getSalaryMin()));

// NumericDocValuesField: For sorting (ORDER BY salary)
doc.add(new NumericDocValuesField("salaryMin_sort", job.getSalaryMin()));
```

---

## 7. Query Building Flow

How the search request is converted to a Lucene query.

### Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│  SearchRequest                                                  │
│  {                                                              │
│    "query": "java developer",                                   │
│    "locations": ["San Francisco", "Remote"],                    │
│    "experienceLevels": ["SENIOR"],                              │
│    "salaryMin": 100000,                                         │
│    "remote": true                                               │
│  }                                                              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  BooleanQuery.Builder                                           │
│  (Combines multiple query clauses)                              │
└─────────────────────────────────────────────────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          │                   │                   │
          ▼                   ▼                   ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│  Text Query     │ │  Filter Clauses │ │  Range Queries  │
│  (MUST)         │ │  (FILTER)       │ │  (FILTER)       │
└─────────────────┘ └─────────────────┘ └─────────────────┘
          │                   │                   │
          ▼                   ▼                   ▼
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│  BooleanQuery {                                                 │
│                                                                 │
│    MUST: MultiFieldQuery("java developer")                      │
│          ├─ title:java^3.0                                      │
│          ├─ title:developer^3.0                                 │
│          ├─ skills:java^2.0                                     │
│          ├─ skills:developer^2.0                                │
│          ├─ company:java^1.5                                    │
│          ├─ description:java^1.0                                │
│          └─ description:developer^1.0                           │
│                                                                 │
│    FILTER: BooleanQuery(OR) {                                   │
│              location_facet:"san francisco"                     │
│              location_facet:"remote"                            │
│            }                                                    │
│                                                                 │
│    FILTER: experienceLevel:"SENIOR"                             │
│                                                                 │
│    FILTER: IntPoint.newRangeQuery("salaryMax", 100000, MAX)     │
│                                                                 │
│    FILTER: remote:"true"                                        │
│                                                                 │
│  }                                                              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### BooleanClause.Occur Types

| Type | SQL Equivalent | Affects Score? | Description |
|------|---------------|----------------|-------------|
| `MUST` | AND (required) | Yes | Document must match, contributes to score |
| `SHOULD` | OR (optional) | Yes | Document may match, boosts score if it does |
| `FILTER` | AND (required) | No | Document must match, but doesn't affect score |
| `MUST_NOT` | NOT | No | Document must not match |

### Why FILTER vs MUST?

```java
// MUST: "Find documents about java, rank by how relevant they are"
boolQuery.add(textQuery, BooleanClause.Occur.MUST);

// FILTER: "Only include remote jobs, but don't change ranking"
// Filters are also cached for better performance
boolQuery.add(remoteQuery, BooleanClause.Occur.FILTER);
```

### Code Walkthrough: buildQuery()

```java
private Query buildQuery(SearchRequest request) throws ParseException {
    BooleanQuery.Builder boolQuery = new BooleanQuery.Builder();

    // ══════════════════════════════════════════════════════════════
    // TEXT QUERY (affects ranking)
    // ══════════════════════════════════════════════════════════════
    if (request.getQuery() != null && !request.getQuery().isBlank()) {

        // Search across multiple fields with different weights (boosts)
        String[] fields = {"title", "skills", "company", "description", "location"};
        Map<String, Float> boosts = Map.of(
            "title", 3.0f,        // Title matches are 3x more important
            "skills", 2.0f,       // Skill matches are 2x
            "company", 1.5f,      // Company is 1.5x
            "description", 1.0f,  // Description is baseline
            "location", 1.0f
        );

        MultiFieldQueryParser parser = new MultiFieldQueryParser(fields, analyzer, boosts);
        parser.setDefaultOperator(MultiFieldQueryParser.Operator.OR);
        // OR means: match "java" OR "developer" (more results, less precise)
        // AND would mean: match "java" AND "developer" (fewer results, more precise)

        Query textQuery = parser.parse(request.getQuery());
        boolQuery.add(textQuery, BooleanClause.Occur.MUST);

    } else {
        // No text query = return all documents
        boolQuery.add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST);
    }

    // ══════════════════════════════════════════════════════════════
    // LOCATION FILTER (OR logic within, AND with other filters)
    // ══════════════════════════════════════════════════════════════
    if (request.getLocations() != null && !request.getLocations().isEmpty()) {
        BooleanQuery.Builder locationQuery = new BooleanQuery.Builder();

        // "San Francisco" OR "Remote"
        for (String location : request.getLocations()) {
            locationQuery.add(
                new TermQuery(new Term("location_facet", location.toLowerCase())),
                BooleanClause.Occur.SHOULD  // OR between locations
            );
        }

        boolQuery.add(locationQuery.build(), BooleanClause.Occur.FILTER);
    }

    // ══════════════════════════════════════════════════════════════
    // SALARY RANGE FILTER
    // ══════════════════════════════════════════════════════════════
    if (request.getSalaryMin() != null) {
        // "Show jobs where max salary >= my minimum requirement"
        // e.g., I want $100k+, show jobs that pay up to $100k or more
        boolQuery.add(
            IntPoint.newRangeQuery("salaryMax", request.getSalaryMin(), Integer.MAX_VALUE),
            BooleanClause.Occur.FILTER
        );
    }

    if (request.getSalaryMax() != null) {
        // "Show jobs where min salary <= my maximum budget"
        boolQuery.add(
            IntPoint.newRangeQuery("salaryMin", 0, request.getSalaryMax()),
            BooleanClause.Occur.FILTER
        );
    }

    return boolQuery.build();
}
```

---

## 8. Faceting Flow

Facets provide counts for filtering options (like "San Francisco (15)").

### What Are Facets?

```
┌─────────────────────────────────────────────────────────────────┐
│  Search Results UI                                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Filters:                        Results:                       │
│  ┌─────────────────────┐         ┌─────────────────────────┐    │
│  │ Location            │         │ Senior Java Developer   │    │
│  │ ☑ San Francisco (15)│ ◄────── │ TechCorp • SF • $150k  │    │
│  │ ☐ New York (12)     │  These  │                         │    │
│  │ ☐ Seattle (8)       │  are    ├─────────────────────────┤    │
│  │ ☐ Remote (20)       │  facets │ Java Backend Engineer   │    │
│  ├─────────────────────┤         │ FinTech • SF • $130k   │    │
│  │ Job Type            │         │                         │    │
│  │ ☑ Full-time (35)    │         └─────────────────────────┘    │
│  │ ☐ Contract (7)      │                                        │
│  └─────────────────────┘                                        │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│  After main search completes with query results                 │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  calculateFacets(searcher, query)                               │
│  │                                                              │
│  │  // Get all matching documents (up to limit)                 │
│  └─► TopDocs allDocs = searcher.search(query, 1000)             │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  For each matching document:                                    │
│  │                                                              │
│  │  Document doc = searcher.doc(scoreDoc.doc)                   │
│  │                                                              │
│  │  // Count each facet field                                   │
│  │  locationCounts.merge(doc.get("location"), 1, Integer::sum)  │
│  │  jobTypeCounts.merge(doc.get("jobType"), 1, Integer::sum)    │
│  │  expCounts.merge(doc.get("experienceLevel"), 1, Integer::sum)│
│  │                                                              │
│  │  // Skills is multi-valued                                   │
│  │  for (String skill : doc.getValues("skills")) {              │
│  │      skillsCounts.merge(skill, 1, Integer::sum)              │
│  │  }                                                           │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Sort facets by count (descending) and limit to top 10          │
│                                                                 │
│  Result:                                                        │
│  {                                                              │
│    "location": [                                                │
│      {"value": "San Francisco", "count": 15},                   │
│      {"value": "New York", "count": 12},                        │
│      {"value": "Remote", "count": 10}                           │
│    ],                                                           │
│    "jobType": [                                                 │
│      {"value": "FULL_TIME", "count": 35},                       │
│      {"value": "CONTRACT", "count": 7}                          │
│    ],                                                           │
│    ...                                                          │
│  }                                                              │
└─────────────────────────────────────────────────────────────────┘
```

### Production Note

Our implementation iterates over documents for simplicity. In production with millions of documents, use Lucene's `FacetsCollector`:

```java
// Production approach (more efficient)
FacetsCollector fc = new FacetsCollector();
FacetsCollector.search(searcher, query, 10, fc);

// Using taxonomy-based facets
TaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoDir);
Facets facets = new FastTaxonomyFacetCounts(taxoReader, config, fc);
FacetResult result = facets.getTopChildren(10, "location");
```

---

## 9. Highlighting Flow

Highlighting shows users WHY a document matched by marking matching terms.

### Example

```
Search: "java developer"

Result without highlighting:
  "We are looking for an experienced Java developer to join..."

Result with highlighting:
  "We are looking for an experienced <mark>Java</mark> <mark>developer</mark> to join..."
```

### Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│  Setup Highlighter (once per search)                            │
│  │                                                              │
│  ├─► Parse query: "java developer"                              │
│  │   MultiFieldQueryParser.parse("java developer")              │
│  │                                                              │
│  ├─► Create QueryScorer                                         │
│  │   - Identifies which terms to highlight                      │
│  │   - Weights terms by their query score                       │
│  │                                                              │
│  ├─► Create Highlighter with HTML formatter                     │
│  │   SimpleHTMLFormatter("<mark>", "</mark>")                   │
│  │                                                              │
│  └─► Set fragmenter                                             │
│      SimpleFragmenter(150)  // 150 char fragments               │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  For each search result:                                        │
│  │                                                              │
│  │  Job job = documentToJob(doc)                                │
│  │                                                              │
│  │  // Highlight title                                          │
│  │  String titleHL = highlighter.getBestFragment(               │
│  │      analyzer,                                               │
│  │      "title",           // field name                        │
│  │      job.getTitle()     // original text                     │
│  │  )                                                           │
│  │  // Returns: "Senior <mark>Java</mark> <mark>Developer</mark>"│
│  │  // Or null if no matches                                    │
│  │                                                              │
│  │  // Highlight description                                    │
│  │  String descHL = highlighter.getBestFragment(                │
│  │      analyzer,                                               │
│  │      "description",                                          │
│  │      job.getDescription()                                    │
│  │  )                                                           │
│  │  // Returns best matching 150-char fragment with highlights  │
│  │                                                              │
│  └─► highlights = {"title": titleHL, "description": descHL}     │
└─────────────────────────────────────────────────────────────────┘
```

### How Highlighting Works Internally

```
Original text: "We are looking for an experienced Java developer to join our team"

Step 1: Tokenize with same analyzer used for indexing
  → ["we", "are", "looking", "for", "an", "experienced",
     "java", "developer", "to", "join", "our", "team"]

Step 2: Match tokens against query terms
  Query: "java developer"
  → "java" matches at position 6
  → "developer" matches at position 7

Step 3: Get token offsets (character positions)
  → "java": start=42, end=46
  → "developer": start=47, end=56

Step 4: Insert highlight markers at offsets
  → "...experienced <mark>Java</mark> <mark>developer</mark> to join..."
```

### Code Walkthrough

```java
private List<JobHit> extractHits(IndexSearcher searcher, TopDocs topDocs,
                                  int start, String queryText) throws IOException {
    List<JobHit> hits = new ArrayList<>();

    // Setup highlighter if we have a query
    Highlighter highlighter = null;
    if (queryText != null && !queryText.isBlank()) {
        try {
            // Parse the query (same as search query)
            MultiFieldQueryParser parser = new MultiFieldQueryParser(
                new String[]{"title", "description"},
                analyzer
            );
            Query highlightQuery = parser.parse(queryText);

            // QueryScorer determines which terms to highlight
            QueryScorer scorer = new QueryScorer(highlightQuery);

            // Highlighter wraps matches with HTML tags
            highlighter = new Highlighter(
                new SimpleHTMLFormatter("<mark>", "</mark>"),
                scorer
            );

            // Fragmenter breaks long text into snippets
            // 150 = max characters per fragment
            highlighter.setTextFragmenter(new SimpleFragmenter(150));

        } catch (ParseException e) {
            // If query parsing fails, just skip highlighting
            logger.debug("Could not create highlighter", e);
        }
    }

    // Process each hit
    for (int i = start; i < scoreDocs.length; i++) {
        Document doc = searcher.doc(scoreDocs[i].doc);
        Job job = documentToJob(doc);

        Map<String, String> highlights = new HashMap<>();

        if (highlighter != null) {
            try {
                // getBestFragment returns the most relevant snippet
                // Returns null if field doesn't contain query terms
                String titleHighlight = highlighter.getBestFragment(
                    analyzer,
                    "title",
                    job.getTitle()
                );
                if (titleHighlight != null) {
                    highlights.put("title", titleHighlight);
                }

                String descHighlight = highlighter.getBestFragment(
                    analyzer,
                    "description",
                    job.getDescription()
                );
                if (descHighlight != null) {
                    highlights.put("description", descHighlight);
                }

            } catch (InvalidTokenOffsetsException e) {
                // Happens if analyzer behavior differs between index and search
                logger.debug("Could not highlight", e);
            }
        }

        hits.add(new JobHit(job, scoreDocs[i].score, highlights));
    }

    return hits;
}
```

---

## Summary

### Request Flow Overview

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Client    │────▶│ Controller  │────▶│   Service   │
└─────────────┘     └─────────────┘     └──────┬──────┘
                                               │
                    ┌──────────────────────────┴──────────────────────────┐
                    │                                                     │
                    ▼                                                     ▼
           ┌───────────────┐                                    ┌───────────────┐
           │  IndexWriter  │                                    │SearcherManager│
           │  (writes)     │                                    │   (reads)     │
           └───────┬───────┘                                    └───────┬───────┘
                   │                                                    │
                   └────────────────────┬───────────────────────────────┘
                                        │
                                        ▼
                              ┌───────────────────┐
                              │   Lucene Index    │
                              │  (./data/index)   │
                              └───────────────────┘
```

### Key Takeaways

1. **Lucene is embedded** - No separate server, just a library in your JVM
2. **Documents have fields** - Like entity properties, but with specific types
3. **Analyzers matter** - They determine how text is tokenized and searched
4. **Acquire/release pattern** - Thread-safe searcher access
5. **MUST vs FILTER** - Use FILTER for non-scoring criteria (faster, cacheable)
6. **Commit = visible** - Documents aren't searchable until committed + refreshed
7. **Near real-time** - Balance between latency and throughput with commit strategies
