# Redis GEO API: Implementation Deep Dive

This API (`index.js`, port 3000) exposes four endpoints built on Redis GEO commands: `GEOADD`, `GEOPOS`, `GEOSEARCH`, and `GEODIST`. This document explains what happens beneath each command, the data structures involved, performance characteristics, and production gotchas.

---

## 1. How Redis Stores Geo Data Internally

### 1.1 The Sorted Set Foundation

`GEOADD` does not create a special data type. It writes to a standard **sorted set** (ZSET).

```
GEOADD stores:locations -74.006 40.7128 "store:1"
```

Under the hood, this is equivalent to:

```
ZADD stores:locations <52-bit-geohash-as-float> "store:1"
```

The "score" is a 52-bit integer derived from interleaving latitude and longitude bits (a geohash encoded as a double-precision float). Members are the store IDs.

Because geo data lives in a ZSET, you can use standard sorted set commands on it:

```javascript
// This works because stores:locations IS a sorted set
const members = await redis.zRange("stores:locations", 0, -1);
```

This is exactly what the `GET /stores` endpoint does in `index.js:32` — it calls `zRange` to enumerate all store IDs, then enriches each with `GEOPOS` and `HGETALL`.

### 1.2 Coordinate Encoding: Latitude/Longitude to 52-bit Geohash

Redis uses:
- **26 bits** for longitude (range: -180 to 180)
- **26 bits** for latitude (range: -85.05112878 to 85.05112878)

The latitude range is **not** -90 to 90. Redis clips at the Mercator projection limit (±85.05112878°). Points in Antarctica or the Arctic beyond this latitude cannot be stored.

The bits are interleaved:

```
lon_bit0, lat_bit0, lon_bit1, lat_bit1, lon_bit2, lat_bit2, ...
```

This produces a single 52-bit integer that preserves spatial locality — nearby coordinates produce numerically close scores, which is why sorted set range queries can approximate spatial queries.

### 1.3 Why 52 Bits?

52 is the number of mantissa bits in an IEEE 754 double-precision float. By using exactly 52 bits, Redis stores the geohash as the sorted set score without any precision loss. This is a deliberate design choice — no new data structure needed, just a clever use of the existing ZSET.

### 1.4 Precision

52 bits yields sub-meter resolution (~0.6m at the equator). For a store locator, this is far more precision than needed. The tradeoff is that decoded coordinates will not be bit-identical to the originals — `GEOPOS` for a store at `(-74.006, 40.7128)` might return `(-74.00600105, 40.71279904)`.

---

## 2. Command-by-Command Breakdown

### 2.1 GEOADD (used in `seed.js:30-34`)

```javascript
await client.geoAdd("stores:locations", {
  longitude: store.lng,
  latitude: store.lat,
  member: store.id,
});
```

**What happens internally:**
1. Validates longitude is in [-180, 180] and latitude in [-85.05112878, 85.05112878].
2. Converts `(lng, lat)` to a 52-bit geohash.
3. Calls `ZADD` with the geohash as the score.

**Key properties:**
- **Time complexity:** O(log N) per member (same as ZADD — skip list insertion).
- **Idempotent:** Re-adding the same member updates its coordinates silently.
- **Bulk support:** GEOADD accepts multiple `longitude latitude member` triples in a single command. The seed script adds stores one at a time (8 round-trips); a single bulk call would reduce this to 1.

```javascript
// Optimized: single call for all stores
await client.geoAdd("stores:locations", STORES.map(s => ({
  longitude: s.lng, latitude: s.lat, member: s.id,
})));
```

### 2.2 GEOPOS (used in `GET /stores`, `index.js:35`)

```javascript
const [pos] = await redis.geoPos("stores:locations", id);
// pos = { longitude: -74.00600105, latitude: 40.71279904 }
```

**What happens internally:**
1. Reads the ZSCORE for the member (the 52-bit geohash as a float).
2. Decodes the float back to `(longitude, latitude)`.

**Key properties:**
- **Time complexity:** O(1) per member (ZSCORE is O(1) via the internal dict).
- **Quantization error:** Decoded coordinates differ from originals by up to ~0.6m. For store locations this is irrelevant; for sub-meter tracking (indoor positioning) it is a hard limit.
- **Bulk support:** GEOPOS accepts multiple members. The `/stores` endpoint calls it per-member in a loop — a single bulk call would eliminate N-1 round-trips.

### 2.3 GEOSEARCH (used in `GET /stores/nearby` and `POST /stores/search-inventory`)

```javascript
const results = await redis.geoSearchWith("stores:locations", {
  longitude: parseFloat(lng),
  latitude: parseFloat(lat),
}, {
  radius: parseFloat(radius),
  unit,
}, ["WITHCOORD", "WITHDIST"]);
```

**What happens internally (four-step process):**

```
Step 1: Compute bounding box
  ┌─────────────────────┐
  │                     │
  │    ┌───────────┐    │
  │    │  Radius   │    │
  │    │  circle   │    │
  │    │    (•)    │    │   (•) = search center
  │    │           │    │
  │    └───────────┘    │
  │   Bounding box      │
  └─────────────────────┘

Step 2: Convert bounding box to geohash ranges
  The box maps to one or more sorted set score ranges.

Step 3: ZRANGEBYSCORE on each range
  Returns candidates — all members whose geohash falls in the box.
  O(log N + M) where M = candidates in the box.

Step 4: Haversine post-filter
  For each candidate, compute exact distance from center.
  Discard any member outside the actual radius (box is an over-approximation).
  This is where WITHDIST values are computed.
```

**Key properties:**
- **Time complexity:** O(N + log(N) * M) where N = set size, M = results. For small sets (8 stores) this is effectively O(1). For 1M stores with a tight radius returning 50 results, it is still fast (~1-2ms).
- **Two search shapes:** `BYRADIUS` (circle) and `BYBOX` (rectangle). The current code uses radius.
- **WITHCOORD and WITHDIST:** These are computed during the post-filter phase, not stored separately. They add decoded coordinates and Haversine distance to each result.
- **Sorting:** GEOSEARCH supports `ASC`/`DESC` sorting by distance natively. The `index.js:73` code sorts client-side (`stores.sort(...)`) which is redundant — adding `ASC` to the command would be more efficient.
- **Replaced GEORADIUS:** GEOSEARCH was introduced in Redis 6.2 as a replacement for the deprecated GEORADIUS/GEORADIUSBYMEMBER commands.

### 2.4 GEODIST (used in `GET /stores/distance`, `index.js:85`)

```javascript
const dist = await redis.geoDist("stores:locations", from, to, unit);
```

**What happens internally:**
1. GEOPOS for both members (two score lookups + decoding).
2. Haversine formula on the decoded coordinates.

**The Haversine formula:**
- Assumes Earth is a perfect sphere with radius ~6,372.797 km.
- This introduces error of up to **~0.5%** compared to the WGS-84 ellipsoid (Vincenty formula).
- At 100 miles, the error is ~0.5 miles. For a store locator, negligible. For surveying or aviation, unacceptable.

**Key properties:**
- **Time complexity:** O(1) — two score lookups + math.
- **Supported units:** `m`, `km`, `mi`, `ft`. Conversion is applied after Haversine.
- **Missing members:** Returns `null` if either member does not exist. The current code does not guard against this — `parseFloat(null)` returns `NaN`, so the response would be `{ distance: NaN }`.

---

## 3. Data Modeling Pattern

### 3.1 Three Data Structure Types Working Together

```
┌─────────────────────────┐
│  Sorted Set (ZSET)      │    Spatial index
│  key: stores:locations  │
│  ┌───────────┬────────┐ │
│  │ member    │ score  │ │
│  ├───────────┼────────┤ │
│  │ store:1   │ <hash> │ │
│  │ store:2   │ <hash> │ │
│  │ ...       │ ...    │ │
│  └───────────┴────────┘ │
└─────────────────────────┘
         │
         │ member == hash key
         ▼
┌─────────────────────────┐     ┌──────────────────────────────────┐
│  Hash                   │     │  Hash                            │
│  key: store:1           │     │  key: inventory:store:1:prod:1   │
│  ┌───────┬────────────┐ │     │  ┌───────────┬────────────────┐  │
│  │ name  │ NYC Downtn │ │     │  │ storeId   │ store:1        │  │
│  │ lng   │ -74.006    │ │     │  │ productId │ prod:1         │  │
│  │ lat   │ 40.7128    │ │     │  │ stockQty  │ 15             │  │
│  └───────┴────────────┘ │     │  └───────────┴────────────────┘  │
└─────────────────────────┘     └──────────────────────────────────┘
                                           │
                                           │ productId == hash key
                                           ▼
                                ┌──────────────────────────────────┐
                                │  Hash                            │
                                │  key: prod:1                     │
                                │  ┌──────────┬─────────────────┐  │
                                │  │ name     │ Puma Running... │  │
                                │  │ category │ footwear        │  │
                                │  │ price    │ 89.99           │  │
                                │  └──────────┴─────────────────┘  │
                                └──────────────────────────────────┘
```

The sorted set acts as a **spatial index**, not a data store. It answers "which stores are near this point?" and returns member IDs. Metadata lives in hashes.

The critical design choice: **the sorted set member IS the hash key**. `store:1` in the ZSET is the same string as the hash key `store:1`. No lookup table needed.

### 3.2 Key Naming Convention

Colon-separated hierarchical keys:

| Key Pattern | Type | Purpose |
|---|---|---|
| `stores:locations` | ZSET | Geo spatial index |
| `store:{id}` | Hash | Store metadata |
| `prod:{id}` | Hash | Product catalog |
| `inventory:store:{id}:prod:{id}` | Hash | Stock quantity per store per product |

This convention enables SCAN pattern matching — the `search-inventory` endpoint uses `inventory:${storeId}:prod:*` to find all inventory keys for a given store.

### 3.3 The SCAN-Based Inventory Lookup

The `POST /stores/search-inventory` endpoint (`index.js:113-117`) uses SCAN to find inventory keys:

```javascript
const pattern = `inventory:${r.member}:prod:*`;
for await (const key of redis.scanIterator({ MATCH: pattern, COUNT: 100 })) {
  keys.push(key);
}
```

**How SCAN works:**
- Iterates the entire keyspace in chunks, returning keys matching the pattern.
- Each iteration is O(1), but traversing the full keyspace is O(N) total where N = total keys in the database (not just matching keys).
- `COUNT 100` is a hint, not a guarantee — Redis may return more or fewer keys per iteration.

**The scaling problem:**
- Current dataset: 8 stores × 3 products = 24 inventory keys. SCAN completes in ~1 iteration. Fast.
- At scale: 10,000 stores × 1,000 products = 10M inventory keys. SCAN must traverse the entire keyspace to find matches. This endpoint would take seconds, not milliseconds.

**Production alternative:** Maintain a Redis SET per store listing its inventory keys:

```javascript
// During seeding
await client.sAdd(`store:${storeId}:inventory-keys`, `inventory:${storeId}:${prodId}`);

// During query — O(M) where M = products in this store
const keys = await redis.sMembers(`store:${storeId}:inventory-keys`);
```

---

## 4. Time Complexity Summary

| Operation | Command | Complexity | Notes |
|---|---|---|---|
| Add a store location | GEOADD | O(log N) | N = total members in the set |
| Get store coordinates | GEOPOS | O(1) per member | Decodes ZSCORE to lat/lng |
| Find nearby stores | GEOSEARCH | O(N + log(N) × M) | M = results; practically O(log N + M) for small radii |
| Distance between stores | GEODIST | O(1) | Two ZSCORE lookups + Haversine |
| List all stores | ZRANGE | O(log N + M) | M = elements returned |
| Get store metadata | HGETALL | O(K) | K = fields in the hash |
| Scan inventory keys | SCAN | O(1) per call, O(N) total | N = total keys in the DB, not matching keys |

---

## 5. Edge Cases and Limitations

### 5.1 Antimeridian (International Date Line)

Coordinates near ±180° longitude. A radius search centered at 179° with a 100-mile radius should include points at -179°. Redis handles this correctly since version 3.2.10 — it splits the bounding box into two score ranges.

Not relevant for this NYC-area dataset, but critical for deployments in the Pacific (e.g., New Zealand, Fiji, Japan).

### 5.2 Polar Regions

Latitude is capped at ±85.05112878° (the Mercator limit). `GEOADD` will reject coordinates beyond this. Research stations in Antarctica or Arctic weather stations cannot be indexed.

### 5.3 Quantization Error

The 52-bit encoding introduces up to ~0.6m error when decoding coordinates back from the geohash. This means:

```
Input:   (-74.006000, 40.712800)
Stored:  52-bit geohash
Decoded: (-74.006001, 40.712799)   ← ~0.6m difference
```

For store locations, irrelevant. For indoor positioning or sub-meter tracking, this is a hard precision ceiling.

### 5.4 Empty Results and Missing Members

- `GEOSEARCH` with no matches returns an empty array — safe, no special handling needed.
- `GEODIST` returns `null` if either member is missing. The current code does `parseFloat(null)` → `NaN`.
- `GEOPOS` returns `null` entries for non-existent members.

### 5.5 Large Result Sets

`GEOSEARCH` with a very large radius (e.g., 10,000 miles) returns every member in the set. There is no server-side pagination — use the `COUNT` option to limit results:

```javascript
// Limit to the 20 nearest stores
await redis.geoSearchWith("stores:locations", { longitude, latitude },
  { radius: 10, unit: "mi" },
  ["WITHCOORD", "WITHDIST", "COUNT", 20, "ASC"]
);
```

The current code applies no `COUNT`. With millions of stores, this would be a memory and network problem.

---

## 6. Production Considerations

### 6.1 The N+1 Query Problem

The `GET /stores` endpoint makes **2N + 1** Redis calls for N stores:

```
1 × ZRANGE       → get all member IDs
N × GEOPOS       → get coordinates per store
N × HGETALL      → get metadata per store
─────────────────
Total: 2N + 1 = 17 calls for 8 stores
```

**Fix: pipeline all calls into a single round-trip.**

```javascript
const pipeline = redis.multi();
for (const id of members) {
  pipeline.geoPos("stores:locations", id);
  pipeline.hGetAll(id);
}
const results = await pipeline.exec();
```

This reduces 17 sequential round-trips to 1. At 0.5ms per round-trip over a network, that is 8ms saved — and the savings scale linearly.

### 6.2 Memory Footprint

| Component | Per Store | 1K Stores | 1M Stores |
|---|---|---|---|
| Geo sorted set entry | ~80 bytes | ~80 KB | ~80 MB |
| Metadata hash (3 fields) | ~150 bytes | ~150 KB | ~150 MB |
| Inventory hashes (3 products) | ~300 bytes | ~300 KB | ~300 MB |
| **Total** | **~530 bytes** | **~530 KB** | **~530 MB** |

At 1M stores with 3 products each, expect ~530 MB of RAM. At 10 products per store, closer to ~1.2 GB. This is RAM, not disk — budget accordingly.

### 6.3 Single-Threaded Blocking

Redis executes commands on a single thread. One slow `GEOSEARCH` (huge radius, millions of results) blocks **all** other clients until it completes.

The `search-inventory` endpoint is particularly dangerous: it issues GEOSEARCH + N × (SCAN + HGETALL) sequentially. During this chain, no other client can execute commands.

Mitigation: use `COUNT` to cap GEOSEARCH results, replace SCAN with direct key lookups, and pipeline HGETALL calls.

### 6.4 Non-Atomic search-inventory

The `POST /stores/search-inventory` endpoint is **not atomic** across its multiple commands. A store's inventory could change between the GEOSEARCH call and the subsequent HGETALL calls. For a store locator showing "in stock" status, this is usually acceptable (eventual consistency). For inventory reservation or checkout, it is not — you would need `MULTI/EXEC` or Lua scripting.

### 6.5 Error Handling Gaps

The current implementation has:
- No `try/catch` around Redis calls — any Redis error crashes the request.
- No connection retry logic beyond the initial connect.
- No input validation on radius (negative values, non-numeric strings, extremely large values).
- No rate limiting on the SCAN-heavy `search-inventory` endpoint.
- No graceful degradation if Redis is temporarily unreachable.
