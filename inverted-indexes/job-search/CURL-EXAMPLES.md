# Job Search API - Curl Examples

## Start the Service

```bash
# Build and run
mvn clean package -DskipTests
java -jar target/job-search-1.0.0.jar

# Or with Docker
docker-compose up --build
```

## Search Endpoints

### 1. Simple Text Search (GET)

```bash
curl "http://localhost:8080/api/jobs/search?q=java+developer"
```

### 2. Search with Pagination

```bash
curl "http://localhost:8080/api/jobs/search?q=developer&page=0&size=5"
```

### 3. Search with Filters

```bash
# Remote senior jobs
curl "http://localhost:8080/api/jobs/search?q=developer&remote=true&experienceLevel=SENIOR"

# Jobs in specific locations
curl "http://localhost:8080/api/jobs/search?q=engineer&location=San+Francisco&location=Seattle"

# Filter by job type
curl "http://localhost:8080/api/jobs/search?jobType=FULL_TIME&jobType=CONTRACT"

# Salary range filter (150k+)
curl "http://localhost:8080/api/jobs/search?salaryMin=150000"

# Combined filters
curl "http://localhost:8080/api/jobs/search?q=python&remote=true&experienceLevel=SENIOR&salaryMin=150000"
```

### 4. Search with Sorting

```bash
# Sort by date (newest first)
curl "http://localhost:8080/api/jobs/search?q=developer&sortBy=date"

# Sort by salary (highest first)
curl "http://localhost:8080/api/jobs/search?sortBy=salaryDesc"

# Sort by salary (lowest first)
curl "http://localhost:8080/api/jobs/search?sortBy=salaryAsc"
```

### 5. POST Search with JSON Body

```bash
curl -X POST "http://localhost:8080/api/jobs/search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "java developer",
    "locations": ["San Francisco", "Remote"],
    "jobTypes": ["FULL_TIME"],
    "experienceLevels": ["SENIOR", "MID"],
    "salaryMin": 100000,
    "salaryMax": 200000,
    "remote": true,
    "skills": ["Spring Boot"],
    "sortBy": "relevance",
    "page": 0,
    "size": 10
  }'
```

### 6. Get Job by ID

```bash
curl "http://localhost:8080/api/jobs/job-001"
```

## Index Management Endpoints

### 7. Check Index Status

```bash
curl "http://localhost:8080/api/index/status"
```

### 8. Index a New Job (Sync)

```bash
curl -X POST "http://localhost:8080/api/index/job" \
  -H "Content-Type: application/json" \
  -d '{
    "id": "job-custom-001",
    "title": "Rust Systems Engineer",
    "company": "MemorySafe Inc",
    "description": "Build high-performance systems in Rust. Memory safety is our priority.",
    "location": "Remote",
    "jobType": "FULL_TIME",
    "experienceLevel": "SENIOR",
    "salaryMin": 180000,
    "salaryMax": 220000,
    "skills": ["Rust", "Systems Programming", "Linux"],
    "postedDate": "2025-01-17",
    "remote": true
  }'
```

### 9. Index a New Job (Async)

```bash
curl -X POST "http://localhost:8080/api/index/job/async" \
  -H "Content-Type: application/json" \
  -d '{
    "id": "job-async-001",
    "title": "Go Backend Developer",
    "company": "Concurrent Systems",
    "description": "Build scalable microservices in Go.",
    "location": "Chicago",
    "jobType": "FULL_TIME",
    "experienceLevel": "MID",
    "salaryMin": 130000,
    "salaryMax": 170000,
    "skills": ["Go", "Kubernetes", "gRPC"],
    "postedDate": "2025-01-17",
    "remote": true
  }'
```

### 10. Delete a Job

```bash
# Sync delete
curl -X DELETE "http://localhost:8080/api/index/job/job-custom-001"

# Async delete
curl -X DELETE "http://localhost:8080/api/index/job/job-async-001/async"
```

### 11. Reindex All Jobs

```bash
curl -X POST "http://localhost:8080/api/index/reindex"
```

### 12. Clear Entire Index

```bash
curl -X DELETE "http://localhost:8080/api/index"
```

## Sample Response

```json
{
  "hits": [
    {
      "job": {
        "id": "job-001",
        "title": "Senior Java Developer",
        "company": "TechCorp Inc",
        "description": "We are looking for an experienced Java developer...",
        "location": "San Francisco",
        "jobType": "FULL_TIME",
        "experienceLevel": "SENIOR",
        "salaryMin": 150000,
        "salaryMax": 200000,
        "skills": ["Java", "Spring Boot", "Kubernetes"],
        "postedDate": "2025-01-10",
        "remote": true
      },
      "score": 6.5,
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
      {"value": "FULL_TIME", "count": 35}
    ],
    "experienceLevel": [
      {"value": "SENIOR", "count": 20}
    ],
    "skills": [
      {"value": "Java", "count": 25}
    ]
  },
  "searchTimeMs": 12
}
```

## Query Parameters Reference

| Parameter | Type | Description |
|-----------|------|-------------|
| `q` | string | Search query text |
| `location` | string[] | Filter by location(s) |
| `jobType` | enum[] | FULL_TIME, PART_TIME, CONTRACT, INTERNSHIP |
| `experienceLevel` | enum[] | ENTRY, MID, SENIOR, LEAD, EXECUTIVE |
| `salaryMin` | int | Minimum salary filter |
| `salaryMax` | int | Maximum salary filter |
| `remote` | boolean | Remote jobs only |
| `skill` | string[] | Filter by skill(s) |
| `sortBy` | string | relevance, date, salaryAsc, salaryDesc |
| `page` | int | Page number (0-indexed) |
| `size` | int | Results per page |
