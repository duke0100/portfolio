#!/usr/bin/env node

// Imports N random categories into project2's Category API.
// Usage: node import-categories.js [count] [baseUrl]
// Example: node import-categories.js 1000 http://localhost:8082

const TOTAL = parseInt(process.argv[2] || "1000", 10);
const BASE_URL = process.argv[3] || "http://localhost:8082";
const API_URL = `${BASE_URL}/api/v1/categories`;
const CONCURRENCY = 20;

const ADJECTIVES = [
  "Modern", "Classic", "Premium", "Eco", "Smart", "Vintage", "Urban", "Rustic",
  "Digital", "Organic", "Compact", "Deluxe", "Portable", "Handmade", "Wireless",
  "Sporty", "Elegant", "Rugged", "Minimal", "Luxury",
];

const NOUNS = [
  "Electronics", "Furniture", "Apparel", "Footwear", "Accessories", "Kitchenware",
  "Toys", "Books", "Beauty", "Sports Gear", "Groceries", "Tools", "Jewelry",
  "Stationery", "Pet Supplies", "Garden Tools", "Home Decor", "Automotive Parts",
  "Musical Instruments", "Fitness Equipment",
];

function randomItem(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

function slugify(text, suffix) {
  return `${text}-${suffix}`
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "");
}

function buildCategory(index) {
  const adjective = randomItem(ADJECTIVES);
  const noun = randomItem(NOUNS);
  const name = `${adjective} ${noun} ${index}`;
  return {
    name,
    description: `${name} category generated for testing purposes.`,
    slug: slugify(name, index),
    imageUrl: `https://picsum.photos/seed/category-${index}/400/300`,
    displayOrder: Math.floor(Math.random() * 100),
    isFeatured: Math.random() < 0.2,
    metaTitle: name,
    metaDescription: `Browse our collection of ${name.toLowerCase()}.`,
  };
}

async function createCategory(category) {
  const response = await fetch(API_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(category),
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(`HTTP ${response.status}: ${body}`);
  }

  return response.json();
}

async function runBatch(items, worker) {
  let cursor = 0;
  let success = 0;
  let failed = 0;

  async function next() {
    while (cursor < items.length) {
      const current = cursor++;
      try {
        await worker(items[current]);
        success++;
      } catch (err) {
        failed++;
        console.error(`[${current + 1}/${items.length}] failed: ${err.message}`);
      }
      if ((success + failed) % 100 === 0) {
        console.log(`Progress: ${success + failed}/${items.length} (ok: ${success}, failed: ${failed})`);
      }
    }
  }

  const workers = Array.from({ length: Math.min(CONCURRENCY, items.length) }, next);
  await Promise.all(workers);
  return { success, failed };
}

async function main() {
  console.log(`Importing ${TOTAL} random categories into ${API_URL} (concurrency: ${CONCURRENCY})`);

  const categories = Array.from({ length: TOTAL }, (_, i) => buildCategory(i + 1));
  const start = Date.now();
  const { success, failed } = await runBatch(categories, createCategory);
  const elapsed = ((Date.now() - start) / 1000).toFixed(1);

  console.log(`Done in ${elapsed}s — success: ${success}, failed: ${failed}`);
  process.exitCode = failed > 0 ? 1 : 0;
}

main().catch((err) => {
  console.error("Fatal error:", err);
  process.exitCode = 1;
});
