const express = require("express");
const { createClient } = require("redis");

const app = express();
app.use(express.json());

const PORT = process.env.PORT || 3001;
const REDIS_URL = process.env.REDIS_URL || "redis://localhost:6379";

let redis;

async function connectRedis() {
  redis = createClient({ url: REDIS_URL });
  redis.on("error", (err) => console.error("Redis error:", err));
  await redis.connect();
  console.log("Connected to Redis at", REDIS_URL);
}

// ---------------------------------------------------------------------------
// GET /health
// ---------------------------------------------------------------------------
app.get("/health", async (_req, res) => {
  const pong = await redis.ping();
  res.json({ status: "ok", redis: pong });
});

// ---------------------------------------------------------------------------
// GET /stores/geohash?ids=store:1,store:2,store:3
// GEOHASH — return the 11-character geohash string for each store
// ---------------------------------------------------------------------------
app.get("/stores/geohash", async (req, res) => {
  const { ids } = req.query;
  if (!ids) return res.status(400).json({ error: "ids query param is required (comma-separated)" });

  const members = ids.split(",").map((s) => s.trim());
  const hashes = await redis.geoHash("stores:locations", members);

  const results = await Promise.all(
    members.map(async (id, i) => {
      const meta = await redis.hGetAll(id);
      return { id, name: meta.name, geohash: hashes[i] };
    })
  );

  res.json(results);
});

// ---------------------------------------------------------------------------
// GET /stores/geohash/all
// GEOHASH for every store in the sorted set
// ---------------------------------------------------------------------------
app.get("/stores/geohash/all", async (_req, res) => {
  const members = await redis.zRange("stores:locations", 0, -1);
  const hashes = await redis.geoHash("stores:locations", members);

  const results = await Promise.all(
    members.map(async (id, i) => {
      const meta = await redis.hGetAll(id);
      return { id, name: meta.name, geohash: hashes[i] };
    })
  );

  res.json(results);
});

// ---------------------------------------------------------------------------
// GET /stores/geohash/prefix?prefix=dr5r
// Find all stores whose geohash starts with a given prefix.
// Stores sharing a prefix are in the same geographic cell — this is how
// geohash-based proximity works without radius queries.
// ---------------------------------------------------------------------------
app.get("/stores/geohash/prefix", async (req, res) => {
  const { prefix } = req.query;
  if (!prefix) return res.status(400).json({ error: "prefix query param is required" });

  const members = await redis.zRange("stores:locations", 0, -1);
  const hashes = await redis.geoHash("stores:locations", members);

  const matches = [];
  for (let i = 0; i < members.length; i++) {
    if (hashes[i] && hashes[i].startsWith(prefix)) {
      const meta = await redis.hGetAll(members[i]);
      matches.push({ id: members[i], name: meta.name, geohash: hashes[i] });
    }
  }

  res.json({
    prefix,
    matchCount: matches.length,
    stores: matches,
  });
});

// ---------------------------------------------------------------------------
// GET /stores/geohash/neighbors?id=store:1
// Given a store, find other stores that share progressively longer geohash
// prefixes (i.e. are in the same cell at different precision levels).
// Precision → approximate cell size:
//   1 → 5000km, 2 → 1250km, 3 → 156km, 4 → 39km,
//   5 → 4.9km, 6 → 1.2km, 7 → 153m, 8 → 38m
// ---------------------------------------------------------------------------
app.get("/stores/geohash/neighbors", async (req, res) => {
  const { id } = req.query;
  if (!id) return res.status(400).json({ error: "id query param is required" });

  const [targetHash] = await redis.geoHash("stores:locations", id);
  if (!targetHash) return res.status(404).json({ error: `${id} not found` });

  const members = await redis.zRange("stores:locations", 0, -1);
  const hashes = await redis.geoHash("stores:locations", members);

  const PRECISION_LABELS = [
    null,
    "~5000 km",
    "~1250 km",
    "~156 km",
    "~39 km",
    "~4.9 km",
    "~1.2 km",
    "~153 m",
    "~38 m",
  ];

  // For each precision level (1..8), find stores sharing that prefix
  const byPrecision = {};
  for (let p = 1; p <= 8; p++) {
    const prefix = targetHash.substring(0, p);
    const matching = [];
    for (let i = 0; i < members.length; i++) {
      if (members[i] === id) continue; // skip self
      if (hashes[i] && hashes[i].startsWith(prefix)) {
        matching.push(members[i]);
      }
    }
    if (matching.length > 0) {
      byPrecision[p] = {
        prefix,
        cellSize: PRECISION_LABELS[p],
        stores: matching,
      };
    }
  }

  const meta = await redis.hGetAll(id);
  res.json({
    store: { id, name: meta.name, geohash: targetHash },
    neighbors: byPrecision,
  });
});

// ---------------------------------------------------------------------------
// POST /stores/geohash/compare
// Compare geohashes of two stores to determine their shared prefix length
// and approximate proximity bucket.
// Body: { "storeA": "store:1", "storeB": "store:3" }
// ---------------------------------------------------------------------------
app.post("/stores/geohash/compare", async (req, res) => {
  const { storeA, storeB } = req.body;
  if (!storeA || !storeB) return res.status(400).json({ error: "storeA and storeB are required" });

  const [hashA, hashB] = await redis.geoHash("stores:locations", [storeA, storeB]);
  if (!hashA) return res.status(404).json({ error: `${storeA} not found` });
  if (!hashB) return res.status(404).json({ error: `${storeB} not found` });

  // Find shared prefix length
  let shared = 0;
  while (shared < hashA.length && shared < hashB.length && hashA[shared] === hashB[shared]) {
    shared++;
  }

  const PRECISION_APPROX = [
    "different hemisphere",
    "~5000 km",
    "~1250 km",
    "~156 km",
    "~39 km",
    "~4.9 km",
    "~1.2 km",
    "~153 m",
    "~38 m",
    "~5 m",
    "~1 m",
    "< 1 m",
  ];

  // Also get the exact distance for comparison
  const dist = await redis.geoDist("stores:locations", storeA, storeB, "mi");

  const metaA = await redis.hGetAll(storeA);
  const metaB = await redis.hGetAll(storeB);

  res.json({
    storeA: { id: storeA, name: metaA.name, geohash: hashA },
    storeB: { id: storeB, name: metaB.name, geohash: hashB },
    sharedPrefix: hashA.substring(0, shared) || "(none)",
    sharedPrefixLength: shared,
    approximateProximity: PRECISION_APPROX[shared] || "< 1 m",
    exactDistanceMiles: parseFloat(dist),
  });
});

// ---------------------------------------------------------------------------
// POST /stores/geohash/add
// Add a new location by coordinates, return its geohash
// Body: { "id": "store:99", "name": "Test Store", "lat": 40.75, "lng": -73.99 }
// ---------------------------------------------------------------------------
app.post("/stores/geohash/add", async (req, res) => {
  const { id, name, lat, lng } = req.body;
  if (!id || !lat || !lng) return res.status(400).json({ error: "id, lat, lng are required" });

  await redis.geoAdd("stores:locations", {
    longitude: parseFloat(lng),
    latitude: parseFloat(lat),
    member: id,
  });
  await redis.hSet(id, { name: name || id, lng: String(lng), lat: String(lat) });

  const [geohash] = await redis.geoHash("stores:locations", id);

  res.status(201).json({ id, name: name || id, lat, lng, geohash });
});

// ---------------------------------------------------------------------------
// Start
// ---------------------------------------------------------------------------
connectRedis().then(() => {
  app.listen(PORT, () => console.log(`Geohash API listening on :${PORT}`));
});
