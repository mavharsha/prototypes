# Lucene and Elasticsearch: Architecture Guide

This document explains Apache Lucene, why we use it as an embedded library, and how to scale with Elasticsearch when needed.

## Table of Contents

1. [What is Apache Lucene?](#1-what-is-apache-lucene)
2. [Embedded vs Service Architecture](#2-embedded-vs-service-architecture)
3. [Why We Chose Embedded Lucene](#3-why-we-chose-embedded-lucene)
4. [When Embedded Becomes a Problem](#4-when-embedded-becomes-a-problem)
5. [Elasticsearch: Lucene as a Service](#5-elasticsearch-lucene-as-a-service)
6. [Migration Path: Lucene to Elasticsearch](#6-migration-path-lucene-to-elasticsearch)
7. [Architecture Comparison](#7-architecture-comparison)
8. [Decision Framework](#8-decision-framework)

---

## 1. What is Apache Lucene?

### Overview

Apache Lucene is a **high-performance, full-text search library** written in Java. It is not a server, application, or service—it's a library you embed into your application.

```
┌─────────────────────────────────────────────────────────────────┐
│                    What Lucene IS                               │
├─────────────────────────────────────────────────────────────────┤
│  • A Java library (JAR files)                                   │
│  • An inverted index implementation                             │
│  • A query parser and search engine                             │
│  • A text analysis framework                                    │
│  • A scoring/ranking system (TF-IDF, BM25)                      │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    What Lucene IS NOT                           │
├─────────────────────────────────────────────────────────────────┤
│  • A server or standalone application                           │
│  • A distributed system                                         │
│  • A REST API                                                   │
│  • A database replacement                                       │
└─────────────────────────────────────────────────────────────────┘
```

### History and Adoption

| Year | Milestone |
|------|-----------|
| 1999 | Doug Cutting creates Lucene |
| 2001 | Donated to Apache Software Foundation |
| 2004 | Solr created (Lucene + server) |
| 2010 | Elasticsearch created (Lucene + distributed) |
| Today | Powers most search infrastructure worldwide |

### Who Uses Lucene?

Directly or through Elasticsearch/Solr:

- **Wikipedia** - Article search
- **Stack Overflow** - Question search
- **Netflix** - Content discovery
- **LinkedIn** - People/job search
- **Twitter** - Tweet search
- **GitHub** - Code search
- **Uber** - Location search
- **Most Fortune 500 companies**

### Core Capabilities

```
┌─────────────────────────────────────────────────────────────────┐
│                     Lucene Core Features                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  INDEXING                          SEARCHING                    │
│  ┌─────────────────────┐           ┌─────────────────────┐      │
│  │ • Document storage  │           │ • Full-text queries │      │
│  │ • Field types       │           │ • Boolean logic     │      │
│  │ • Text analysis     │           │ • Phrase matching   │      │
│  │ • Inverted index    │           │ • Wildcard/Fuzzy    │      │
│  │ • Compression       │           │ • Range queries     │      │
│  │ • Segment merging   │           │ • Faceted search    │      │
│  └─────────────────────┘           └─────────────────────┘      │
│                                                                 │
│  ANALYSIS                          SCORING                      │
│  ┌─────────────────────┐           ┌─────────────────────┐      │
│  │ • Tokenization      │           │ • TF-IDF            │      │
│  │ • Lowercasing       │           │ • BM25              │      │
│  │ • Stemming          │           │ • Field boosting    │      │
│  │ • Stop words        │           │ • Custom scoring    │      │
│  │ • Synonyms          │           │ • Relevance tuning  │      │
│  └─────────────────────┘           └─────────────────────┘      │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Embedded vs Service Architecture

### What is "Embedded"?

Embedded means Lucene runs **inside your application process**, just like an embedded database (H2, SQLite).

```
┌─────────────────────────────────────────────────────────────────┐
│                    EMBEDDED ARCHITECTURE                        │
│                    (What we built)                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                    JVM Process                            │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │              Spring Boot Application                │  │  │
│  │  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  │  │  │
│  │  │  │ Controllers │  │  Services   │  │   Config    │  │  │  │
│  │  │  └─────────────┘  └──────┬──────┘  └─────────────┘  │  │  │
│  │  │                          │                          │  │  │
│  │  │                          ▼                          │  │  │
│  │  │               ┌─────────────────────┐               │  │  │
│  │  │               │    Apache Lucene    │               │  │  │
│  │  │               │   (embedded JAR)    │               │  │  │
│  │  │               └──────────┬──────────┘               │  │  │
│  │  │                          │                          │  │  │
│  │  └──────────────────────────┼──────────────────────────┘  │  │
│  │                             │                             │  │
│  └─────────────────────────────┼─────────────────────────────┘  │
│                                │                                │
│                                ▼                                │
│                    ┌─────────────────────┐                      │
│                    │    File System      │                      │
│                    │   ./data/index/     │                      │
│                    │  (Lucene segments)  │                      │
│                    └─────────────────────┘                      │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### What is "Service" Architecture?

Service architecture means search runs as a **separate process** that your application talks to over the network.

```
┌─────────────────────────────────────────────────────────────────┐
│                    SERVICE ARCHITECTURE                         │
│                    (Elasticsearch/Solr)                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────┐         ┌─────────────────────────┐    │
│  │    Your App (JVM)   │         │   Elasticsearch (JVM)   │    │
│  │  ┌───────────────┐  │  HTTP   │  ┌───────────────────┐  │    │
│  │  │ Spring Boot   │  │ ──────► │  │    REST API       │  │    │
│  │  │               │  │ ◄────── │  └─────────┬─────────┘  │    │
│  │  │ ES Client     │  │  JSON   │            │            │    │
│  │  └───────────────┘  │         │  ┌─────────▼─────────┐  │    │
│  └─────────────────────┘         │  │  Cluster Manager  │  │    │
│                                  │  └─────────┬─────────┘  │    │
│                                  │            │            │    │
│                                  │  ┌─────────▼─────────┐  │    │
│                                  │  │  Apache Lucene    │  │    │
│                                  │  └─────────┬─────────┘  │    │
│                                  │            │            │    │
│                                  └────────────┼────────────┘    │
│                                               │                 │
│                                               ▼                 │
│                                  ┌─────────────────────┐        │
│                                  │    File System      │        │
│                                  │  /var/lib/es/data   │        │
│                                  └─────────────────────┘        │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Comparison Table

| Aspect | Embedded Lucene | Elasticsearch |
|--------|-----------------|---------------|
| **Deployment** | Part of your app | Separate service |
| **Communication** | Method calls (in-process) | HTTP/REST (network) |
| **Latency** | Microseconds | Milliseconds |
| **Scaling** | Scale with app | Scale independently |
| **Complexity** | Simple | More infrastructure |
| **Multiple instances** | Each has own index | Shared cluster |
| **Language** | Java only | Any (REST API) |
| **Operations** | Your app's ops | Dedicated search ops |

---

## 3. Why We Chose Embedded Lucene

For this prototype, embedded Lucene was the right choice for several reasons:

### 3.1 Educational Value

```
┌─────────────────────────────────────────────────────────────────┐
│  With Embedded Lucene, you see everything:                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  • How documents are created                                    │
│    → new Document(), add(new TextField(...))                    │
│                                                                 │
│  • How fields are configured                                    │
│    → StringField vs TextField vs IntPoint                       │
│                                                                 │
│  • How queries are built                                        │
│    → BooleanQuery, TermQuery, RangeQuery                        │
│                                                                 │
│  • How analysis works                                           │
│    → StandardAnalyzer tokenization                              │
│                                                                 │
│  • How commits/refresh work                                     │
│    → indexWriter.commit(), searcherManager.maybeRefresh()       │
│                                                                 │
│  With Elasticsearch, these are hidden behind:                   │
│    → JSON mappings and REST calls                               │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 Simplicity

```
Embedded:                          Elasticsearch:
─────────────────                  ─────────────────
1 container                        3+ containers
1 process                          Multiple processes
0 network calls                    Network latency
No cluster config                  Cluster configuration
No separate ops                    Dedicated monitoring

docker-compose.yml:                docker-compose.yml:
  services:                          services:
    job-search:                        job-search:
      build: .                           build: .
                                       elasticsearch:
                                         image: elasticsearch:8.x
                                         environment: [...]
                                         volumes: [...]
                                       kibana:  # optional but common
                                         image: kibana:8.x
```

### 3.3 Lower Latency

```
Embedded Lucene:
┌──────────┐  method call   ┌────────────┐
│   App    │ ────────────► │   Lucene   │   ~0.1ms
└──────────┘  (in-process)  └────────────┘

Elasticsearch:
┌──────────┐  HTTP request  ┌────────────┐
│   App    │ ────────────► │     ES     │   ~1-5ms
└──────────┘  (network)     └────────────┘

For high-throughput, low-latency requirements:
• 0.1ms × 10,000 requests = 1 second
• 3ms × 10,000 requests = 30 seconds
```

### 3.4 Prototype Scope

| Requirement | Our Prototype | Production |
|-------------|---------------|------------|
| Dataset size | 20 jobs | Millions |
| App instances | 1 | 10+ |
| Availability | Dev machine | 99.9% SLA |
| Updates | Occasional | Real-time |
| Team | 1 developer | Multiple teams |

For our scope, embedded is perfect. For production scale, we'd reconsider.

---

## 4. When Embedded Becomes a Problem

### 4.1 Multiple Application Instances

The biggest issue: **each instance has its own index**.

```
┌─────────────────────────────────────────────────────────────────┐
│                    THE SYNC PROBLEM                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Load Balancer                                                  │
│       │                                                         │
│       ├──────────────┬──────────────┬──────────────┐            │
│       ▼              ▼              ▼              ▼            │
│  ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐       │
│  │ App #1  │    │ App #2  │    │ App #3  │    │ App #4  │       │
│  │┌───────┐│    │┌───────┐│    │┌───────┐│    │┌───────┐│       │
│  ││Index A││    ││Index B││    ││Index C││    ││Index D││       │
│  │└───────┘│    │└───────┘│    │└───────┘│    │└───────┘│       │
│  └─────────┘    └─────────┘    └─────────┘    └─────────┘       │
│       │              │              │              │            │
│       ▼              ▼              ▼              ▼            │
│  User adds      User searches   User searches   User searches  │
│  new job        (doesn't see    (doesn't see    (doesn't see   │
│  (indexed       new job!)       new job!)       new job!)      │
│  only here)                                                     │
│                                                                 │
│  PROBLEM: Indexes are NOT synchronized!                         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 4.2 Potential Solutions (and their problems)

```
┌─────────────────────────────────────────────────────────────────┐
│  Solution 1: Shared File System (NFS/EFS)                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐                          │
│  │ App #1  │  │ App #2  │  │ App #3  │                          │
│  └────┬────┘  └────┬────┘  └────┬────┘                          │
│       │            │            │                               │
│       └────────────┼────────────┘                               │
│                    ▼                                            │
│           ┌───────────────┐                                     │
│           │  Shared NFS   │                                     │
│           │  /mnt/index   │                                     │
│           └───────────────┘                                     │
│                                                                 │
│  PROBLEM:                                                       │
│  • Lucene IndexWriter requires exclusive lock                   │
│  • Only ONE writer allowed at a time                            │
│  • NFS has locking issues                                       │
│  • Performance is poor                                          │
│                                                                 │
│  VERDICT: ❌ Don't do this                                      │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  Solution 2: Write to One, Read from All                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────┐                    │
│  │            Message Queue                │                    │
│  │  (All index operations go here)         │                    │
│  └────────────────────┬────────────────────┘                    │
│                       │                                         │
│                       ▼                                         │
│  ┌─────────────────────────────────────────┐                    │
│  │       Dedicated Indexer Service         │                    │
│  │  (Single writer, broadcasts to others)  │                    │
│  └────────────────────┬────────────────────┘                    │
│                       │                                         │
│       ┌───────────────┼───────────────┐                         │
│       ▼               ▼               ▼                         │
│  ┌─────────┐    ┌─────────┐    ┌─────────┐                      │
│  │ App #1  │    │ App #2  │    │ App #3  │                      │
│  │ (read)  │    │ (read)  │    │ (read)  │                      │
│  └─────────┘    └─────────┘    └─────────┘                      │
│                                                                 │
│  PROBLEM:                                                       │
│  • Complex architecture                                         │
│  • You're building your own Elasticsearch                       │
│  • Replication lag                                              │
│                                                                 │
│  VERDICT: ⚠️ Works but complex                                  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  Solution 3: Just Use Elasticsearch                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐                          │
│  │ App #1  │  │ App #2  │  │ App #3  │                          │
│  └────┬────┘  └────┬────┘  └────┬────┘                          │
│       │            │            │                               │
│       └────────────┼────────────┘                               │
│                    ▼                                            │
│           ┌───────────────┐                                     │
│           │ Elasticsearch │                                     │
│           │   Cluster     │                                     │
│           └───────────────┘                                     │
│                                                                 │
│  BENEFITS:                                                      │
│  • Built-in clustering                                          │
│  • Built-in replication                                         │
│  • REST API (language agnostic)                                 │
│  • Proven at scale                                              │
│                                                                 │
│  VERDICT: ✅ Recommended for production                         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 4.3 Other Scaling Challenges

| Challenge | Embedded Limitation |
|-----------|---------------------|
| **Index size** | Limited by single machine disk/RAM |
| **Query throughput** | Limited by single JVM |
| **Availability** | App dies = search dies |
| **Zero-downtime deploy** | Hard to achieve |
| **Index rebuild** | Blocks the app or needs second index |

---

## 5. Elasticsearch: Lucene as a Service

### What Elasticsearch Adds to Lucene

```
┌─────────────────────────────────────────────────────────────────┐
│                      ELASTICSEARCH                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                     REST API                              │  │
│  │  • HTTP endpoints for all operations                      │  │
│  │  • JSON request/response                                  │  │
│  │  • Language-agnostic (curl, Python, Java, Node...)        │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                   DISTRIBUTION                            │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │  │
│  │  │   Sharding  │  │ Replication │  │  Routing    │        │  │
│  │  │ Split index │  │   Copies    │  │ Find shard  │        │  │
│  │  │  across     │  │    for      │  │   for doc   │        │  │
│  │  │   nodes     │  │   failover  │  │             │        │  │
│  │  └─────────────┘  └─────────────┘  └─────────────┘        │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                 CLUSTER MANAGEMENT                        │  │
│  │  • Node discovery                                         │  │
│  │  • Master election                                        │  │
│  │  • Health monitoring                                      │  │
│  │  • Automatic rebalancing                                  │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                 ADDITIONAL FEATURES                       │  │
│  │  • Aggregations (analytics)                               │  │
│  │  • Geo queries                                            │  │
│  │  • Machine learning                                       │  │
│  │  • Security (authentication, authorization)               │  │
│  │  • Cross-cluster search                                   │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                   APACHE LUCENE                           │  │
│  │             (The actual search engine)                    │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Sharding Explained

```
┌─────────────────────────────────────────────────────────────────┐
│                    INDEX WITH 3 SHARDS                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  "jobs" index                                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                                                          │   │
│  │   Shard 0          Shard 1          Shard 2              │   │
│  │  ┌──────────┐     ┌──────────┐     ┌──────────┐          │   │
│  │  │ Jobs     │     │ Jobs     │     │ Jobs     │          │   │
│  │  │ A-H      │     │ I-P      │     │ Q-Z      │          │   │
│  │  │          │     │          │     │          │          │   │
│  │  │ (Node 1) │     │ (Node 2) │     │ (Node 3) │          │   │
│  │  └──────────┘     └──────────┘     └──────────┘          │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                 │
│  Benefits:                                                      │
│  • Parallel search across shards                                │
│  • Distribute data across machines                              │
│  • Handle larger datasets than single machine                   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Replication Explained

```
┌─────────────────────────────────────────────────────────────────┐
│              SHARDS WITH 1 REPLICA EACH                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Node 1               Node 2               Node 3               │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────────┐     │
│  │              │     │              │     │              │     │
│  │ [P] Shard 0  │     │ [P] Shard 1  │     │ [P] Shard 2  │     │
│  │ (primary)    │     │ (primary)    │     │ (primary)    │     │
│  │              │     │              │     │              │     │
│  │ [R] Shard 1  │     │ [R] Shard 2  │     │ [R] Shard 0  │     │
│  │ (replica)    │     │ (replica)    │     │ (replica)    │     │
│  │              │     │              │     │              │     │
│  └──────────────┘     └──────────────┘     └──────────────┘     │
│                                                                 │
│  If Node 1 dies:                                                │
│  • Shard 0 replica on Node 3 becomes primary                    │
│  • No data loss                                                 │
│  • Search continues working                                     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 6. Migration Path: Lucene to Elasticsearch

### Concept Mapping

Our Lucene implementation maps directly to Elasticsearch:

```
┌──────────────────────────┬──────────────────────────────────────┐
│     Our Lucene Code      │       Elasticsearch Equivalent       │
├──────────────────────────┼──────────────────────────────────────┤
│                          │                                      │
│ Directory                │ Index                                │
│ ./data/index             │ /jobs                                │
│                          │                                      │
├──────────────────────────┼──────────────────────────────────────┤
│                          │                                      │
│ Document                 │ Document                             │
│ new Document()           │ JSON object                          │
│                          │                                      │
├──────────────────────────┼──────────────────────────────────────┤
│                          │                                      │
│ TextField                │ "type": "text"                       │
│ StringField              │ "type": "keyword"                    │
│ IntPoint                 │ "type": "integer"                    │
│ LongPoint                │ "type": "long"                       │
│                          │                                      │
├──────────────────────────┼──────────────────────────────────────┤
│                          │                                      │
│ IndexWriter.addDocument  │ POST /index/_doc                     │
│ IndexWriter.updateDoc    │ PUT /index/_doc/{id}                 │
│ IndexWriter.deleteDoc    │ DELETE /index/_doc/{id}              │
│                          │                                      │
├──────────────────────────┼──────────────────────────────────────┤
│                          │                                      │
│ IndexSearcher.search     │ GET /index/_search                   │
│ BooleanQuery             │ "bool": { "must", "filter" }         │
│ TermQuery                │ "term": { "field": "value" }         │
│ RangeQuery               │ "range": { "field": { "gte": n } }   │
│                          │                                      │
├──────────────────────────┼──────────────────────────────────────┤
│                          │                                      │
│ StandardAnalyzer         │ "analyzer": "standard"               │
│ Highlighter              │ "highlight": { "fields": {...} }     │
│ Facets                   │ "aggs": { ... }                      │
│                          │                                      │
└──────────────────────────┴──────────────────────────────────────┘
```

### Field Type Mapping

```java
// Our Lucene field definitions
doc.add(new StringField("id", job.getId(), Store.YES));
doc.add(new TextField("title", job.getTitle(), Store.YES));
doc.add(new TextField("description", job.getDescription(), Store.YES));
doc.add(new StringField("jobType", job.getJobType().name(), Store.YES));
doc.add(new IntPoint("salaryMin", job.getSalaryMin()));
doc.add(new StoredField("salaryMin", job.getSalaryMin()));
```

```json
// Equivalent Elasticsearch mapping
PUT /jobs
{
  "mappings": {
    "properties": {
      "id": {
        "type": "keyword"
      },
      "title": {
        "type": "text",
        "analyzer": "standard"
      },
      "description": {
        "type": "text",
        "analyzer": "standard"
      },
      "jobType": {
        "type": "keyword"
      },
      "salaryMin": {
        "type": "integer"
      }
    }
  }
}
```

### Query Mapping

```java
// Our Lucene query
BooleanQuery.Builder boolQuery = new BooleanQuery.Builder();

// Text search with field boosting
MultiFieldQueryParser parser = new MultiFieldQueryParser(
    new String[]{"title", "description"},
    analyzer,
    Map.of("title", 3.0f, "description", 1.0f)
);
boolQuery.add(parser.parse("java developer"), BooleanClause.Occur.MUST);

// Filters
boolQuery.add(
    new TermQuery(new Term("jobType", "FULL_TIME")),
    BooleanClause.Occur.FILTER
);
boolQuery.add(
    IntPoint.newRangeQuery("salaryMin", 100000, Integer.MAX_VALUE),
    BooleanClause.Occur.FILTER
);
```

```json
// Equivalent Elasticsearch query
GET /jobs/_search
{
  "query": {
    "bool": {
      "must": [
        {
          "multi_match": {
            "query": "java developer",
            "fields": ["title^3", "description"]
          }
        }
      ],
      "filter": [
        {
          "term": {
            "jobType": "FULL_TIME"
          }
        },
        {
          "range": {
            "salaryMin": {
              "gte": 100000
            }
          }
        }
      ]
    }
  }
}
```

### Spring Boot with Elasticsearch

#### Add Dependencies

```xml
<dependency>
    <groupId>org.springframework.data</groupId>
    <artifactId>spring-data-elasticsearch</artifactId>
</dependency>
<!-- or -->
<dependency>
    <groupId>co.elastic.clients</groupId>
    <artifactId>elasticsearch-java</artifactId>
    <version>8.11.0</version>
</dependency>
```

#### Configuration

```yaml
# application.yml
spring:
  elasticsearch:
    uris: http://localhost:9200
    username: elastic
    password: changeme
```

#### Entity Mapping

```java
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

@Document(indexName = "jobs")
public class Job {

    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String title;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Keyword)
    private String jobType;

    @Field(type = FieldType.Integer)
    private int salaryMin;

    @Field(type = FieldType.Integer)
    private int salaryMax;

    @Field(type = FieldType.Keyword)
    private String[] skills;

    @Field(type = FieldType.Date, format = DateFormat.date)
    private LocalDate postedDate;

    // getters/setters
}
```

#### Repository

```java
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface JobRepository extends ElasticsearchRepository<Job, String> {

    List<Job> findByTitle(String title);

    List<Job> findByJobType(String jobType);

    List<Job> findBySalaryMinGreaterThanEqual(int salary);
}
```

#### Custom Search Service

```java
@Service
public class JobSearchService {

    private final ElasticsearchClient esClient;

    public SearchResponse search(SearchRequest request) throws IOException {
        SearchResponse<Job> response = esClient.search(s -> s
            .index("jobs")
            .query(q -> q
                .bool(b -> {
                    // Text query
                    if (request.getQuery() != null) {
                        b.must(m -> m
                            .multiMatch(mm -> mm
                                .query(request.getQuery())
                                .fields("title^3", "description", "skills^2")
                            )
                        );
                    }

                    // Filters
                    if (request.getJobType() != null) {
                        b.filter(f -> f
                            .term(t -> t
                                .field("jobType")
                                .value(request.getJobType())
                            )
                        );
                    }

                    if (request.getSalaryMin() != null) {
                        b.filter(f -> f
                            .range(r -> r
                                .field("salaryMax")
                                .gte(JsonData.of(request.getSalaryMin()))
                            )
                        );
                    }

                    return b;
                })
            )
            .aggregations("jobTypes", a -> a
                .terms(t -> t.field("jobType"))
            )
            .highlight(h -> h
                .fields("title", f -> f)
                .fields("description", f -> f)
            )
            .from(request.getPage() * request.getSize())
            .size(request.getSize()),
            Job.class
        );

        return convertResponse(response);
    }
}
```

---

## 7. Architecture Comparison

### Development vs Production

```
┌─────────────────────────────────────────────────────────────────┐
│                    DEVELOPMENT                                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                   Single Container                      │    │
│  │  ┌─────────────────────────────────────────────────┐    │    │
│  │  │              job-search:8080                    │    │    │
│  │  │  ┌────────────────┐  ┌────────────────┐         │    │    │
│  │  │  │  Spring Boot   │  │  Embedded      │         │    │    │
│  │  │  │  REST API      │  │  Lucene        │         │    │    │
│  │  │  └────────────────┘  └────────────────┘         │    │    │
│  │  └─────────────────────────────────────────────────┘    │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
│  Pros: Simple, fast iteration, easy debugging                   │
│  Cons: Single instance only                                     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    PRODUCTION                                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Load Balancer                                                  │
│       │                                                         │
│       ├──────────────────────────────────────┐                  │
│       ▼                                      ▼                  │
│  ┌──────────────┐  ┌──────────────┐    ┌──────────────┐         │
│  │ job-search-1 │  │ job-search-2 │    │ job-search-3 │         │
│  │   (app only) │  │   (app only) │    │   (app only) │         │
│  └──────┬───────┘  └──────┬───────┘    └──────┬───────┘         │
│         │                 │                   │                 │
│         └─────────────────┼───────────────────┘                 │
│                           │                                     │
│                           ▼                                     │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                 Elasticsearch Cluster                     │   │
│  │  ┌────────────┐  ┌────────────┐  ┌────────────┐          │   │
│  │  │   Node 1   │  │   Node 2   │  │   Node 3   │          │   │
│  │  │  (master)  │  │   (data)   │  │   (data)   │          │   │
│  │  └────────────┘  └────────────┘  └────────────┘          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                 │
│  Pros: Scalable, highly available, production-ready             │
│  Cons: More infrastructure, operational complexity              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Docker Compose: Production Setup

```yaml
version: '3.8'

services:
  # Application (stateless, can scale)
  job-search:
    build: .
    deploy:
      replicas: 3
    environment:
      - SPRING_ELASTICSEARCH_URIS=http://elasticsearch:9200
    depends_on:
      - elasticsearch
    networks:
      - app-network

  # Elasticsearch cluster
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.11.0
    environment:
      - discovery.type=single-node  # Use cluster config for production
      - xpack.security.enabled=false
      - "ES_JAVA_OPTS=-Xms1g -Xmx1g"
    volumes:
      - es-data:/usr/share/elasticsearch/data
    ports:
      - "9200:9200"
    networks:
      - app-network

  # Kibana (optional, for management)
  kibana:
    image: docker.elastic.co/kibana/kibana:8.11.0
    environment:
      - ELASTICSEARCH_HOSTS=http://elasticsearch:9200
    ports:
      - "5601:5601"
    depends_on:
      - elasticsearch
    networks:
      - app-network

  # Nginx load balancer
  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf:ro
    depends_on:
      - job-search
    networks:
      - app-network

volumes:
  es-data:

networks:
  app-network:
```

---

## 8. Decision Framework

### When to Use Each Approach

```
┌─────────────────────────────────────────────────────────────────┐
│                    DECISION TREE                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  START                                                          │
│    │                                                            │
│    ▼                                                            │
│  ┌─────────────────────────────────────┐                        │
│  │ Is this a learning project or POC?  │                        │
│  └─────────────────┬───────────────────┘                        │
│                    │                                            │
│         YES ───────┴─────── NO                                  │
│          │                   │                                  │
│          ▼                   ▼                                  │
│  ┌───────────────┐   ┌─────────────────────────────────────┐    │
│  │ Use Embedded  │   │ Will you have multiple app instances?│    │
│  │ Lucene        │   └─────────────────┬───────────────────┘    │
│  └───────────────┘                     │                        │
│                           YES ─────────┴─────── NO              │
│                            │                     │              │
│                            ▼                     ▼              │
│                  ┌───────────────┐    ┌────────────────────┐    │
│                  │ Use           │    │ Is low latency      │    │
│                  │ Elasticsearch │    │ critical (<1ms)?    │    │
│                  └───────────────┘    └─────────┬──────────┘    │
│                                                 │               │
│                                      YES ───────┴─────── NO     │
│                                       │                   │     │
│                                       ▼                   ▼     │
│                            ┌───────────────┐   ┌───────────────┐│
│                            │ Use Embedded  │   │ Use           ││
│                            │ Lucene        │   │ Elasticsearch ││
│                            └───────────────┘   └───────────────┘│
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Summary Matrix

| Factor | Embedded Lucene | Elasticsearch |
|--------|-----------------|---------------|
| **Learning curve** | Learn Lucene internals | Learn ES API + Lucene concepts |
| **Deployment** | Simple (one process) | Complex (multiple services) |
| **Scaling** | Vertical only | Horizontal + Vertical |
| **Multi-instance** | ❌ Not supported | ✅ Built-in |
| **High availability** | ❌ Manual | ✅ Built-in |
| **Latency** | ~0.1ms | ~1-5ms |
| **Operational cost** | Low | Medium-High |
| **Language support** | Java only | Any (REST API) |
| **Best for** | Prototypes, single-instance apps, low-latency | Production, multi-tenant, large scale |

### Our Recommendation

```
┌─────────────────────────────────────────────────────────────────┐
│                    RECOMMENDED PATH                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Phase 1: Development & Learning                                │
│  ─────────────────────────────────                              │
│  • Use embedded Lucene (what we built)                          │
│  • Understand core concepts                                     │
│  • Fast iteration, easy debugging                               │
│                                                                 │
│  Phase 2: Staging & Testing                                     │
│  ─────────────────────────────────                              │
│  • Add Elasticsearch to docker-compose                          │
│  • Create abstraction layer (SearchService interface)           │
│  • Support both embedded and ES via config                      │
│                                                                 │
│  Phase 3: Production                                            │
│  ─────────────────────────────────                              │
│  • Use Elasticsearch (or OpenSearch)                            │
│  • Set up proper cluster                                        │
│  • Add monitoring (Kibana, Prometheus)                          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Abstraction Layer Example

To support both embedded and Elasticsearch:

```java
// Interface
public interface SearchService {
    SearchResponse search(SearchRequest request);
    void index(Job job);
    void delete(String jobId);
}

// Embedded implementation (current)
@Service
@Profile("embedded")
public class LuceneSearchService implements SearchService {
    // Current implementation
}

// Elasticsearch implementation
@Service
@Profile("elasticsearch")
public class ElasticsearchSearchService implements SearchService {
    // ES client implementation
}
```

```yaml
# application.yml
spring:
  profiles:
    active: embedded  # or 'elasticsearch'
```

This allows switching between implementations without changing application code.
