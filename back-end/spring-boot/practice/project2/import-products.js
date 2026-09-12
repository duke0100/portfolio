#!/usr/bin/env node

// Imports N random products into project2's Product API, spread evenly across
// existing categories (PER_CATEGORY products per category).
// Requires at least Math.ceil(TOTAL / PER_CATEGORY) categories to already exist
// (use import-categories.js to create them first).
//
// Usage: node import-products.js [count] [baseUrl] [concurrency]
// Example: node import-products.js 1000000 http://localhost:8082 50

const TOTAL = parseInt(process.argv[2] || "1000000", 10);
const BASE_URL = process.argv[3] || "http://localhost:8082";
const CONCURRENCY = parseInt(process.argv[4] || "50", 10);
const PER_CATEGORY = 1000;

const CATEGORIES_URL = `${BASE_URL}/api/v1/categories`;
const PRODUCTS_URL = `${BASE_URL}/api/v1/products`;
const CATEGORY_PAGE_SIZE = 500;

const ADJECTIVES = [
  "Ultra", "Pro", "Max", "Lite", "Advanced", "Compact", "Wireless", "Portable",
  "Premium", "Smart", "Rugged", "Slim", "Turbo", "Eco", "Classic", "Deluxe",
  "Performance", "Essential", "Signature", "Next-Gen",
];

const NOUNS = [
  "Laptop", "Smartphone", "Headphones", "Keyboard", "Monitor", "Camera",
  "Speaker", "Backpack", "Watch", "Charger", "Router", "Tablet", "Mouse",
  "Chair", "Desk Lamp", "Blender", "Sneakers", "Jacket", "Sunglasses", "Wallet",
];

const BRANDS = [
  "Nova", "Zenith", "Apex", "Vertex", "Orbit", "Pulse", "Fusion", "Quantum",
  "Aria", "Nimbus",
];

function randomItem(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

function randomDecimal(min, max, decimals = 2) {
  const value = Math.random() * (max - min) + min;
  return Number(value.toFixed(decimals));
}

function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

async function fetchCategories(minCount) {
  const categories = [];
  let page = 0;

  while (categories.length < minCount) {
    const response = await fetch(
      `${CATEGORIES_URL}?page=${page}&size=${CATEGORY_PAGE_SIZE}`
    );
    if (!response.ok) {
      const body = await response.text();
      throw new Error(`Failed to fetch categories: HTTP ${response.status}: ${body}`);
    }

    const payload = await response.json();
    const pageData = payload.data;
    const content = pageData.content || [];
    categories.push(...content.map((c) => c.id));

    const isLastPage =
      pageData.last === true || content.length === 0 || categories.length >= pageData.totalElements;
    if (isLastPage) break;
    page++;
  }

  return categories;
}

function buildProduct(index, categoryId) {
  const adjective = randomItem(ADJECTIVES);
  const noun = randomItem(NOUNS);
  const brand = randomItem(BRANDS);
  const name = `${brand} ${adjective} ${noun} ${index}`;
  return {
    categoryId,
    name,
    description: `${name} — generated product for load testing.`,
    price: randomDecimal(5, 2000),
    stockQuantity: randomInt(0, 500),
    sku: `SKU-${index}`,
    imageUrl: `https://picsum.photos/seed/product-${index}/400/300`,
    weight: randomDecimal(0.1, 20, 3),
    brand,
    dimensions: `${randomInt(5, 100)}x${randomInt(5, 100)}x${randomInt(5, 100)}cm`,
    discountPercent: Math.random() < 0.3 ? randomDecimal(5, 50) : 0,
  };
}

async function createProduct(product) {
  const response = await fetch(PRODUCTS_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(product),
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
      if ((success + failed) % 1000 === 0) {
        console.log(`Progress: ${success + failed}/${items.length} (ok: ${success}, failed: ${failed})`);
      }
    }
  }

  const workers = Array.from({ length: Math.min(CONCURRENCY, items.length) }, next);
  await Promise.all(workers);
  return { success, failed };
}

async function main() {
  const categoryCount = Math.ceil(TOTAL / PER_CATEGORY);
  console.log(`Fetching at least ${categoryCount} categories from ${CATEGORIES_URL}...`);

  const categoryIds = await fetchCategories(categoryCount);
  if (categoryIds.length < categoryCount) {
    throw new Error(
      `Need ${categoryCount} categories (${PER_CATEGORY} products each) but only found ${categoryIds.length}. ` +
        `Run "node import-categories.js ${categoryCount} ${BASE_URL}" first.`
    );
  }

  console.log(
    `Importing ${TOTAL} random products into ${PRODUCTS_URL} across ${categoryCount} categories ` +
      `(${PER_CATEGORY}/category, concurrency: ${CONCURRENCY})`
  );

  const products = Array.from({ length: TOTAL }, (_, i) => {
    const categoryId = categoryIds[Math.floor(i / PER_CATEGORY)];
    return buildProduct(i + 1, categoryId);
  });

  const start = Date.now();
  const { success, failed } = await runBatch(products, createProduct);
  const elapsed = ((Date.now() - start) / 1000).toFixed(1);

  console.log(`Done in ${elapsed}s — success: ${success}, failed: ${failed}`);
  process.exitCode = failed > 0 ? 1 : 0;
}

main().catch((err) => {
  console.error("Fatal error:", err);
  process.exitCode = 1;
});
