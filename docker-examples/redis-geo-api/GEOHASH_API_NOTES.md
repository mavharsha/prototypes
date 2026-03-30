# Geohash API: Implementation Deep Dive

This API (`index-hash.js`, port 3001) explores the Redis `GEOHASH` command and the geohash concept itself. While `index.js` uses radius-based queries (GEOSEARCH), this API demonstrates an alternative approach: **geohash prefix matching** for proximity detection, precision-level neighbor analysis, and the comparison between geohash-based proximity buckets and exact distances.

---

## 1. What Is a Geohash

### 1.1 The Core Idea

A geohash is a single string that encodes a 2D coordinate (latitude, longitude) into a 1D value while preserving spatial locality — **nearby points usually share prefixes**.

Invented by Gustavo Niemeyer in 2008 (public domain). The string uses base32 encoding with this alphabet:

```
0 1 2 3 4 5 6 7 8 9 b c d e f g h j k m n p q r s t u v w x y z
```

Note the missing letters: `a`, `i`, `l`, `o` are excluded to avoid confusion with digits.

Each additional character narrows the geographic area the hash represents. Truncating a geohash is equivalent to zooming out on a map.

### 1.2 How the Encoding Works (Step by Step)

To encode **NYC Downtown** (`longitude: -74.006, latitude: 40.7128`):

**Step 1 — Bisect longitude range:**

```
Range: [-180, 180]    →  midpoint = 0
-74.006 < 0           →  bit = 0, new range = [-180, 0]

Range: [-180, 0]      →  midpoint = -90
-74.006 > -90         →  bit = 1, new range = [-90, 0]

Range: [-90, 0]       →  midpoint = -45
-74.006 < -45         →  bit = 0, new range = [-90, -45]

Range: [-90, -45]     →  midpoint = -67.5
-74.006 < -67.5       →  bit = 0, new range = [-90, -67.5]

Range: [-90, -67.5]   →  midpoint = -78.75
-74.006 > -78.75      →  bit = 1, new range = [-78.75, -67.5]
...
```

**Step 2 — Bisect latitude range (interleaved with longitude):**

```
Range: [-90, 90]      →  midpoint = 0
40.7128 > 0           →  bit = 1, new range = [0, 90]

Range: [0, 90]        →  midpoint = 45
40.7128 < 45          →  bit = 0, new range = [0, 45]
...
```

**Step 3 — Interleave bits (lon, lat, lon, lat, ...):**

```
lon:  0 1 0 0 1 ...
lat:  1 0 ...
      ─────────────
bits: 0 1 1 0 0 0 1 0 ...
```

**Step 4 — Group into 5-bit chunks and map to base32:**

```
01100 → d
10010 → r
01011 → 5
10100 → r
...
```

Result for NYC Downtown: `dr5ru7c99hd` (11 characters from Redis GEOHASH).

### 1.3 Precision Levels and Cell Sizes

| Geohash Length | Cell Width | Cell Height | Approximate Area | Example Use |
|---|---|---|---|---|
| 1 | 5,000 km | 5,000 km | 25,000,000 km² | Continent |
| 2 | 1,250 km | 625 km | 781,250 km² | Large country region |
| 3 | 156 km | 156 km | 24,336 km² | State / province |
| 4 | 39.1 km | 19.5 km | 762 km² | Metro area |
| 5 | 4.9 km | 4.9 km | 24 km² | Neighborhood |
| 6 | 1.2 km | 610 m | 0.73 km² | City block cluster |
| 7 | 153 m | 153 m | 0.023 km² | Street segment |
| 8 | 38.2 m | 19.1 m | 730 m² | Building |
| 9 | 4.8 m | 4.8 m | 23 m² | Room |
| 10 | 1.2 m | 0.6 m | 0.72 m² | Person |
| 11 | 15 cm | 15 cm | 0.023 m² | Survey point |

The `PRECISION_LABELS` array in `index-hash.js:112-122` maps levels 1–8 to human-readable cell sizes. These are referenced by the `/stores/geohash/neighbors` endpoint.

### 1.4 Why Prefix Matching Works for Proximity

Two geohashes that share a longer prefix are guaranteed to be in the same cell at that precision level:

```
store:1 (NYC Downtown):    dr5ru7c99hd
store:3 (Jersey City):     dr5r7p5et36

Shared prefix: "dr5r" (4 characters → ~39 km cell)
Both are in the same metro-area cell.

store:8 (Philadelphia):    dr4e3e1h6j0

Shared prefix: "dr" (2 characters → ~1,250 km cell)
Same region, but not the same metro area.
```

This is the principle behind the `/stores/geohash/prefix` endpoint: find all members whose geohash starts with a given string. No radius computation needed — just string comparison.

---

## 2. Redis GEOHASH Internals

### 2.1 Internal 52-bit vs External 11-Character

Redis stores geo coordinates in two representations:

```
Internal (ZSCORE):   52-bit binary geohash → stored as sorted set score (double float)
External (GEOHASH):  11-character base32 string → returned by the GEOHASH command
```

The conversion:
1. Redis has a 52-bit binary geohash (26 bits lon + 26 bits lat, interleaved).
2. 11 base32 characters require 55 bits (11 × 5 = 55).
3. Redis pads the 52-bit value with **3 zero bits** at the least significant end to fill 55 bits.
4. Groups into 5-bit chunks, maps each to a base32 character.

### 2.2 Why 52 Bits?

IEEE 754 double-precision floats have a 52-bit mantissa. By using exactly 52 bits for the geohash, Redis stores it as the sorted set score **without precision loss**. The geohash IS the score — no separate storage, no conversion error.

This is a core Redis design philosophy: reuse existing structures rather than creating special types.

### 2.3 Relationship Between GEOHASH, GEOPOS, and ZSCORE

All three commands read the **same underlying data** — the sorted set score. They differ only in output format:

```
                    ┌─────────────────────────┐
                    │  Sorted Set Score       │
                    │  (52-bit geohash as     │
                    │   double float)         │
                    └────────┬────────────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
              ▼              ▼              ▼
         ┌─────────┐  ┌──────────┐  ┌────────────┐
         │ ZSCORE   │  │ GEOPOS   │  │ GEOHASH    │
         │          │  │          │  │            │
         │ Raw float│  │ Decode → │  │ Decode →   │
         │ value    │  │ (lng,lat)│  │ base32 str │
         └─────────┘  └──────────┘  └────────────┘
```

This means:
- `ZSCORE store:1` → `1791875796750882` (raw geohash as number)
- `GEOPOS store:1` → `(-74.00600105, 40.71279904)` (decoded coordinates)
- `GEOHASH store:1` → `"dr5ru7c99hd"` (decoded + re-encoded as base32 string)

---

## 3. Endpoint-by-Endpoint Analysis

### 3.1 GET /stores/geohash?ids=store:1,store:2

**What it does:** Returns the 11-character geohash string for specified stores.

**Redis call pattern:**

```
1 × GEOHASH (bulk — all IDs in one call)
N × HGETALL (one per store for metadata)
────────────
Total: N + 1 calls
```

The GEOHASH call is efficient (bulk form), but the HGETALL calls are sequential. A pipeline would reduce N + 1 round-trips to 1.

### 3.2 GET /stores/geohash/all

**What it does:** Returns geohashes for every store in the sorted set.

**Redis call pattern:**

```
1 × ZRANGE    (get all member IDs)
1 × GEOHASH   (bulk for all members)
N × HGETALL   (one per store)
────────────
Total: N + 2 calls for N stores
```

For 8 stores: 10 Redis commands. At scale, this endpoint would need pagination — returning 1M geohashes in a single response is impractical.

### 3.3 GET /stores/geohash/prefix?prefix=dr5r

**What it does:** Finds all stores whose geohash starts with the given prefix.

**Redis call pattern:**

```
1 × ZRANGE    (get ALL member IDs)
1 × GEOHASH   (bulk for ALL members)
M × HGETALL   (one per matching store)
────────────
Where M = matching stores, but ZRANGE + GEOHASH scan ALL N stores
```

**This is a full table scan on every request.** Time complexity: O(N) where N = total stores, regardless of how many match.

**Why it is done this way:** Redis has no native "find members by geohash prefix" command. GEOSEARCH works by radius/box, not by prefix. There is no `GEOSEARCHBYPREFIX`.

**A better approach:** Use `ZRANGEBYSCORE` with the score range corresponding to the geohash prefix. Each geohash prefix maps to a contiguous range of 52-bit scores:

```javascript
// Convert prefix "dr5r" to a score range
// All geohashes starting with "dr5r" have scores between [minScore, maxScore]
// This would be O(log N + M) instead of O(N)
```

This requires computing the score boundaries from the base32 prefix — not trivial, but it is exactly what GEOSEARCH does internally for its bounding box computation.

### 3.4 GET /stores/geohash/neighbors?id=store:1

**What it does:** For a given store, shows which other stores share geohash prefixes at each precision level (1 through 8).

**Sample output for NYC Downtown:**

```
Precision 1 ("d", ~5000 km):     All 8 stores — they are all in eastern North America
Precision 2 ("dr", ~1250 km):    All 8 stores — they are all in the northeast US
Precision 3 ("dr5", ~156 km):    7 stores — Philadelphia might share "dr4" instead
Precision 4 ("dr5r", ~39 km):    4-5 stores — NYC metro core only
Precision 5 ("dr5ru", ~4.9 km):  1-2 stores — only very close ones (Brooklyn Heights)
Precision 6+ (~1.2 km or less):  Likely none — the seeded stores are not that close
```

This demonstrates the granularity of geohash precision in a tangible way. Each level drops stores that are outside the cell boundary at that precision.

**Redis call pattern:**

```
1 × GEOHASH  (target store)
1 × ZRANGE   (all members)
1 × GEOHASH  (all members, bulk)
1 × HGETALL  (target store metadata)
────────────
Total: 4 calls (efficient — no N+1 problem here)
```

The prefix matching (8 precision levels × N members) is done entirely in application code with `startsWith()`. For 8 stores, this is 64 string comparisons — negligible. For 1M stores, it is 8M comparisons, which is still fast in JavaScript (~50ms) but could be optimized with the ZRANGEBYSCORE approach.

### 3.5 POST /stores/geohash/compare

**What it does:** Compares two stores' geohashes to find their shared prefix length and maps it to an approximate proximity bucket. Also fetches exact GEODIST for comparison.

**The key insight this endpoint demonstrates:** Geohash proximity is a **coarse bucket**, not a precise measurement.

```
store:1 (NYC Downtown) vs store:3 (Jersey City):
  Geohash A: dr5ru7c99hd
  Geohash B: dr5r7p5et36
  Shared prefix: "dr5r" (4 chars) → "~39 km" proximity bucket
  Exact GEODIST: ~2.1 miles

  The bucket says "within 39 km" but they are actually 3.4 km apart.
  The bucket is correct but imprecise.
```

```
store:1 (NYC Downtown) vs store:8 (Philadelphia):
  Shared prefix: "dr" (2 chars) → "~1250 km" proximity bucket
  Exact GEODIST: ~80 miles

  The bucket says "within 1250 km" — a 15x over-estimate of actual distance.
```

**Redis call pattern:**

```
1 × GEOHASH  (both stores, bulk)
1 × GEODIST  (exact distance)
2 × HGETALL  (metadata for both)
────────────
Total: 4 calls
```

### 3.6 POST /stores/geohash/add

**What it does:** Adds a new location via GEOADD, stores metadata via HSET, retrieves and returns the GEOHASH.

**Redis call pattern:**

```
1 × GEOADD   (add to sorted set)
1 × HSET     (store metadata)
1 × GEOHASH  (retrieve the generated geohash)
────────────
Total: 3 calls
```

These three commands should be pipelined or wrapped in `MULTI/EXEC` for atomicity. Currently, if HSET fails after GEOADD succeeds, you have a geo entry with no metadata.

Does not validate for duplicate IDs — GEOADD silently overwrites existing coordinates.

---

## 4. The Geohash Boundary Problem

### 4.1 What It Is

Two points can be physically adjacent (e.g., 10 meters apart) but have **completely different geohash prefixes** if they straddle a cell boundary.

```
         Cell Boundary
              │
    Cell A    │    Cell B
   "dr5ru"    │   "dr5rv"
              │
        X ····│···· Y
       10m apart
              │

X and Y share only "dr5r" (4 chars, ~39 km bucket)
despite being 10 meters apart.
```

This happens because geohash cells are a grid overlay. Points near the edges of cells are close to points in adjacent cells, but their geohashes diverge at the character where the cell boundary falls.

### 4.2 Why It Matters for This API

- **`/stores/geohash/prefix`** would miss nearby stores that fall in an adjacent cell. Searching for prefix `"dr5ru"` would NOT return a store 10 meters away in cell `"dr5rv"`.
- **`/stores/geohash/neighbors`** shows misleading precision labels when stores straddle a boundary — two nearby stores appear to be "~39 km apart" when they are actually meters apart.
- **`/stores/geohash/compare`** could show a short shared prefix for stores that are actually very close, as shown in section 3.5.

### 4.3 The Standard Mitigation

When doing geohash-based proximity lookups, also check the **8 neighboring cells** at the same precision level:

```
┌─────┬─────┬─────┐
│ NW  │  N  │ NE  │
├─────┼─────┼─────┤
│  W  │  X  │  E  │   Query all 9 cells (self + 8 neighbors)
├─────┼─────┼─────┤
│ SW  │  S  │ SE  │
└─────┴─────┴─────┘
```

This is exactly what `GEOSEARCH` does internally — it computes multiple geohash ranges that cover the search area, not just one cell. The boundary problem is why GEOSEARCH exists as a separate command rather than relying on simple prefix matching.

### 4.4 Implication for Choosing Geohash vs Radius

| Criterion | Geohash Prefix | GEOSEARCH (Radius) |
|---|---|---|
| Boundary correctness | Has blind spots at edges | Handles correctly |
| Speed | String comparison (very fast) | Score range + Haversine (fast) |
| Precision | Coarse buckets only | Exact distance |
| Simplicity | Trivial to implement | Requires Redis GEO commands |
| Portability | Works with any datastore that supports strings | Redis-specific |

---

## 5. When to Use Geohash-Based Lookups vs Radius Queries

### 5.1 Geohash Prefix Matching Is Better For

- **Sharding:** Partition data by geohash prefix so each shard handles a geographic region. Example: all stores with geohash starting "dr5" go to shard A, "9q8" to shard B.
- **Caching:** Use geohash prefix as a cache key. All "find nearby" queries within the same cell return the same cached result. Truncate to 4–5 characters for reasonable granularity.
- **Approximate grouping:** "Which stores are roughly in the same metro area?" Precision 4 (39 km) is a good fit.
- **External system integration:** Geohash strings are portable, URL-safe, and human-readable. Pass them between microservices without coupling to Redis.
- **Database-agnostic proximity:** If you migrate from Redis to Postgres, geohash strings still work. You cannot port GEOSEARCH commands.

### 5.2 Radius Queries Are Better For

- **User-facing proximity search:** "Find stores within 5 miles." Users expect precise, circular results.
- **Distance calculations:** Exact miles/km between two points.
- **Anything where correctness matters more than speed.** The boundary problem means geohash prefix matching can miss nearby results — unacceptable for a store locator.

### 5.3 Combining Both (Recommended Production Pattern)

Use geohash prefix (4–5 chars) as a **first-level filter**, then GEOSEARCH within that subset:

```
User location → compute geohash prefix (5 chars, ~4.9 km cell)
  → look up cache/shard for that prefix
    → GEOSEARCH within the shard for exact radius results
```

This is a two-level index: geohash narrows the candidate set cheaply, radius refines it precisely. The geohash layer can be a CDN cache, a database partition key, or a Redis Cluster hash tag.

---

## 6. Production Considerations

### 6.1 Scaling the Prefix Search

The current `/stores/geohash/prefix` endpoint scans all N members on every request. Options for improvement:

1. **ZRANGEBYSCORE with computed score ranges:** Convert the geohash prefix to a score range and query the sorted set directly. O(log N + M) instead of O(N).
2. **Secondary index:** Maintain a SET per geohash prefix (e.g., `geohash:dr5r → {store:1, store:3, store:4}`). Update on GEOADD. Lookup is O(1).
3. **Geohash as Redis Cluster hash tag:** `stores:locations:{dr5r}` — all stores in the same ~39 km cell land on the same shard. Query only that shard.

### 6.2 Geohash Stability for Moving Objects

A store's geohash changes if its coordinates are updated (GEOADD with new values). Implications:

- **Static data (store locations):** Geohash is stable. Use freely for sharding, caching, external references.
- **Moving objects (delivery drivers, fleet tracking):** Geohash changes on every position update. If you shard or cache by geohash, every update potentially requires re-sharding or cache invalidation. This makes geohash-based architectures expensive for high-velocity location streams.

For moving objects, prefer radius queries (GEOSEARCH) over geohash-based architectures. Or use a coarse geohash (2–3 chars, ~156–1250 km cells) that changes infrequently even with movement.

### 6.3 Privacy Implications

An 11-character geohash pinpoints a location to ~15 cm. Exposing full geohashes of user locations is a privacy risk.

**Truncation by purpose:**

| Purpose | Characters | Resolution | Privacy |
|---|---|---|---|
| Analytics / heatmaps | 3–4 | ~39–156 km | Safe |
| Approximate grouping | 5 | ~4.9 km | Moderate |
| Delivery zone | 6 | ~1.2 km | Low risk |
| Exact location | 7+ | < 153 m | High risk |

Truncate geohashes before exposing them in APIs, logs, or analytics pipelines. Never store full-precision geohashes of user locations in systems accessible to broad audiences.

### 6.4 Missing Features in This Implementation

- **No neighboring cell lookups:** The boundary problem (section 4) is not mitigated. The prefix endpoint can miss nearby stores across cell boundaries.
- **No pagination** on `/geohash/all` — unusable at scale.
- **No input validation** on prefix length or precision levels.
- **Neighbors endpoint covers levels 1–8** but 11-character geohashes support up to level 11. Levels 9–11 are omitted (sub-meter precision, unlikely to match any stores).
- **No MULTI/EXEC** on the add endpoint — partial failures leave inconsistent state.
