const express = require("express");
const { createClient } = require("redis");

const app = express();
app.use(express.json());

const PORT = process.env.PORT || 3000;
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
// GET /stores
// List all stores with their coordinates (GEOPOS)
// ---------------------------------------------------------------------------
app.get("/stores", async (_req, res) => {
  const members = await redis.zRange("stores:locations", 0, -1);
  const stores = [];
  for (const id of members) {
    const [pos] = await redis.geoPos("stores:locations", id);
    const meta = await redis.hGetAll(id);
    stores.push({ id, ...meta, longitude: pos.longitude, latitude: pos.latitude });
  }
  res.json(stores);
});

// ---------------------------------------------------------------------------
// GET /stores/nearby?lat=40.7128&lng=-74.006&radius=10&unit=mi
// GEOSEARCH — find stores within a radius
// ---------------------------------------------------------------------------
app.get("/stores/nearby", async (req, res) => {
  const { lat, lng, radius = 10, unit = "mi" } = req.query;
  if (!lat || !lng) return res.status(400).json({ error: "lat and lng are required" });

  const results = await redis.geoSearchWith("stores:locations", {
    longitude: parseFloat(lng),
    latitude: parseFloat(lat),
  }, {
    radius: parseFloat(radius),
    unit,
  }, ["WITHCOORD", "WITHDIST"]);

  // results: [{ member, distance, coordinates: { longitude, latitude } }, ...]
  const stores = await Promise.all(
    results.map(async (r) => {
      const meta = await redis.hGetAll(r.member);
      return {
        id: r.member,
        name: meta.name,
        distance: parseFloat(r.distance),
        unit,
        longitude: r.coordinates.longitude,
        latitude: r.coordinates.latitude,
      };
    })
  );

  stores.sort((a, b) => a.distance - b.distance);
  res.json(stores);
});

// ---------------------------------------------------------------------------
// GET /stores/distance?from=store:1&to=store:5&unit=mi
// GEODIST — distance between two stores
// ---------------------------------------------------------------------------
app.get("/stores/distance", async (req, res) => {
  const { from, to, unit = "mi" } = req.query;
  if (!from || !to) return res.status(400).json({ error: "from and to are required" });

  const dist = await redis.geoDist("stores:locations", from, to, unit);
  res.json({ from, to, distance: parseFloat(dist), unit });
});

// ---------------------------------------------------------------------------
// POST /stores/search-inventory
// Combined geo + inventory search
// Body: { "lat": 40.7128, "lng": -74.006, "radiusMiles": 10, "productName": "puma" }
// ---------------------------------------------------------------------------
app.post("/stores/search-inventory", async (req, res) => {
  const { lat, lng, radiusMiles = 10, productName } = req.body;
  if (!lat || !lng) return res.status(400).json({ error: "lat and lng are required" });

  // Step 1: find nearby stores via GEOSEARCH
  const nearby = await redis.geoSearchWith("stores:locations", {
    longitude: parseFloat(lng),
    latitude: parseFloat(lat),
  }, {
    radius: parseFloat(radiusMiles),
    unit: "mi",
  }, ["WITHCOORD", "WITHDIST"]);

  // Step 2: for each store, check inventory for matching products
  const results = [];
  for (const r of nearby) {
    const storeMeta = await redis.hGetAll(r.member);

    // scan inventory keys for this store
    const pattern = `inventory:${r.member}:prod:*`;
    const keys = [];
    for await (const key of redis.scanIterator({ MATCH: pattern, COUNT: 100 })) {
      keys.push(key);
    }

    for (const key of keys) {
      const inv = await redis.hGetAll(key);
      const product = await redis.hGetAll(inv.productId);

      if (productName && !product.name.toLowerCase().includes(productName.toLowerCase())) {
        continue;
      }
      if (parseInt(inv.stockQty) === 0) continue;

      results.push({
        productId: inv.productId,
        productName: product.name,
        category: product.category,
        price: parseFloat(product.price),
        storeId: r.member,
        storeName: storeMeta.name,
        stockQty: parseInt(inv.stockQty),
        distanceInMiles: parseFloat(r.distance),
      });
    }
  }

  results.sort((a, b) => a.distanceInMiles - b.distanceInMiles);
  res.json(results);
});

// ---------------------------------------------------------------------------
// Start
// ---------------------------------------------------------------------------
connectRedis().then(() => {
  app.listen(PORT, () => console.log(`API listening on :${PORT}`));
});
