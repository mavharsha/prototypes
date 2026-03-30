# H3 Hexagonal Spatial Index: Implementation Deep Dive & Comparison with Redis Geospatial

This document covers Uber's H3 hexagonal hierarchical spatial index, how it integrates with Redis in `index-h3.js`, and a thorough comparison with Redis's native geospatial commands (GEOSEARCH/GEODIST/GEOHASH) used in `index.js` and `index-hash.js`. Written from a senior distributed systems architect's perspective.

---

## 1. What Is H3

### 1.1 The Core Idea

H3 is a **hexagonal hierarchical spatial index** developed by Uber in 2018. It divides the Earth's surface into hexagonal cells at 16 resolution levels (0–15). Each cell has a unique 64-bit integer identifier.

The key insight: **hexagons are the optimal shape for spatial indexing**.

```
Rectangles (Geohash)                 Hexagons (H3)
┌─────┬─────┬─────┐                ╱╲   ╱╲   ╱╲
│     │     │     │               ╱  ╲ ╱  ╲ ╱  ╲
│  A  │  B  │  C  │              │    │    │    │
│     │     │     │              │ A  │ B  │ C  │
├─────┼─────┼─────┤               ╲  ╱ ╲  ╱ ╲  ╱
│     │     │     │                ╲╱   ╲╱   ╲╱
│  D  │  E  │  F  │              ╱╲   ╱╲   ╱╲
│     │     │     │             ╱  ╲ ╱  ╲ ╱  ╲
└─────┴─────┴─────┘            │ D  │ E  │ F  │
                                ╲  ╱ ╲  ╱ ╲  ╱
Distances to neighbors:          ╲╱   ╲╱   ╲╱
- Cardinal (N,S,E,W): d
- Diagonal (NE,NW,SE,SW): d×√2   All 6 neighbors: same distance d
  (~41% farther)
```

Rectangles have 8 neighbors at two different distances. Hexagons have **6 neighbors, all equidistant**. This eliminates orientation bias — every direction is treated equally.

### 1.2 Icosahedral Projection

H3 projects Earth's surface onto an **icosahedron** (20-sided polyhedron), then tiles each face with hexagons. This avoids the Mercator distortion that plagues geohash (cells near the poles become extremely tall and narrow).

The base grid (resolution 0) has **122 cells**: 110 hexagons + 12 pentagons. The pentagons sit at the 12 vertices of the icosahedron — they are a mathematical necessity (you cannot tile a sphere purely with hexagons, per Euler's formula).

Pentagon cells are rare (12 per resolution out of millions/billions of hexagons) but they do exist. `gridDisk` and `gridRing` behave differently near pentagons — production code should handle this gracefully.

### 1.3 Resolution Levels

Each finer resolution has approximately **7× more cells** than the previous level (aperture-7 subdivision).

| Resolution | Total Cells | Avg Hex Area | Avg Edge Length | Approximate Use |
|---|---|---|---|---|
| 0 | 122 | 4,357,449 km² | 1,108 km | Continent |
| 1 | 842 | 609,788 km² | 419 km | Subcontinent |
| 2 | 5,882 | 86,802 km² | 158 km | Large state |
| 3 | 41,162 | 12,393 km² | 59 km | State / province |
| 4 | 288,122 | 1,770 km² | 22 km | Metro area |
| **5** | **2,016,842** | **253 km²** | **8.5 km** | **City district** |
| 6 | 14,117,882 | 36 km² | 3.2 km | Large neighborhood |
| **7** | **98,825,162** | **5.2 km²** | **1.2 km** | **Neighborhood** |
| 8 | 691,776,122 | 0.74 km² | 461 m | City block |
| **9** | **4,842,432,842** | **0.11 km²** | **174 m** | **Building cluster** |
| 10 | 33,897,029,882 | 0.015 km² | 65 m | Parking lot |
| 11 | 237,279,209,162 | 0.002 km² | 25 m | Single structure |
| 12–15 | Exponentially finer | < 400 m² | < 10 m | Sub-meter precision |

This project seeds H3 indexes at **resolutions 4, 5, 7, and 9** (bolded above), covering metro through block-level granularity.

### 1.4 The H3 Index: A 64-bit Integer

Each H3 cell index encodes:

```
Bits 63–59:  Mode (1 = H3 cell index)
Bits 58–56:  Reserved
Bits 55–52:  Resolution (0–15)
Bits 51–45:  Base cell number (0–121)
Bits 44–0:   Hierarchy path (3 bits per resolution level, encoding which child cell)
```

Example: NYC Downtown (40.7128, -74.006) at resolution 7:

```javascript
const h3 = require("h3-js");
const cell = h3.latLngToCell(40.7128, -74.006, 7);
// → "872a1072dffffff"  (hex string representation of the 64-bit integer)
```

The string representation is a base-16 encoding of the 64-bit integer. Unlike geohash (which uses a custom base32 alphabet), H3 uses standard hex digits.

### 1.5 Aperture-7 Hierarchy

Each hexagon at resolution N contains approximately 7 child hexagons at resolution N+1.

```
Resolution 4 (parent)          Resolution 5 (children)
                               ┌───────────────────────┐
   ╱╲   ╱╲   ╱╲              │   ╱╲  ╱╲  ╱╲  ╱╲     │
  ╱  ╲ ╱  ╲ ╱  ╲             │  ╱ 1╲╱ 2╲╱ 3╲╱  ╲    │
 │    │ P  │    │             │ │  4 │ C │ 5 │    │   │
  ╲  ╱ ╲  ╱ ╲  ╱             │  ╲ 6╱╲ 7╱╲  ╱╲  ╱    │
   ╲╱   ╲╱   ╲╱              │   ╲╱  ╲╱  ╲╱  ╲╱     │
                               └───────────────────────┘
                               C = center child (same center as parent)
                               1-6 = surrounding children
```

**Important caveat:** The 7 children are not geometrically contained within the parent. Due to the rotational offset in aperture-7 subdivision, some children's edges extend beyond the parent's boundary. This means `cellToParent` followed by `cellToChildren` is not a perfect containment relationship — it is a **logical hierarchy**, not a geometric one.

Compare with geohash where children are always perfectly contained within the parent rectangle. H3 trades geometric containment for uniform cell shape.

---

## 2. H3 + Redis Integration Pattern

### 2.1 Architecture: H3 Is Application-Level, Redis Is Storage

Unlike Redis's native `GEOSEARCH` (which is a server-side command), H3 computations happen **entirely in the application layer** via the `h3-js` library. Redis provides fast storage and set operations; h3-js provides the spatial math.

```
┌────────────────────────────────────────────────┐
│  Application Layer (Node.js + h3-js)           │
│                                                │
│  latLngToCell()  gridDisk()  gridDistance()     │
│       │              │             │            │
│       │  H3 cell     │  Cell list  │            │
│       │  indexes     │             │            │
│       ▼              ▼             ▼            │
│  ┌────────────────────────────────────────┐     │
│  │  Redis Calls                          │     │
│  │  SADD / SMEMBERS / HSET / HGETALL     │     │
│  └────────────────────────────────────────┘     │
└─────────────────────┬──────────────────────────┘
                      │
                      ▼
              ┌──────────────┐
              │    Redis     │
              │              │
              │  SETs:  h3:cell:res7:{index} → {store:1, store:3}  │
              │  HASHes: store:1 → {name, lat, lng, h3Res4, ...}   │
              │  ZSET:  stores:locations → geo sorted set           │
              └──────────────┘
```

### 2.2 The SET-per-Cell Pattern

The key data structure in `index-h3.js` is a **Redis SET for each H3 cell** containing the IDs of stores within that cell:

```
h3:cell:res7:872a1072dffffff → { "store:1", "store:3" }
h3:cell:res7:872a10729ffffff → { "store:2" }
h3:cell:res5:852a1073fffffff → { "store:1", "store:2", "store:3", "store:4" }
```

**Why this pattern is fast for proximity queries:**

A `gridDisk(center, k=2)` at resolution 7 returns 19 cells. The nearby query becomes:

```javascript
// 1. Compute which cells to search (in-memory, ~0.01ms)
const diskCells = h3.gridDisk(centerCell, 2);  // → 19 cell indexes

// 2. Look up stores in each cell (19 SMEMBERS calls, pipelineable)
const storeIds = await Promise.all(
  diskCells.map(cell => redis.sMembers(`h3:cell:res7:${cell}`))
);
```

Compare with the `index.js` approach:

| Step | Redis GEO (index.js) | H3 + SETs (index-h3.js) |
|---|---|---|
| Spatial query | `GEOSEARCH` (single command) | `gridDisk` (in-memory) + `SMEMBERS` × k cells |
| Redis calls | 1 | k (19 for ring=2) |
| Server-side computation | Bounding box + Haversine post-filter | None (Redis just returns SET members) |
| Result ordering | Built-in (`ASC`) | Application-level sort by `greatCircleDistance` |

The Redis GEO approach is faster for simple radius queries (1 command vs 19). The H3 approach is faster for **repeated queries in the same area** (the cell list is cacheable) and **better for sharding** (each cell is a natural partition key).

### 2.3 Write Amplification

Each store insert requires writes to **multiple Redis SETs** (one per resolution):

```javascript
// seed.js: for each store, at 4 resolutions
for (const res of [4, 5, 7, 9]) {
  const cellIndex = h3.latLngToCell(store.lat, store.lng, res);
  await client.sAdd(`h3:cell:res${res}:${cellIndex}`, store.id);
}
```

| | Redis GEO | H3 + Redis SETs (4 resolutions) |
|---|---|---|
| Write commands per store | 1 (GEOADD) + 1 (HSET) | 4 (SADD) + 1 (HSET) + 1 (GEOADD) |
| Write amplification | 1× | 3× |

Mitigation: **only index at resolutions you actually query**. If you only need neighborhood-level proximity (res 7), skip resolutions 4, 5, 9 and cut writes by 75%.

### 2.4 Memory Analysis

Each Redis SET entry costs ~80 bytes (member string + SET overhead). For the reverse-lookup SETs:

| Scale | Resolutions | SET Entries | Additional Memory |
|---|---|---|---|
| 8 stores | 4 | 32 | ~2.5 KB |
| 10K stores | 4 | 40K | ~3.2 MB |
| 1M stores | 4 | 4M | ~320 MB |
| 1M stores | 1 (res 7 only) | 1M | ~80 MB |

This is **on top of** the geo sorted set and metadata hashes. At 1M stores with 4 resolutions, H3 indexing adds ~320 MB of RAM. With a single resolution, ~80 MB.

---

## 3. Endpoint-by-Endpoint Analysis

### 3.1 GET /stores/h3

**What it does:** Lists all stores with their H3 indexes at resolutions 4, 5, 7, 9.

**Redis calls:** 1 × ZRANGE + N × HGETALL = N+1 calls.

Same N+1 pattern as the other APIs. The H3 indexes are read from the hash fields (`h3Res4`, `h3Res5`, etc.) — no h3-js computation needed at read time.

### 3.2 GET /stores/h3/cell?lat=40.7128&lng=-74.006&resolution=7

**What it does:** Computes the H3 cell for a coordinate, returns all stores in that cell plus cell metadata (center, boundary polygon, area).

**Flow:**

```
1. h3.latLngToCell(lat, lng, res)     → cell index          (in-memory, ~0.01ms)
2. h3.cellToLatLng(cell)              → center coordinate   (in-memory)
3. h3.cellToBoundary(cell)            → polygon vertices    (in-memory)
4. h3.cellArea(cell, "km2")           → area                (in-memory)
5. redis.sMembers(h3:cell:res7:...)   → store IDs           (1 Redis call)
6. redis.hGetAll(storeId) × M         → metadata            (M Redis calls)
```

**Redis calls:** 1 + M (where M = stores in the cell). The h3-js computations are purely in-memory math — sub-millisecond.

**Why this endpoint is unique to H3:** Neither geohash nor Redis GEO can return a cell's **polygon boundary**. H3 cells have a well-defined hexagonal shape that can be rendered on a map. Geohash cells are rectangles with no native boundary API.

### 3.3 GET /stores/h3/nearby?lat=40.7128&lng=-74.006&resolution=7&ring=2

**What it does:** The core proximity query. Uses `gridDisk` to find all H3 cells within `k` hex rings of the query point, then looks up stores in each cell.

**How gridDisk works:**

```
ring=0: 1 cell    (self only)
ring=1: 7 cells   (self + 6 neighbors)
ring=2: 19 cells  (self + 6 + 12)
ring=3: 37 cells  (self + 6 + 12 + 18)

Formula: 3k² + 3k + 1 cells

     ring=2 at resolution 7 (~5.2 km² per hex):
     ┌─────────────────────────────────────┐
     │       ╱╲   ╱╲   ╱╲   ╱╲           │
     │      ╱  ╲ ╱  ╲ ╱  ╲ ╱  ╲          │
     │     │ r2 │ r2 │ r2 │ r2 │          │
     │      ╲  ╱ ╲  ╱ ╲  ╱ ╲  ╱           │
     │  ╱╲   ╲╱   ╲╱   ╲╱   ╲╱   ╱╲      │
     │ ╱  ╲ ╱╲   ╱╲   ╱╲   ╱╲  ╱  ╲     │
     ││ r2 │ r1 │ r1 │ r1 │ r2 │         │
     │ ╲  ╱ ╲  ╱ ╲  ╱ ╲  ╱ ╲  ╱          │
     │  ╲╱   ╲╱   ╲╱   ╲╱   ╲╱           │
     │ ╱╲   ╱╲   ╱╲   ╱╲   ╱╲            │
     │╱  ╲ ╱ r1╲╱ •  ╲╱r1 ╲╱ r2╲          │
     │ r2 │     │  ☆  │    │     │         │
     │ ╲  ╱ ╲r1╱╲    ╱╲r1 ╱╲ r2╱          │
     │  ╲╱   ╲╱  ╲  ╱  ╲╱  ╲╱            │
     │       ╱╲   ╲╱   ╱╲                 │
     │      ╱r2 ╲ ╱r2╲ ╱r2╲               │
     └─────────────────────────────────────┘
     ☆ = query center    r1 = ring 1    r2 = ring 2
```

**Redis calls:** `diskCells.length` × SMEMBERS + M × HGETALL (where M = total unique stores found).

At resolution 7 with ring=2: 19 SMEMBERS + M HGETALL calls. These SMEMBERS calls are independent and can be pipelined into a single round-trip.

**Approximate search radius by resolution and ring size:**

| Resolution | Ring 1 (~2.4 km edge) | Ring 2 (~4.8 km) | Ring 3 (~7.2 km) |
|---|---|---|---|
| 5 (8.5 km edge) | ~17 km | ~34 km | ~51 km |
| 7 (1.2 km edge) | ~2.4 km | ~4.8 km | ~7.2 km |
| 9 (174 m edge) | ~350 m | ~700 m | ~1 km |

For this project's NYC-area stores: resolution 7 with ring=2 covers ~4.8 km radius — enough to find stores within a few miles.

### 3.4 GET /stores/h3/distance?from=store:1&to=store:3

**What it does:** Returns both the **grid distance** (number of hex steps between cells) and the **great circle distance** (Haversine, in miles).

Grid distance is unique to H3: it counts how many hexagonal cells you must traverse to get from one cell to another. This is an integer metric that is useful for:
- Approximate distance bucketing ("within 3 hex steps")
- Network/graph traversal cost estimation
- Surge pricing zone calculations

**Caveat:** `gridDistance` throws an error if the two cells are in different base cells (too far apart for the algorithm). The code catches this and returns `null`.

### 3.5 GET /stores/h3/neighbors?id=store:1

**What it does:** For each seeded resolution (4, 5, 7, 9), shows which other stores share the same H3 cell. This is the H3 equivalent of the geohash neighbors endpoint.

**How it differs from the geohash version:**

The geohash neighbors endpoint (`index-hash.js`) iterates precision levels 1–8 and checks prefix sharing. It suffers from the **boundary problem** — two nearby stores can have completely different prefixes.

The H3 neighbors endpoint checks actual cell membership via Redis SETs. Stores are either in the same cell or not — there is no prefix ambiguity. However, the **aperture-7 boundary issue** still exists: two nearby stores in adjacent cells at resolution 7 will not appear as neighbors at resolution 7, only at a coarser resolution where they share a cell.

This is why the endpoint shows **multiple resolutions**: stores that are not neighbors at res 7 might be neighbors at res 5.

### 3.6 POST /stores/h3/compare

**What it does:** Compares two stores across all seeded resolutions. For each resolution, shows whether they share a cell and their grid distance. Also provides exact great circle distance.

**The key insight this endpoint reveals:**

```
store:1 (NYC Downtown) vs store:3 (Jersey City):
  Resolution 4 (~1,770 km²): same cell     → in the same metro area
  Resolution 5 (~253 km²):   same cell     → in the same district
  Resolution 7 (~5.2 km²):   different cell → in different neighborhoods
  Resolution 9 (~0.11 km²):  different cell → in different blocks
  Great circle: ~2.1 miles

  Finest shared resolution: 5 (district-level)
```

```
store:1 (NYC Downtown) vs store:8 (Philadelphia):
  Resolution 4 (~1,770 km²): different cell → different metro areas
  Resolution 5+: all different
  Great circle: ~80 miles

  Finest shared resolution: none (or resolution 2/3 if those were indexed)
```

The `finestSharedResolution` field tells you the smallest H3 cell that contains both stores — a natural measure of proximity.

### 3.7 POST /stores/h3/add

**What it does:** Adds a new store to both the geo sorted set (GEOADD) and the H3 cell SETs at all resolutions.

**Redis calls:** 1 × GEOADD + 4 × SADD + 1 × HSET = 6 calls. These could be pipelined or wrapped in MULTI/EXEC for atomicity.

---

## 4. H3 vs Redis Native Geospatial: Deep Comparison

### 4.1 The Fundamental Difference

**Redis GEO** is a **database feature**: spatial indexing is done server-side. You send a query, Redis returns results. Your application does not need to understand geohashes, bounding boxes, or the Haversine formula.

**H3** is a **library/algorithm**: spatial indexing is done client-side. Your application computes cell indexes, determines which cells to query, and then uses Redis (or any storage) as a key-value lookup. Redis has no awareness that the keys represent hexagons.

This distinction drives every other tradeoff.

### 4.2 Comparison Table

| Criterion | Redis GEO (GEOSEARCH) | H3 + Redis SETs |
|---|---|---|
| **Cell shape** | N/A (radius circle or bounding box) | Hexagonal (uniform, equidistant neighbors) |
| **Boundary problem** | Handled internally (multi-range scan) | Eliminated by design (6 equidistant neighbors) |
| **Proximity query** | `GEOSEARCH BYRADIUS` — single command | `gridDisk` (in-memory) → `SMEMBERS` × k cells |
| **Query latency** | < 1ms (single command, no round-trips) | 2–5ms (gridDisk + multiple SMEMBERS) |
| **Distance calculation** | `GEODIST` — native, O(1) | `greatCircleDistance` — app-level, same Haversine |
| **Distance metric** | Continuous (miles/km/m) | Grid distance (integer hex steps) + continuous |
| **Write cost** | 1 × GEOADD per location | 1 × GEOADD + N × SADD (one per resolution) |
| **Memory overhead** | ~80 bytes per member in sorted set | + ~80 bytes per member per resolution SET |
| **Sharding** | Poor (single sorted set = single shard) | Excellent (cells are natural shard keys) |
| **Hierarchical drill-down** | Not supported | `cellToParent` / `cellToChildren` |
| **Aggregation (heatmaps)** | Not supported | Count per cell at any resolution |
| **Polygon queries** | Not supported | `polygonToCells` → SMEMBERS |
| **Bounding box queries** | `GEOSEARCH BYBOX` — native | Not directly supported (convert box to cells) |
| **Server dependency** | Requires Redis with GEO support | Any key-value store works (Redis, DynamoDB, etc.) |
| **Polar/antimeridian** | Latitude capped at ±85.05° | Full sphere coverage (icosahedral projection) |
| **Result precision** | Exact (Haversine post-filter) | Approximate (cell membership, then refine) |

### 4.3 The Boundary Problem: Solved by H3

Geohash's biggest weakness is the boundary problem (see `GEOHASH_API_NOTES.md` section 4): two points 10 meters apart can have completely different prefixes if they straddle a cell boundary.

H3 eliminates this structurally:

```
Geohash boundary problem:

   Cell "dr5ru"  │  Cell "dr5rv"
                 │
           X ····│···· Y          X and Y are 10m apart but share
                 │                only prefix "dr5r" (~39 km bucket)

H3: no boundary problem for radius queries:

      ╱╲   ╱╲   ╱╲
     ╱  ╲ ╱  ╲ ╱  ╲
    │ A  │ B  │ C  │
     ╲  ╱ ╲  ╱ ╲  ╱
      ╲╱   ╲╱   ╲╱

    gridDisk(B, 1) returns {A, B, C, D, E, F, G}
    — always includes all adjacent cells, no blind spots
```

When you search with `gridDisk(center, k)`, you get **all cells within k steps**, including cells across any boundary. There is no prefix-based matching that can miss neighbors.

However, H3 still has an **aperture-7 containment issue**: a child cell's geometry can extend slightly beyond its parent cell. This is not a boundary problem for queries (gridDisk handles it), but it means `cellToParent`/`cellToChildren` is not a perfect geometric containment hierarchy.

### 4.4 When Redis GEO Wins

- **Simple radius queries ("find within 5 miles"):** One command, sub-millisecond, no round-trips. H3 requires multiple SMEMBERS calls and application-level distance sorting.
- **Exact distance between two points:** `GEODIST` is O(1) and native. `greatCircleDistance` produces the same result but requires an application-level call.
- **Low write volume, read-heavy workloads:** Redis GEO has zero write amplification. H3 writes N SETs per location per resolution.
- **Small datasets (< 100K locations):** The memory overhead of H3 SETs is wasted when a single GEOSEARCH scans the entire set in < 1ms anyway.
- **When you don't need aggregation or sharding:** If "find nearby" and "calculate distance" are your only operations, Redis GEO is simpler with fewer moving parts.

### 4.5 When H3 Wins

- **Hexagonal aggregation and heatmaps:** Count events per H3 cell to produce density maps. Uber uses this for surge pricing — count ride requests per hex cell to determine price multipliers. Redis GEO has no aggregation capability.

  ```javascript
  // Count rides per hex cell at resolution 7
  const cell = h3.latLngToCell(riderLat, riderLng, 7);
  await redis.incr(`ride-count:res7:${cell}`);

  // Heatmap: get counts for all cells in a region
  const cells = h3.polygonToCells(regionBoundary, 7);
  const counts = await Promise.all(cells.map(c => redis.get(`ride-count:res7:${c}`)));
  ```

- **Natural sharding / partitioning:** H3 cells are natural shard keys. In Redis Cluster, use hash tags:

  ```
  h3:cell:res4:{872a1072dffffff} → shard A
  h3:cell:res4:{872a10729ffffff} → shard B
  ```

  Each geographic region lands on a different shard. Redis GEO's single sorted set cannot be distributed across shards.

- **Polygon/geofence queries:** "Is this point inside our delivery zone?"

  ```javascript
  const deliveryZone = [...polygonCoordinates];
  const zoneCells = h3.polygonToCells(deliveryZone, 7);
  const zoneCellSet = new Set(zoneCells);

  // Check if a delivery address is in the zone
  const addressCell = h3.latLngToCell(addressLat, addressLng, 7);
  const inZone = zoneCellSet.has(addressCell);
  ```

  Redis GEO has no polygon containment check.

- **Hierarchical drill-down:** "How many stores are in this metro area? Zoom in — how many in this neighborhood?"

  ```javascript
  // Metro level (res 4): one cell covers ~1,770 km²
  const metroCell = h3.latLngToCell(lat, lng, 4);
  const metroStores = await redis.sMembers(`h3:cell:res4:${metroCell}`);

  // Drill into neighborhoods (res 7): ~7⁴ child cells
  const neighborhoods = h3.cellToChildren(metroCell, 7);
  ```

- **Moving objects (fleet tracking, delivery drivers):** H3 cell changes are predictable — you know exactly when a driver crosses a cell boundary. Update only the affected cells' SETs, not the entire geo index.

- **Storage-agnostic:** The same H3 logic works with DynamoDB, PostgreSQL, MongoDB, or any key-value store. You are not locked into Redis's GEO commands.

---

## 5. Real-World Scenarios

### 5.1 Ride-Sharing (Uber's Original Use Case)

Uber developed H3 for three core operations:

**Surge pricing:**
- Divide the city into res-7 hex cells (~5 km²)
- Count ride requests per cell per 5-minute window
- If requests exceed supply by a threshold, apply surge multiplier for that cell
- Hexagons ensure uniform coverage — no cell is larger/smaller due to latitude

**Driver assignment:**
- When a rider requests a ride, compute their H3 cell
- `gridDisk(riderCell, 2)` returns ~19 cells to search for available drivers
- Query each cell's driver SET for availability
- This is O(k) SET lookups vs searching all drivers in the city

**Supply/demand heatmaps:**
- Aggregate request counts at resolution 7
- Roll up to resolution 5 for city-wide dashboards using `cellToParent`
- Frontend renders hexagons on the map using `cellToBoundary`

**Why Redis GEO would not work here:** Redis GEO has no aggregation. Counting requests per geographic zone requires an explicit spatial index. H3 cells provide the zones; Redis SETs or counters provide the storage.

### 5.2 Store Locator (This Project)

For a simple "find stores within 5 miles" feature, **Redis GEO is the better choice**:

- One GEOSEARCH command vs 19+ SMEMBERS calls
- Sub-millisecond vs 2–5ms
- Zero write amplification
- Simpler code, fewer moving parts

H3 is overkill unless you also need:
- "How many stores are in each neighborhood?" → H3 cell counts
- "Which delivery zone is this address in?" → H3 polygon query
- Sharding across Redis Cluster nodes → H3 cells as hash tags

### 5.3 Delivery Zone Management

Define delivery zones as polygons, then convert to H3 cells:

```javascript
// Define a delivery zone polygon (GeoJSON ring)
const brooklynZone = [
  [40.694, -73.990], [40.710, -73.940], [40.680, -73.930], [40.660, -73.970]
];

// Convert to H3 cells at resolution 9 (~174m precision)
const zoneCells = h3.polygonToCells(brooklynZone, 9);
// → ~500 cells covering Brooklyn

// Store in Redis
for (const cell of zoneCells) {
  await redis.sAdd("delivery:zone:brooklyn", cell);
}

// Check if an address is deliverable
const addressCell = h3.latLngToCell(customerLat, customerLng, 9);
const deliverable = await redis.sIsMember("delivery:zone:brooklyn", addressCell);
```

Redis GEO cannot do this — it has no polygon containment check. You would need PostGIS (`ST_Contains`) or application-level point-in-polygon math.

### 5.4 Fleet Tracking with Geo-Sharding

For a fleet of 100K delivery drivers updating their location every 5 seconds:

**The problem with Redis GEO at this scale:**
- 100K × GEOADD every 5 seconds = 20K writes/second to a single sorted set
- Single-threaded Redis: all other commands are blocked during write bursts
- Cannot shard the sorted set across Redis Cluster nodes

**H3 solution:**
- Partition drivers by res-4 H3 cell (~1,770 km²) → each city gets its own shard
- Use `latLngToCell` to route driver updates to the correct shard
- Use `gridDisk` on the rider's shard for nearby driver lookups

```
Driver Update Flow:
1. Driver sends (lat, lng) every 5 seconds
2. Compute h3.latLngToCell(lat, lng, 4) → determines shard
3. Compute h3.latLngToCell(lat, lng, 7) → determines cell within shard
4. GEOADD on shard's sorted set (for exact distance queries)
5. SREM old cell SET, SADD new cell SET (for hex-based queries)

Rider Nearby Query:
1. Compute rider's res-4 cell → determines which shard(s) to query
2. gridDisk(riderCell_res7, 2) → 19 cells to check
3. SMEMBERS on each cell → available driver IDs
4. GEODIST for exact distance ranking
```

This distributes write load across shards (one per metro area) while maintaining fast proximity queries within each shard.

---

## 6. Production Considerations

### 6.1 Resolution Selection Guide

Choosing the right resolution is critical. Too fine → too many cells to query; too coarse → too many stores per cell (false positives).

| Use Case | Recommended Resolution | Cell Size | Ring Size for ~5 mi Search |
|---|---|---|---|
| Store locator | 7 | ~5.2 km² | ring=2 (~5 km radius) |
| Delivery zone check | 9 | ~0.11 km² | N/A (polygon containment) |
| Surge pricing / heatmap | 7 or 8 | 5.2 / 0.74 km² | N/A (aggregation) |
| Fleet tracking | 7 (nearby) + 4 (sharding) | 5.2 km² / 1,770 km² | ring=3 (~7 km) |
| Indoor / venue level | 10–12 | 15–0.3 m² | ring=1 |

**Rule of thumb:** Pick a resolution where the expected number of results per cell is 1–50. If most cells are empty, you are too fine; if cells have thousands of entries, you are too coarse.

### 6.2 H3 Index Stability

H3 is **deterministic pure math**: the same (lat, lng, resolution) always produces the same cell index. There is no randomness, no server state, no configuration that can change the result.

This means:
- H3 indexes computed in Node.js, Python, Java, Go, or C will all produce the same value
- You can safely compute H3 indexes in different services without coordination
- Pre-computed H3 indexes stored in a database remain valid indefinitely

### 6.3 Pentagon Cells: The Edge Case

12 cells per resolution are pentagons (5 sides instead of 6). They sit at the vertices of the icosahedron base.

- `gridDisk` handles pentagons correctly — it returns the right neighbors
- `gridRingUnsafe` may fail near pentagons (hence "unsafe" in the name)
- Pentagon cells have ~1/7th the area of surrounding hexagons at the same resolution
- In practice, all 12 pentagons at every resolution are in the ocean — none fall on land. You are unlikely to encounter them in real-world applications, but defensive code should handle the possibility.

### 6.4 Write Amplification Mitigation

At 4 resolutions × 1M stores = 4M SET entries = ~320 MB of additional RAM. Mitigation strategies:

1. **Index only resolutions you query.** If you only use resolution 7 for proximity search, don't index 4, 5, 9. This cuts memory by 75%.

2. **Lazy indexing.** Don't pre-compute all resolutions at write time. Compute coarser/finer resolutions on demand using `cellToParent`:

   ```javascript
   // Store only res 9 (finest). Derive coarser on read.
   const res9 = meta.h3Res9;
   const res7 = h3.cellToParent(res9, 7);
   const res5 = h3.cellToParent(res7, 5);
   ```

   This eliminates write amplification but requires per-query computation. For read-heavy workloads, pre-computing is usually worth the memory.

3. **TTL on cell SETs for moving objects.** For fleet tracking, cell SETs become stale as objects move. Use `EXPIRE` on cell SETs or periodically rebuild them.

### 6.5 Combining H3 with Redis GEO (Best of Both Worlds)

The recommended production pattern uses both:

```
┌─────────────────────────────────────────────────────────┐
│  User-facing query: "Find stores within 5 miles"        │
│                                                         │
│  1. H3: determine shard (res 4 cell → shard routing)    │
│  2. Redis GEO: GEOSEARCH on the shard's sorted set      │
│     → exact radius results, sub-millisecond              │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│  Backend analytics: "Heatmap of demand by neighborhood"  │
│                                                         │
│  1. H3: res 7 cells as buckets                           │
│  2. Redis: INCR per cell, read counts for region         │
│     → aggregation by hexagonal zone                      │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│  Geofencing: "Is this address in our delivery zone?"     │
│                                                         │
│  1. H3: polygonToCells for the delivery zone             │
│  2. Redis SET: SISMEMBER for the address's H3 cell       │
│     → O(1) containment check                             │
└─────────────────────────────────────────────────────────┘
```

H3 handles what Redis GEO cannot (sharding, aggregation, polygon queries). Redis GEO handles what H3 is not optimized for (exact radius queries with sub-millisecond latency). Together they cover the full spectrum of location-based operations.

---

## 7. Quick Reference: Three Approaches Compared

| Capability | Redis GEO (`index.js`) | Redis GEOHASH (`index-hash.js`) | H3 + Redis (`index-h3.js`) |
|---|---|---|---|
| **Primary command** | GEOSEARCH | GEOHASH + prefix match | gridDisk + SMEMBERS |
| **Cell shape** | Circle/box | Rectangle | Hexagon |
| **Boundary problem** | None (handled internally) | Yes (major weakness) | None (gridDisk includes neighbors) |
| **Query latency** | < 1ms | < 1ms (but O(N) prefix scan) | 2–5ms |
| **Distance metric** | Miles/km (continuous) | Prefix length (coarse bucket) | Grid steps (integer) + miles |
| **Aggregation** | No | No | Yes (count per cell) |
| **Polygon query** | No | No | Yes (polygonToCells) |
| **Sharding** | Poor | Possible (prefix-based) | Excellent (cell-based) |
| **Hierarchy** | No | Prefix truncation | cellToParent / cellToChildren |
| **Write cost** | 1 command | 1 command | N commands (1 per resolution) |
| **Storage dependency** | Redis-specific | Redis-specific | Storage-agnostic |
| **Best for** | Simple radius queries | Coarse grouping, cache keys | Analytics, sharding, geofencing |
