const { createClient } = require("redis");
const h3 = require("h3-js");

const STORES = [
  { id: "store:1", name: "NYC Downtown", lng: -74.006, lat: 40.7128 },
  { id: "store:2", name: "Brooklyn Heights", lng: -73.9934, lat: 40.6959 },
  { id: "store:3", name: "Jersey City", lng: -74.0431, lat: 40.7178 },
  { id: "store:4", name: "Hoboken", lng: -74.0323, lat: 40.744 },
  { id: "store:5", name: "Newark", lng: -74.1724, lat: 40.7357 },
  { id: "store:6", name: "Stamford CT", lng: -73.5387, lat: 41.0534 },
  { id: "store:7", name: "White Plains", lng: -73.7629, lat: 41.034 },
  { id: "store:8", name: "Philadelphia", lng: -75.1652, lat: 39.9526 },
];

const PRODUCTS = [
  { id: "prod:1", name: "Puma Running Shoe", category: "footwear", price: 89.99 },
  { id: "prod:2", name: "Nike Air Max", category: "footwear", price: 129.99 },
  { id: "prod:3", name: "Adidas Backpack", category: "accessories", price: 49.99 },
];

async function seed() {
  const client = createClient({ url: process.env.REDIS_URL || "redis://localhost:6379" });
  await client.connect();
  console.log("Connected to Redis");

  // Flush previous data
  await client.flushDb();

  // --- GEOADD: index store locations ---
  for (const store of STORES) {
    await client.geoAdd("stores:locations", {
      longitude: store.lng,
      latitude: store.lat,
      member: store.id,
    });
    // Store metadata as a hash
    await client.hSet(store.id, { name: store.name, lng: String(store.lng), lat: String(store.lat) });
  }
  console.log(`Added ${STORES.length} store locations via GEOADD`);

  // --- H3: index store locations at multiple resolutions ---
  const H3_RESOLUTIONS = [4, 5, 7, 9];
  for (const store of STORES) {
    const h3Fields = {};
    for (const res of H3_RESOLUTIONS) {
      const cellIndex = h3.latLngToCell(store.lat, store.lng, res);
      h3Fields[`h3Res${res}`] = cellIndex;
      // Reverse-lookup SET: h3 cell → store IDs
      await client.sAdd(`h3:cell:res${res}:${cellIndex}`, store.id);
    }
    await client.hSet(store.id, h3Fields);
  }
  console.log(`Indexed ${STORES.length} stores with H3 at resolutions [${H3_RESOLUTIONS}]`);

  // --- Store product details ---
  for (const prod of PRODUCTS) {
    await client.hSet(prod.id, {
      name: prod.name,
      category: prod.category,
      price: String(prod.price),
    });
  }

  // --- Seed random inventory per store ---
  for (const store of STORES) {
    for (const prod of PRODUCTS) {
      const qty = Math.floor(Math.random() * 30);
      await client.hSet(`inventory:${store.id}:${prod.id}`, {
        storeId: store.id,
        productId: prod.id,
        stockQty: String(qty),
      });
    }
  }
  console.log("Seeded inventory for all stores");

  await client.quit();
  console.log("Seed complete");
}

seed().catch((err) => {
  console.error(err);
  process.exit(1);
});
