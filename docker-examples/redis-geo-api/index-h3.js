const express = require("express");
const { createClient } = require("redis");
const h3 = require("h3-js");

const app = express();
app.use(express.json());

const PORT = process.env.PORT || 3002;
const REDIS_URL = process.env.REDIS_URL || "redis://localhost:6379";
const DEFAULT_RESOLUTION = 7; // ~5.2 km² hexagons
const H3_RESOLUTIONS = [4, 5, 7, 9];
const KM_TO_MI = 0.621371;

let redis;

async function connectRedis() {
  redis = createClient({ url: REDIS_URL });
  redis.on("error", (err) => console.error("Redis error:", err));
  await redis.connect();
  console.log("Connected to Redis at", REDIS_URL);
}

// ---------------------------------------------------------------------------
// Helper: enrich a store ID with metadata and H3 fields from its hash
// ---------------------------------------------------------------------------
async function enrichStore(storeId) {
  const meta = await redis.hGetAll(storeId);
  return { id: storeId, ...meta };
}

// ---------------------------------------------------------------------------
// GET /health
// ---------------------------------------------------------------------------
app.get("/health", async (_req, res) => {
  const pong = await redis.ping();
  res.json({ status: "ok", redis: pong });
});

// ---------------------------------------------------------------------------
// GET /stores/h3
// List all stores with their H3 indexes at every seeded resolution
// ---------------------------------------------------------------------------
app.get("/stores/h3", async (_req, res) => {
  const members = await redis.zRange("stores:locations", 0, -1);

  const stores = await Promise.all(
    members.map(async (id) => {
      const meta = await redis.hGetAll(id);
      const h3Indexes = {};
      for (const r of H3_RESOLUTIONS) {
        h3Indexes[`res${r}`] = meta[`h3Res${r}`] || null;
      }
      return {
        id,
        name: meta.name,
        lat: parseFloat(meta.lat),
        lng: parseFloat(meta.lng),
        h3: h3Indexes,
      };
    })
  );

  res.json(stores);
});

// ---------------------------------------------------------------------------
// GET /stores/h3/cell?lat=40.7128&lng=-74.006&resolution=7
// Get the H3 cell for a coordinate and return all stores in that cell
// ---------------------------------------------------------------------------
app.get("/stores/h3/cell", async (req, res) => {
  const { lat, lng, resolution } = req.query;
  if (!lat || !lng) return res.status(400).json({ error: "lat and lng are required" });

  const r = parseInt(resolution) || DEFAULT_RESOLUTION;
  const cellIndex = h3.latLngToCell(parseFloat(lat), parseFloat(lng), r);
  const cellCenter = h3.cellToLatLng(cellIndex);
  const cellBoundary = h3.cellToBoundary(cellIndex);
  const areaKm2 = h3.cellArea(cellIndex, "km2");

  // Find stores in this cell via the reverse-lookup SET
  const storeIds = await redis.sMembers(`h3:cell:res${r}:${cellIndex}`);
  const stores = await Promise.all(storeIds.map(enrichStore));

  res.json({
    cell: cellIndex,
    resolution: r,
    center: { lat: cellCenter[0], lng: cellCenter[1] },
    boundary: cellBoundary.map(([lt, ln]) => ({ lat: lt, lng: ln })),
    areaKm2: parseFloat(areaKm2.toFixed(4)),
    storeCount: stores.length,
    stores,
  });
});

// ---------------------------------------------------------------------------
// GET /stores/h3/nearby?lat=40.7128&lng=-74.006&resolution=7&ring=2
// Use gridDisk to find stores within k hex rings of a point.
// ring=1 → 7 cells (self + 6 neighbors)
// ring=2 → 19 cells
// ring=3 → 37 cells
// ---------------------------------------------------------------------------
app.get("/stores/h3/nearby", async (req, res) => {
  const { lat, lng, resolution, ring = 2 } = req.query;
  if (!lat || !lng) return res.status(400).json({ error: "lat and lng are required" });

  const r = parseInt(resolution) || DEFAULT_RESOLUTION;
  const k = parseInt(ring);
  const centerCell = h3.latLngToCell(parseFloat(lat), parseFloat(lng), r);

  // gridDisk returns all cells within k steps (filled hexagonal disk)
  const diskCells = h3.gridDisk(centerCell, k);

  // For each cell, look up stores in the reverse-lookup SET
  const storeIdSets = await Promise.all(
    diskCells.map((cell) => redis.sMembers(`h3:cell:res${r}:${cell}`))
  );

  // Flatten, deduplicate, enrich
  const uniqueIds = [...new Set(storeIdSets.flat())];
  const queryLat = parseFloat(lat);
  const queryLng = parseFloat(lng);

  const stores = await Promise.all(
    uniqueIds.map(async (id) => {
      const meta = await redis.hGetAll(id);
      const storeLat = parseFloat(meta.lat);
      const storeLng = parseFloat(meta.lng);
      const distKm = h3.greatCircleDistance(
        [queryLat, queryLng],
        [storeLat, storeLng],
        "km"
      );
      const distMiles = distKm * KM_TO_MI;
      const storeCell = meta[`h3Res${r}`];
      return {
        id,
        name: meta.name,
        lat: storeLat,
        lng: storeLng,
        h3Cell: storeCell,
        gridSteps: storeCell ? h3.gridDistance(centerCell, storeCell) : null,
        distanceMiles: parseFloat(distMiles.toFixed(4)),
      };
    })
  );

  stores.sort((a, b) => a.distanceMiles - b.distanceMiles);

  res.json({
    center: { lat: queryLat, lng: queryLng },
    centerCell,
    resolution: r,
    ringSize: k,
    cellsSearched: diskCells.length,
    storeCount: stores.length,
    stores,
  });
});

// ---------------------------------------------------------------------------
// GET /stores/h3/distance?from=store:1&to=store:3
// Grid distance (hex steps) + great circle distance between two stores
// ---------------------------------------------------------------------------
app.get("/stores/h3/distance", async (req, res) => {
  const { from, to, resolution } = req.query;
  if (!from || !to) return res.status(400).json({ error: "from and to are required" });

  const r = parseInt(resolution) || DEFAULT_RESOLUTION;

  const metaFrom = await redis.hGetAll(from);
  const metaTo = await redis.hGetAll(to);
  if (!metaFrom.name) return res.status(404).json({ error: `${from} not found` });
  if (!metaTo.name) return res.status(404).json({ error: `${to} not found` });

  const cellFrom = metaFrom[`h3Res${r}`] || h3.latLngToCell(parseFloat(metaFrom.lat), parseFloat(metaFrom.lng), r);
  const cellTo = metaTo[`h3Res${r}`] || h3.latLngToCell(parseFloat(metaTo.lat), parseFloat(metaTo.lng), r);

  let gridDist;
  try {
    gridDist = h3.gridDistance(cellFrom, cellTo);
  } catch {
    gridDist = null; // gridDistance throws if cells are too far apart (different base cells)
  }

  const gcDistKm = h3.greatCircleDistance(
    [parseFloat(metaFrom.lat), parseFloat(metaFrom.lng)],
    [parseFloat(metaTo.lat), parseFloat(metaTo.lng)],
    "km"
  );
  const gcDistMi = gcDistKm * KM_TO_MI;

  res.json({
    from: { id: from, name: metaFrom.name, h3Cell: cellFrom },
    to: { id: to, name: metaTo.name, h3Cell: cellTo },
    resolution: r,
    gridDistance: gridDist,
    greatCircleDistanceMiles: parseFloat(gcDistMi.toFixed(4)),
  });
});

// ---------------------------------------------------------------------------
// GET /stores/h3/neighbors?id=store:1
// Show which other stores share the same H3 cell at each resolution.
// At coarser resolutions (lower number), more stores share a cell.
// ---------------------------------------------------------------------------
app.get("/stores/h3/neighbors", async (req, res) => {
  const { id } = req.query;
  if (!id) return res.status(400).json({ error: "id query param is required" });

  const meta = await redis.hGetAll(id);
  if (!meta.name) return res.status(404).json({ error: `${id} not found` });

  const RESOLUTION_LABELS = {
    4: "~1,770 km² (city-level)",
    5: "~253 km² (district-level)",
    7: "~5.2 km² (neighborhood-level)",
    9: "~0.11 km² (block-level)",
  };

  const byResolution = {};
  for (const r of H3_RESOLUTIONS) {
    const cellIndex = meta[`h3Res${r}`];
    if (!cellIndex) continue;

    const storeIds = await redis.sMembers(`h3:cell:res${r}:${cellIndex}`);
    const neighbors = storeIds.filter((sid) => sid !== id);

    byResolution[r] = {
      cell: cellIndex,
      cellSize: RESOLUTION_LABELS[r],
      area: parseFloat(h3.cellArea(cellIndex, "km2").toFixed(4)),
      neighborsInCell: neighbors,
    };
  }

  res.json({
    store: {
      id,
      name: meta.name,
      lat: parseFloat(meta.lat),
      lng: parseFloat(meta.lng),
    },
    resolutions: byResolution,
  });
});

// ---------------------------------------------------------------------------
// POST /stores/h3/compare
// Compare two stores: H3 cells at each resolution, grid distance, exact distance
// Body: { "storeA": "store:1", "storeB": "store:8" }
// ---------------------------------------------------------------------------
app.post("/stores/h3/compare", async (req, res) => {
  const { storeA, storeB } = req.body;
  if (!storeA || !storeB) return res.status(400).json({ error: "storeA and storeB are required" });

  const metaA = await redis.hGetAll(storeA);
  const metaB = await redis.hGetAll(storeB);
  if (!metaA.name) return res.status(404).json({ error: `${storeA} not found` });
  if (!metaB.name) return res.status(404).json({ error: `${storeB} not found` });

  const gcDistKm = h3.greatCircleDistance(
    [parseFloat(metaA.lat), parseFloat(metaA.lng)],
    [parseFloat(metaB.lat), parseFloat(metaB.lng)],
    "km"
  );
  const gcDistMi = gcDistKm * KM_TO_MI;

  const resolutions = {};
  for (const r of H3_RESOLUTIONS) {
    const cellA = metaA[`h3Res${r}`];
    const cellB = metaB[`h3Res${r}`];
    if (!cellA || !cellB) continue;

    let gridDist;
    try {
      gridDist = h3.gridDistance(cellA, cellB);
    } catch {
      gridDist = null;
    }

    resolutions[r] = {
      cellA,
      cellB,
      sameCell: cellA === cellB,
      gridDistance: gridDist,
    };
  }

  // Find the finest resolution where both stores share a cell
  const sharedAt = H3_RESOLUTIONS.filter((r) => resolutions[r]?.sameCell);
  const finestShared = sharedAt.length > 0 ? Math.max(...sharedAt) : null;

  res.json({
    storeA: { id: storeA, name: metaA.name },
    storeB: { id: storeB, name: metaB.name },
    greatCircleDistanceMiles: parseFloat(gcDistMi.toFixed(4)),
    finestSharedResolution: finestShared,
    resolutions,
  });
});

// ---------------------------------------------------------------------------
// POST /stores/h3/add
// Add a new store, compute and store its H3 indexes
// Body: { "id": "store:99", "name": "Test Store", "lat": 40.75, "lng": -73.99 }
// ---------------------------------------------------------------------------
app.post("/stores/h3/add", async (req, res) => {
  const { id, name, lat, lng } = req.body;
  if (!id || !lat || !lng) return res.status(400).json({ error: "id, lat, lng are required" });

  const parsedLat = parseFloat(lat);
  const parsedLng = parseFloat(lng);

  // Add to geo sorted set (shared with other APIs)
  await redis.geoAdd("stores:locations", {
    longitude: parsedLng,
    latitude: parsedLat,
    member: id,
  });

  // Compute H3 indexes at all resolutions and store
  const h3Fields = { name: name || id, lng: String(parsedLng), lat: String(parsedLat) };
  const h3Indexes = {};

  for (const r of H3_RESOLUTIONS) {
    const cellIndex = h3.latLngToCell(parsedLat, parsedLng, r);
    h3Fields[`h3Res${r}`] = cellIndex;
    h3Indexes[`res${r}`] = cellIndex;
    await redis.sAdd(`h3:cell:res${r}:${cellIndex}`, id);
  }

  await redis.hSet(id, h3Fields);

  res.status(201).json({ id, name: name || id, lat: parsedLat, lng: parsedLng, h3: h3Indexes });
});

// ---------------------------------------------------------------------------
// Start
// ---------------------------------------------------------------------------
connectRedis().then(() => {
  app.listen(PORT, () => console.log(`H3 API listening on :${PORT}`));
});
