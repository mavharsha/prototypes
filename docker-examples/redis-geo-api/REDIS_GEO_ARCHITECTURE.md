# Redis for Location Services: An Architect's Decision Framework

This document addresses three questions that come up in every design review when someone proposes Redis for a location-based service. It is not a textbook summary — it is a decision framework with numbers, failure scenarios, and migration paths.

---

## Section A: Should Redis Be the Primary Datastore for Location Services?

### A.1 What Redis Gets Right

#### Sub-Millisecond Latency

GEOSEARCH on a 1M-member sorted set completes in ~1–2ms on a single Redis instance (no network overhead). With network, expect 2–3ms on the same availability zone.

Compare:

| Engine | 1M locations, radius query, p50 | p99 |
|---|---|---|
| Redis GEOSEARCH | < 1 ms | 2–3 ms |
| PostGIS ST_DWithin (GiST index) | 5–15 ms | 30 ms |
| MongoDB $near (2dsphere) | 3–10 ms | 20 ms |
| Elasticsearch geo_distance | 10–50 ms | 100 ms |

This matters for latency-critical paths: ride-sharing matching, delivery ETA widgets, real-time fleet dashboards. When you need a response in < 5ms, Redis is one of the few options that can deliver.

#### Operational Simplicity

One data structure (sorted set) handles spatial indexing. No spatial index configuration, no VACUUM, no query planner tuning, no extension installation.

The entire geo API in `index.js` is ~150 lines including error handling and routing. An equivalent PostGIS implementation would be ~300–400 lines (connection pooling, query building, ORM/query builder setup, migration files, spatial index DDL).

Deployment: one container (`redis:7-alpine`, 7 MB image). Compare: `postgres:15` + PostGIS (~400 MB image) + connection pooler (pgbouncer, another container).

#### Native Commands Reduce Application Complexity

GEOSEARCH, GEODIST, GEOPOS, GEOHASH are single atomic commands. No SQL string construction, no ORM abstraction leaks, no query plan regressions.

Pipelining multiple geo commands is trivial:

```javascript
const pipeline = redis.multi();
pipeline.geoSearch("stores:locations", { longitude, latitude }, { radius: 10, unit: "mi" });
pipeline.geoDist("stores:locations", "store:1", "store:2", "mi");
const [nearby, distance] = await pipeline.exec();
```

This batches two geo operations into a single network round-trip. Achieving this in SQL requires CTEs or subqueries that are harder to compose and debug.

---

### A.2 What Redis Gets Wrong

#### Memory Cost at Scale

Everything is in RAM. Here is what the current data model costs:

| Scale | Geo Index | Metadata Hashes | Inventory Hashes | Total RAM |
|---|---|---|---|---|
| 1K stores (3 products each) | ~80 KB | ~150 KB | ~300 KB | ~530 KB |
| 100K stores | ~8 MB | ~15 MB | ~30 MB | ~53 MB |
| 1M stores | ~80 MB | ~150 MB | ~300 MB | ~530 MB |
| 10M stores | ~800 MB | ~1.5 GB | ~3 GB | ~5.3 GB |
| 100M stores | ~8 GB | ~15 GB | ~30 GB | ~53 GB |

Compare: PostgreSQL with the same 1M-store dataset on disk: ~500 MB on disk, ~50 MB of index in shared_buffers. The ratio is roughly **10:1 in RAM cost**.

At 10M locations, you are paying for 5+ GB of RAM for a single service. This is feasible but expensive — especially if you need redundancy (replicas double the cost).

#### No Complex Queries

The `POST /stores/search-inventory` endpoint in `index.js` illustrates the problem. In SQL this would be:

```sql
SELECT s.name, p.name, i.stock_qty,
       ST_Distance(s.location, ST_MakePoint(-74.006, 40.7128)::geography) AS dist
FROM stores s
JOIN inventory i ON i.store_id = s.id
JOIN products p ON p.id = i.product_id
WHERE ST_DWithin(s.location, ST_MakePoint(-74.006, 40.7128)::geography, 16093)  -- 10 miles
  AND i.stock_qty > 0
  AND p.name ILIKE '%puma%'
ORDER BY dist;
```

One query, one round-trip, fully optimized by the query planner, using indexes on all three tables.

In Redis, the same operation requires:

```
1. GEOSEARCH              → find nearby stores
2. For each store:
   a. HGETALL store:N     → get metadata
   b. SCAN inventory:*    → find inventory keys
   c. For each key:
      i.  HGETALL inv     → get inventory record
      ii. HGETALL prod    → get product details
      iii. String match   → filter by product name
3. Sort results           → in application code
```

This is **O(N) in total Redis calls** for N nearby stores with M products. It is imperative, hard to optimize, hard to test, and impossible for Redis to optimize internally. Every "complex" query becomes procedural code in your application layer.

#### Volatile by Default

Redis is an in-memory store. Without persistence configured, a restart means **complete data loss**.

The current `docker-compose.yml` mounts a volume (`redis-data:/data`) but does NOT enable AOF or RDB in the Redis command:

```yaml
redis:
  image: redis:7-alpine
  volumes:
    - redis-data:/data
  # No --appendonly yes, no --save configuration
```

Default Redis behavior: periodic RDB snapshots (every 60 seconds if 1000+ keys changed). This means up to **60 seconds of data loss** on an unclean shutdown.

If the geo data originates from a canonical database (and Redis is just a cache), this is acceptable — re-seed from the source. But if Redis IS the primary store, 60 seconds of data loss on a crash is unacceptable.

#### Single-Threaded Command Execution

Redis executes all commands on a single thread. Implications:

- One slow GEOSEARCH (huge radius, millions of results) blocks **all** other clients.
- The `search-inventory` endpoint issues 10–50 sequential commands. During this chain, the entire Redis instance is unresponsive to other requests.
- Redis 7 added I/O threading for network read/write, but command execution remains single-threaded.

At high concurrency (1000+ QPS on geo queries), a single Redis instance can become a bottleneck — not because geo is slow, but because any slow command blocks everything.

---

### A.3 Decision Framework

#### Redis Is Good Enough When

- Location count is **< 1M** (memory is manageable at ~530 MB).
- Queries are **simple**: nearby search, distance, geohash lookup. No multi-table joins.
- Data is **reconstructable** from a canonical source (database, CSV import, third-party API).
- Latency requirement is **< 5ms p99** — this is where Redis is unmatched.
- The team does **not need** full-text search, faceted filtering, or analytical queries on location data.

**Good-fit examples:** Store/ATM/restaurant locator, simple delivery zone check, geofencing alerts, "is this coordinate inside our service area?" checks.

#### Redis Is Not Enough When

- Location count exceeds **10M** (memory cost exceeds ~5 GB, replicas double it).
- Queries require **joining geo data with other dimensions** (price, rating, availability, category) in a single query.
- Data must be **durable with zero data loss** — ACID transactions are required.
- Regulatory requirements mandate **audit trails** or transactional guarantees on location data.
- **Full-text search** on location names/descriptions is needed alongside geo queries (unless you add RediSearch, which is a separate discussion).

**Poor-fit examples:** Real-estate platforms (complex filters + geo), fleet management with analytics and reporting, logistics route optimization, compliance-heavy location tracking.

#### The Middle Ground: Redis as a Cache

The most common production pattern — and what I would recommend for most teams — is:

```
Primary DB (Postgres/Mongo)  →  source of truth, complex queries, ACID
      │
      │  background sync / change-data-capture
      ▼
Redis (geo sorted set)       →  hot path for radius queries, sub-ms latency
```

You get the best of both worlds: durability and queryability from the primary DB, speed from Redis. The cost is operational complexity (two datastores, sync logic, consistency concerns). Section C covers this pattern in detail.

---

## Section B: Making Redis Resilient (Handling Single Point of Failure)

### B.1 Current SPOF Analysis

The current `docker-compose.yml` has a single Redis container:

```
┌─────────┐     ┌─────────┐     ┌──────────────┐
│  api    │────▶│  Redis  │◀────│  api-hash    │
│ :3000   │     │  :6379  │     │  :3001       │
└─────────┘     └─────────┘     └──────────────┘
                     │
                   SPOF
```

If Redis crashes:
- Both APIs return 500 errors immediately (no fallback logic).
- The `redis.on("error", ...)` handler logs the error but does not reconnect.
- There is no health check in the Express apps — they will continue accepting HTTP requests and failing on every one.
- No alerting — the failure is silent until a user reports it.

### B.2 Persistence: Surviving Restarts

#### RDB (Redis Database) Snapshots

Point-in-time binary dump of the entire dataset to disk.

```yaml
# docker-compose.yml — enable RDB
redis:
  image: redis:7-alpine
  command: redis-server --save 60 1000 --save 300 10
  volumes:
    - redis-data:/data
```

- `--save 60 1000`: snapshot every 60 seconds if 1000+ keys changed.
- `--save 300 10`: snapshot every 5 minutes if 10+ keys changed.
- File: `/data/dump.rdb` (compact binary format).

**Tradeoffs:**

| Property | Value |
|---|---|
| Max data loss | Up to 60 seconds (with `save 60 1000`) |
| Performance impact | Fork + copy-on-write. ~10–50ms pause for < 1GB datasets |
| Restore speed | Fast — binary load, no replay |
| File size | Compact (~50% of in-memory size) |

RDB is good for **backups and disaster recovery** but not for minimizing data loss.

#### AOF (Append-Only File)

Logs every write command to a file. On restart, Redis replays the AOF to rebuild state.

```yaml
# docker-compose.yml — enable AOF
redis:
  image: redis:7-alpine
  command: redis-server --appendonly yes --appendfsync everysec
  volumes:
    - redis-data:/data
```

Three fsync policies:

| Policy | Data Loss Window | Performance Impact |
|---|---|---|
| `always` | 0 (every command fsynced) | ~10x slower — every write waits for disk |
| `everysec` | Up to 1 second | Minimal — async fsync once per second |
| `no` | OS-dependent (up to 30s) | None — OS decides when to flush |

`appendfsync everysec` is the standard production choice. You lose at most 1 second of writes on a crash, with negligible performance overhead.

**AOF rewrite:** The AOF file grows indefinitely. Redis periodically compacts it (rewrites the minimal set of commands to recreate current state). During rewrite, writes are buffered in memory — monitor memory usage during this phase.

#### RDB + AOF Together (Recommended)

```yaml
redis:
  image: redis:7-alpine
  command: redis-server --appendonly yes --appendfsync everysec --save 3600 1
  volumes:
    - redis-data:/data
```

- **AOF** for crash recovery with minimal data loss (1 second).
- **RDB** for periodic backups (hourly snapshot) — useful for disaster recovery, migration, or seeding a new replica.
- On restart, Redis prefers AOF if both files exist (AOF is more complete).

---

### B.3 High Availability with Redis Sentinel

#### How It Works

```
┌──────────┐   ┌──────────┐   ┌──────────┐
│Sentinel 1│   │Sentinel 2│   │Sentinel 3│
└─────┬────┘   └─────┬────┘   └─────┬────┘
      │              │              │
      │    monitor + vote           │
      │              │              │
      ▼              ▼              ▼
┌──────────┐   ┌──────────┐   ┌──────────┐
│  Master  │──▶│ Replica 1│   │ Replica 2│
│  (R/W)   │──▶│   (RO)   │   │   (RO)   │
└──────────┘   └──────────┘   └──────────┘

On master failure:
1. Sentinels detect master is unreachable (configurable timeout, 5–30 seconds)
2. Sentinels vote on failover (quorum = 2 of 3)
3. A replica is promoted to master
4. Other replicas reconfigure to follow new master
5. Clients are notified of the new master address
```

**Failover time:** 5–30 seconds depending on detection timeout configuration. During failover:
- Writes fail (no master to accept them).
- Reads from replicas continue to work (serving slightly stale data).

#### Docker Compose Setup

```yaml
version: "3.8"

services:
  redis-master:
    image: redis:7-alpine
    command: redis-server --appendonly yes --appendfsync everysec
    volumes:
      - redis-master-data:/data
    ports:
      - "6379:6379"

  redis-replica-1:
    image: redis:7-alpine
    command: redis-server --replicaof redis-master 6379 --appendonly yes
    depends_on:
      - redis-master

  redis-replica-2:
    image: redis:7-alpine
    command: redis-server --replicaof redis-master 6379 --appendonly yes
    depends_on:
      - redis-master

  sentinel-1:
    image: redis:7-alpine
    command: >
      redis-sentinel /etc/redis/sentinel.conf
    volumes:
      - ./sentinel.conf:/etc/redis/sentinel.conf
    depends_on:
      - redis-master

  sentinel-2:
    image: redis:7-alpine
    command: redis-sentinel /etc/redis/sentinel.conf
    volumes:
      - ./sentinel.conf:/etc/redis/sentinel.conf
    depends_on:
      - redis-master

  sentinel-3:
    image: redis:7-alpine
    command: redis-sentinel /etc/redis/sentinel.conf
    volumes:
      - ./sentinel.conf:/etc/redis/sentinel.conf
    depends_on:
      - redis-master

volumes:
  redis-master-data:
```

**sentinel.conf:**

```
sentinel monitor mymaster redis-master 6379 2
sentinel down-after-milliseconds mymaster 5000
sentinel failover-timeout mymaster 10000
sentinel parallel-syncs mymaster 1
```

This takes the setup from 1 Redis container to **6 containers** (1 master + 2 replicas + 3 sentinels). A significant operational jump — but necessary for production.

#### Node.js Client Configuration

The `redis` v4 client supports Sentinel natively:

```javascript
const client = createClient({
  sentinels: [
    { host: "sentinel-1", port: 26379 },
    { host: "sentinel-2", port: 26379 },
    { host: "sentinel-3", port: 26379 },
  ],
  name: "mymaster",  // the Sentinel group name
});
```

The client automatically discovers the current master and reconnects on failover.

---

### B.4 Redis Cluster: Why It Doesn't Help Geo (and a Workaround)

#### The Problem

Redis Cluster shards data by hash slot (`CRC16(key) mod 16384`). All geo operations (`GEOADD`, `GEOSEARCH`, etc.) operate on a **single key** (`stores:locations`).

This means **all geo data lives on one shard**. You cannot horizontally scale a single sorted set across cluster nodes. Redis Cluster helps with overall memory distribution if you have many other keys, but it does not help with geo query throughput.

```
Cluster Node A          Cluster Node B          Cluster Node C
[slots 0-5460]          [slots 5461-10922]      [slots 10923-16383]

stores:locations        (empty for geo)         (empty for geo)
  → ALL geo data
  → ALL geo queries
  → bottleneck
```

#### Workaround: Geographic Sharding

Split data into multiple geo keys by region:

```javascript
// Use Redis hash tags to control slot assignment
const geoKey = `stores:locations:{${getRegion(lat, lng)}}`;

// Regions based on geohash prefix (3 chars = ~156 km cells)
function getRegion(lat, lng) {
  return computeGeohash(lat, lng).substring(0, 3);  // e.g., "dr5", "9q8"
}
```

Each region's geo key lands on a different cluster shard:

```
Cluster Node A          Cluster Node B          Cluster Node C
stores:locations:{dr5}  stores:locations:{dr4}  stores:locations:{9q8}
  → NYC area              → Philadelphia area     → San Francisco area
```

**Routing logic:**

```javascript
async function findNearby(lat, lng, radiusMiles) {
  const primaryRegion = getRegion(lat, lng);
  const neighborRegions = getNeighborRegions(primaryRegion);

  // Query all potentially relevant shards in parallel
  const results = await Promise.all(
    [primaryRegion, ...neighborRegions].map(region =>
      redis.geoSearchWith(`stores:locations:{${region}}`, ...)
    )
  );

  return mergeAndSort(results.flat());
}
```

**Cost:** Significant application complexity. Cross-region queries (near a boundary) must query multiple shards and merge results. This is the same boundary problem as geohash prefix matching (see GEOHASH_API_NOTES.md section 4).

---

### B.5 Read Replicas

Geo workloads are overwhelmingly **read-heavy**. A store locator might process 10,000 "find nearby" queries per second but only 10 location updates per day.

#### Read/Write Splitting

```
                    ┌──────────────┐
                    │   Writes     │
                    │  (GEOADD)    │
                    └──────┬───────┘
                           ▼
                    ┌──────────────┐
                    │    Master    │
                    └───┬──────┬──┘
                        │      │    replication
                        ▼      ▼
                 ┌──────────┐ ┌──────────┐
                 │ Replica 1│ │ Replica 2│
                 └────┬─────┘ └────┬─────┘
                      │            │
              ┌───────┴───┐  ┌────┴────────┐
              │  Reads    │  │  Reads      │
              │ GEOSEARCH │  │ GEOPOS      │
              │ GEODIST   │  │ GEOHASH     │
              └───────────┘  └─────────────┘
```

For this project: all endpoints except the add endpoint in `index-hash.js` are read-only. Route **95%+** of traffic to replicas.

**Replication lag:** Typically < 1ms on the same network. A store added via GEOADD appears on replicas within milliseconds. For a store locator, this eventual consistency is acceptable — a 1-second delay before a new store appears in search results is fine.

---

### B.6 Cross-Region Replication

#### Active-Passive

```
US-East (Primary)              EU-West (Secondary)
┌──────────────┐               ┌──────────────┐
│    Master    │──────────────▶│   Replica    │
│   (R/W)     │  replication   │    (RO)      │
└──────────────┘  50-200ms     └──────────────┘
```

- Users in EU-West get slightly stale data (replication lag: 50–200ms cross-region).
- Writes always go to US-East.
- On US-East failure, manual promotion of EU-West replica to master.

For geo data, this works well: store locations change rarely, so the 200ms replication lag is invisible to users.

#### Active-Active (Redis Enterprise)

Redis Enterprise (commercial, not open-source) supports **Conflict-free Replicated Data Types (CRDTs)**. Both regions accept writes independently and merge without conflicts.

For geo data: the sorted set CRDT uses last-write-wins. If two regions simultaneously update the same store's coordinates, the later write wins. Since location updates are rare and non-concurrent, this is a non-issue.

**Cost:** Redis Enterprise licensing is significant. Consider only if you need multi-region write capability.

---

### B.7 Client-Side Failover

Even with Sentinel or replicas, there is a failover window (5–30 seconds) where the service cannot serve requests. Client-side strategies to handle this:

#### Circuit Breaker Pattern

```javascript
const CircuitBreaker = require("opossum");

const redisBreaker = new CircuitBreaker(redisGeoSearch, {
  timeout: 3000,      // fail fast after 3 seconds
  errorThresholdPercentage: 50,
  resetTimeout: 10000, // try again after 10 seconds
});

redisBreaker.fallback(() => {
  // Return cached results from local memory or CDN
  return localCache.getNearbyStores(lat, lng);
});
```

#### Local In-Memory Fallback

For a store locator with < 100K locations, load the full dataset into application memory at startup:

```javascript
let localStoreCache = [];

async function warmCache() {
  const members = await redis.zRange("stores:locations", 0, -1);
  localStoreCache = await Promise.all(
    members.map(async (id) => {
      const [pos] = await redis.geoPos("stores:locations", id);
      const meta = await redis.hGetAll(id);
      return { id, ...meta, lng: pos.longitude, lat: pos.latitude };
    })
  );
}

// Refresh every 5 minutes
setInterval(warmCache, 300000);
```

When Redis is down, compute distances in-memory using the Haversine formula. The result is the same — just slower (~10ms vs < 1ms). Stale data is better than no data.

#### Retry with Exponential Backoff

The current code has no retry logic. The `redis` v4 client supports automatic reconnection:

```javascript
const client = createClient({
  url: REDIS_URL,
  socket: {
    reconnectStrategy: (retries) => {
      if (retries > 10) return new Error("Max retries reached");
      return Math.min(retries * 100, 3000);  // 100ms, 200ms, ..., 3000ms
    },
  },
});
```

---

## Section C: Alternative Databases and Migration Patterns

### C.1 When to Move Away from Redis

The decision point: when you need **complex geo queries** (geo + text + filters in one query), **ACID transactions** on location data, or when **memory cost exceeds budget**.

Moving away does not mean removing Redis. The most common pattern is **Redis as a cache** in front of a primary database. The goal is to determine which database should be the **source of truth** and which handles the **hot read path**.

---

### C.2 PostgreSQL + PostGIS

**The strongest general-purpose alternative.** PostGIS adds spatial types and operators to PostgreSQL.

#### Equivalent Operations

| Redis | PostGIS | Notes |
|---|---|---|
| `GEOADD key lng lat member` | `INSERT INTO stores (id, location) VALUES (id, ST_MakePoint(lng, lat)::geography)` | Geography type handles Earth curvature |
| `GEOSEARCH ... BYRADIUS` | `SELECT * FROM stores WHERE ST_DWithin(location, point, radius)` | Uses GiST spatial index |
| `GEODIST` | `ST_Distance(a.location, b.location)` | Returns meters by default with geography type |
| `GEOPOS` | `SELECT ST_X(location::geometry), ST_Y(location::geometry)` | Direct coordinate extraction |
| `GEOHASH` | `SELECT ST_GeoHash(location::geometry, precision)` | Configurable precision (Redis is always 11) |

#### Schema

```sql
CREATE EXTENSION postgis;

CREATE TABLE stores (
  id    TEXT PRIMARY KEY,
  name  TEXT NOT NULL,
  location GEOGRAPHY(Point, 4326) NOT NULL
);

CREATE INDEX idx_stores_location ON stores USING GIST (location);

CREATE TABLE products (
  id       TEXT PRIMARY KEY,
  name     TEXT NOT NULL,
  category TEXT,
  price    NUMERIC(10,2)
);

CREATE TABLE inventory (
  store_id   TEXT REFERENCES stores(id),
  product_id TEXT REFERENCES products(id),
  stock_qty  INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (store_id, product_id)
);
```

#### The search-inventory Endpoint in One Query

```sql
SELECT
  p.id AS product_id,
  p.name AS product_name,
  p.category,
  p.price,
  s.id AS store_id,
  s.name AS store_name,
  i.stock_qty,
  ST_Distance(s.location, ST_MakePoint(-74.006, 40.7128)::geography) / 1609.34 AS distance_miles
FROM stores s
JOIN inventory i ON i.store_id = s.id
JOIN products p ON p.id = i.product_id
WHERE ST_DWithin(s.location, ST_MakePoint(-74.006, 40.7128)::geography, 16093.4)  -- 10 miles in meters
  AND i.stock_qty > 0
  AND p.name ILIKE '%puma%'
ORDER BY distance_miles;
```

One query. One round-trip. The query planner uses the spatial index for the DWithin filter, the B-tree for the JOIN, and the text index for the ILIKE. Compare this to the multi-step Redis implementation in `index.js:94-143`.

#### Tradeoffs vs Redis

| | Redis | PostGIS |
|---|---|---|
| **Radius query latency** | < 1 ms | 5–15 ms |
| **Complex queries** | Application code (N+1 calls) | Single SQL query |
| **Durability** | Needs AOF/RDB config | WAL by default (zero data loss) |
| **Storage cost per 1M locations** | ~530 MB RAM | ~500 MB disk + ~50 MB shared_buffers |
| **Scaling** | Read replicas | Read replicas, partitioning, Citus |
| **Operational overhead** | Low | Medium (VACUUM, connection pooling, backups) |

**When to choose PostGIS:** You need JOINs, ACID, complex filters, or your dataset exceeds what fits comfortably in RAM. This is most production location services.

---

### C.3 MongoDB

MongoDB has native geospatial support via the `2dsphere` index.

#### Equivalent Operations

| Redis | MongoDB | Notes |
|---|---|---|
| `GEOADD` | `insertOne({ location: { type: "Point", coordinates: [lng, lat] } })` | GeoJSON format |
| `GEOSEARCH BYRADIUS` | `find({ location: { $near: { $geometry: point, $maxDistance: meters } } })` | Requires 2dsphere index |
| `GEODIST` | Aggregation pipeline with `$geoNear` | No direct two-point distance command |
| `GEOHASH` | Not available natively | Must compute in application code |

#### Document Schema

```javascript
// stores collection
{
  _id: "store:1",
  name: "NYC Downtown",
  location: {
    type: "Point",
    coordinates: [-74.006, 40.7128]  // [longitude, latitude] — GeoJSON order
  },
  inventory: [
    { productId: "prod:1", productName: "Puma Running Shoe", stockQty: 15, price: 89.99 },
    { productId: "prod:2", productName: "Nike Air Max", stockQty: 8, price: 129.99 }
  ]
}
```

**The document model advantage:** Store metadata, inventory, and coordinates in a single document. The search-inventory query becomes:

```javascript
db.stores.aggregate([
  {
    $geoNear: {
      near: { type: "Point", coordinates: [-74.006, 40.7128] },
      distanceField: "distanceMeters",
      maxDistance: 16093.4,  // 10 miles in meters
      spherical: true
    }
  },
  { $unwind: "$inventory" },
  { $match: { "inventory.stockQty": { $gt: 0 }, "inventory.productName": /puma/i } },
  { $sort: { distanceMeters: 1 } }
]);
```

One pipeline, one round-trip — no multi-step application logic.

#### Tradeoffs vs Redis

| | Redis | MongoDB |
|---|---|---|
| **Radius query latency** | < 1 ms | 3–10 ms |
| **Complex queries** | Application code | Aggregation pipeline |
| **GEOHASH** | Native command | Not available |
| **Horizontal scaling** | Manual sharding (see B.4) | Native sharding on location field |
| **Document model** | Separate hashes (multiple lookups) | Embedded (single read) |

**When to choose MongoDB:** Your data model is document-oriented (stores with embedded inventory), you want native sharding, and you do not need SQL-level query power.

---

### C.4 Elasticsearch

Elasticsearch combines full-text search with geospatial queries. This is its unique advantage.

#### Equivalent Operations

| Redis | Elasticsearch | Notes |
|---|---|---|
| `GEOADD` | `index` with `geo_point` field | Automatic geohash indexing |
| `GEOSEARCH BYRADIUS` | `geo_distance` query | Combine with `bool` for multi-field filters |
| `GEODIST` | `sort` by `_geo_distance` | Distance computed at query time |
| `GEOHASH` | `geohash_grid` aggregation | Geohash-based bucketing for heatmaps |

#### Index Mapping

```json
{
  "mappings": {
    "properties": {
      "storeId": { "type": "keyword" },
      "storeName": { "type": "text" },
      "location": { "type": "geo_point" },
      "products": {
        "type": "nested",
        "properties": {
          "name": { "type": "text" },
          "category": { "type": "keyword" },
          "price": { "type": "float" },
          "stockQty": { "type": "integer" }
        }
      }
    }
  }
}
```

#### The Killer Feature: Geo + Full-Text in One Query

```json
{
  "query": {
    "bool": {
      "must": [
        { "nested": {
            "path": "products",
            "query": {
              "bool": {
                "must": [
                  { "match": { "products.name": "running shoes" } },
                  { "range": { "products.stockQty": { "gt": 0 } } },
                  { "range": { "products.price": { "lte": 100 } } }
                ]
              }
            }
          }
        }
      ],
      "filter": [
        { "geo_distance": { "distance": "10mi", "location": { "lat": 40.7128, "lon": -74.006 } } }
      ]
    }
  },
  "sort": [
    { "_geo_distance": { "location": { "lat": 40.7128, "lon": -74.006 }, "order": "asc", "unit": "mi" } }
  ]
}
```

"Find stores within 10 miles that have running shoes under $100 in stock, sorted by distance." One query. Full-text matching on product names (not just substring `ILIKE`), numeric range filters, geo radius — all in a single request.

#### Tradeoffs vs Redis

| | Redis | Elasticsearch |
|---|---|---|
| **Radius query latency** | < 1 ms | 10–50 ms |
| **Full-text + geo** | Not possible (without RediSearch) | Native strength |
| **Aggregations** | Not available | Geohash grids, distance rings, facets |
| **Real-time indexing** | Instant | Near-real-time (1-second refresh) |
| **Operational complexity** | Low | High (JVM tuning, shard management) |
| **As primary datastore** | Possible (with caveats) | Not recommended (no ACID, consistency issues) |

**When to choose Elasticsearch:** You need full-text search combined with geo queries (e-commerce product search, real-estate listings with descriptions). Avoid as a primary datastore — use it as a read-optimized search index fed from a primary DB.

---

### C.5 DynamoDB

DynamoDB has **no native geospatial support**. Geo queries are implemented via the `dynamodb-geo` library, which layers geohash-based indexing on top of DynamoDB's hash/range key structure.

#### How It Works

```
Table Design:
  Partition Key: geohash prefix (e.g., 4-char geohash)
  Sort Key:      full geohash + store ID

For a radius query:
1. Compute the geohash ranges that cover the search circle.
2. Issue parallel QUERY operations for each range (one per geohash prefix).
3. Post-filter results by exact Haversine distance.
```

This is conceptually identical to how Redis GEOSEARCH works internally, but implemented in application code rather than as a native command.

#### Tradeoffs vs Redis

| | Redis | DynamoDB (with geo library) |
|---|---|---|
| **Radius query latency** | < 1 ms | 20–50 ms (multiple parallel queries) |
| **Geo support** | Native commands | Application-level library |
| **Scaling** | Manual replicas | Fully managed, serverless |
| **GEODIST / GEOHASH** | Native commands | Computed in application code |
| **Multi-region** | Active-passive or Enterprise | Global Tables (active-active, fully managed) |
| **Cost model** | RAM-based (expensive at scale) | Pay-per-request (cheap at low volume) |

**When to choose DynamoDB:** You are already in the AWS ecosystem, need serverless scaling, and your geo queries are simple enough that the library overhead is acceptable. Not recommended if you need low-latency geo queries or advanced geospatial operations.

---

### C.6 Architecture Patterns for Migration

#### Pattern 1: Adapter (Interface Abstraction)

Define a `GeoService` interface and swap implementations:

```javascript
// geo-service.js — interface
class GeoService {
  async findNearby(lat, lng, radius, unit) { throw new Error("not implemented"); }
  async getDistance(storeA, storeB, unit) { throw new Error("not implemented"); }
  async getGeohash(storeId) { throw new Error("not implemented"); }
  async searchInventory(lat, lng, radius, productName) { throw new Error("not implemented"); }
}

// redis-geo-service.js — current implementation extracted
class RedisGeoService extends GeoService {
  async findNearby(lat, lng, radius, unit) {
    return redis.geoSearchWith("stores:locations", ...);
  }
}

// postgis-geo-service.js — alternative implementation
class PostGISGeoService extends GeoService {
  async findNearby(lat, lng, radius, unit) {
    return pool.query("SELECT ... FROM stores WHERE ST_DWithin(...)", [...]);
  }
}

// factory.js
function createGeoService() {
  if (process.env.GEO_BACKEND === "postgis") return new PostGISGeoService();
  return new RedisGeoService();  // default
}
```

The Express routes remain unchanged — they call the interface, not the implementation. This allows A/B testing, gradual migration, and easy fallback.

#### Pattern 2: Dual-Write (Redis as Cache)

Primary database is the source of truth. Redis is a read-through cache.

```
                         Write Path
                    ┌──────────────────┐
   GEOADD request → │  Primary DB      │
                    │  (Postgres/Mongo) │
                    └────────┬─────────┘
                             │
                             │  after commit
                             ▼
                    ┌──────────────────┐
                    │  Redis GEOADD    │
                    │  (cache update)  │
                    └──────────────────┘

                         Read Path
                    ┌──────────────────┐
   GEOSEARCH   →   │  Redis           │  ← fast path (< 1ms)
                    └──────────────────┘
                             │
                             │  if Redis is down
                             ▼
                    ┌──────────────────┐
                    │  Primary DB      │  ← fallback (5–15ms)
                    │  ST_DWithin      │
                    └──────────────────┘
```

**Consistency concern:** If the Redis write fails after the DB write, the cache is stale. Mitigations:

1. **Background sync job:** Periodically rebuild Redis from the DB (every 5 minutes). Brute-force but simple.
2. **Change Data Capture (CDC):** Use Debezium or Postgres logical replication to stream changes from the DB to a consumer that updates Redis. Near-real-time, more complex.
3. **Write-through wrapper:** Wrap both writes in a function that retries the Redis write. Accept that on Redis failure, the cache is stale until the next sync.

For store locations that change rarely (once per quarter for a new store opening), option 1 is sufficient.

#### Pattern 3: CQRS (Command Query Responsibility Segregation)

Separate the write model (primary DB with ACID) from the read model (Redis optimized for geo queries), synchronized via events.

```
┌────────────────────────────────────────────────────────────────┐
│                      Write Side (Commands)                     │
│                                                                │
│  API → Validate → Write to Primary DB → Publish Event          │
│                   (Postgres)             (Kafka/SQS/SNS)       │
└────────────────────────────────┬───────────────────────────────┘
                                 │
                                 │  LocationUpdated event
                                 ▼
┌────────────────────────────────────────────────────────────────┐
│                      Event Consumer                            │
│                                                                │
│  Consume Event → GEOADD to Redis → HSET metadata              │
└────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌────────────────────────────────────────────────────────────────┐
│                      Read Side (Queries)                       │
│                                                                │
│  API → GEOSEARCH Redis → HGETALL → Return to client           │
│        (< 1 ms)                                                │
└────────────────────────────────────────────────────────────────┘
```

**Advantages:**
- Write side gets full ACID guarantees, foreign keys, constraints, audit logs.
- Read side is optimized purely for geo query speed (sub-ms).
- Each side scales independently (add Redis replicas for reads, Postgres replicas for write throughput).

**Disadvantages:**
- Eventual consistency: the Redis read model lags behind writes by event propagation time (50ms–5s depending on the event bus).
- Infrastructure complexity: you now operate a primary DB, an event bus (Kafka/SQS), a consumer service, and Redis.
- Failure modes multiply: what if the consumer crashes? What if events are lost? You need dead-letter queues, replay capability, and monitoring.

**When to use CQRS:** The geo read path has significantly different performance requirements than the write path (thousands of reads per second vs dozens of writes per day), and you need strong durability guarantees on writes. This describes most store-locator and fleet-tracking systems at scale.

---

### C.7 Quick Reference Comparison Table

| Capability | Redis | PostGIS | MongoDB | Elasticsearch | DynamoDB |
|---|---|---|---|---|---|
| Radius query | `GEOSEARCH` | `ST_DWithin` | `$near` | `geo_distance` | Library |
| Distance calc | `GEODIST` | `ST_Distance` | Aggregation | Sort script | Library |
| Geohash | `GEOHASH` | `ST_GeoHash` | Manual | `geohash_grid` | Library |
| Full-text + geo | No (without RediSearch) | Limited (`ILIKE`) | Yes (`$text` + `$near`) | Yes (native) | No |
| Latency (p50) | < 1 ms | 5–15 ms | 3–10 ms | 10–50 ms | 20–50 ms |
| Max practical dataset | ~5M | ~100M+ | ~50M+ | ~100M+ | Unlimited |
| Complex queries (JOINs) | No | Yes (SQL) | Yes (pipeline) | Yes (DSL) | Limited |
| ACID transactions | No | Yes | Per-document | No | Per-item |
| Managed service | ElastiCache | RDS / Aurora | Atlas | Elastic Cloud | Native |
| Storage cost per 1M locations | ~530 MB RAM | ~500 MB disk | ~1 GB disk | ~2 GB disk | Pay-per-use |
| Horizontal geo scaling | Manual sharding | Citus / partitioning | Native sharding | Native sharding | Native |

---

### C.8 Recommended Migration Steps for This Project

If you decide to move beyond standalone Redis, here is the incremental path with minimal risk:

1. **Extract the adapter.** Move Redis calls from `index.js` routes into a `RedisGeoService` class. Routes call the service interface.

2. **Add a primary database.** Implement `PostGISGeoService` (or Mongo equivalent) behind the same interface. Deploy alongside Redis.

3. **Dual-write.** On location updates, write to both the primary DB and Redis. On reads, serve from Redis with primary DB as fallback.

4. **Add a background sync.** A cron job or CDC consumer that rebuilds Redis from the primary DB every N minutes. This catches any dual-write inconsistencies.

5. **Monitor.** Compare Redis results against primary DB results for the same queries. Log discrepancies. Measure latency of both paths.

6. **Evaluate.** If primary DB latency is acceptable for your SLA (typically < 15ms p99 for PostGIS with a spatial index), you can optionally remove Redis entirely and simplify your architecture. If sub-ms is required, keep Redis as the read cache permanently.

The end state depends on your latency requirements. Most store-locator services are perfectly fine at 10ms — the user does not notice the difference between 1ms and 10ms in a UI that takes 200ms to render. In that case, PostGIS as the sole datastore is the simpler, cheaper, and more maintainable choice.
