#!/usr/bin/env node

// Imports N random orders (each with 1-5 order detail items) into project2's
// Order API. Order details are created atomically with the order via the
// single POST /api/v1/orders payload (items[]) - there is no separate
// order-detail endpoint.
// Requires existing users and products (use import-users.js and
// import-products.js to create them first).
//
// Usage: node import-orders.js [count] [baseUrl] [concurrency]
// Example: node import-orders.js 1000000 http://localhost:8082 50

const TOTAL = parseInt(process.argv[2] || "1000000", 10);
const BASE_URL = process.argv[3] || "http://localhost:8082";
const CONCURRENCY = parseInt(process.argv[4] || "50", 10);

const USERS_URL = `${BASE_URL}/api/v1/users`;
const PRODUCTS_URL = `${BASE_URL}/api/v1/products`;
const ORDERS_URL = `${BASE_URL}/api/v1/orders`;
const PAGE_SIZE = 500;
const USER_POOL_SIZE = 5000;
const PRODUCT_POOL_SIZE = 5000;

const MIN_ITEMS_PER_ORDER = 1;
const MAX_ITEMS_PER_ORDER = 5;

const PAYMENT_METHODS = ["CREDIT_CARD", "DEBIT_CARD", "PAYPAL", "BANK_TRANSFER", "CASH_ON_DELIVERY"];

const STREETS = [
  "Main St", "Oak Ave", "Maple Dr", "Cedar Ln", "Elm St", "Park Ave", "Hill Rd",
  "Lake St", "River Rd", "Sunset Blvd",
];

const CITIES = [
  "Springfield", "Riverside", "Georgetown", "Fairview", "Salem", "Franklin",
  "Greenville", "Bristol", "Clinton", "Madison",
];

function randomItem(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function randomDecimal(min, max, decimals = 2) {
  const value = Math.random() * (max - min) + min;
  return Number(value.toFixed(decimals));
}

function randomAddress() {
  return `${randomInt(1, 9999)} ${randomItem(STREETS)}, ${randomItem(CITIES)}`;
}

function sampleUnique(arr, count) {
  const copy = [...arr];
  const result = [];
  const n = Math.min(count, copy.length);
  for (let i = 0; i < n; i++) {
    const idx = Math.floor(Math.random() * copy.length);
    result.push(copy.splice(idx, 1)[0]);
  }
  return result;
}

async function fetchIds(url, cap) {
  const ids = [];
  let page = 0;

  while (ids.length < cap) {
    const response = await fetch(`${url}?page=${page}&size=${PAGE_SIZE}`);
    if (!response.ok) {
      const body = await response.text();
      throw new Error(`Failed to fetch ${url}: HTTP ${response.status}: ${body}`);
    }

    const payload = await response.json();
    const pageData = payload.data;
    const content = pageData.content || [];
    ids.push(...content.map((item) => item.id));

    const isLastPage =
      pageData.last === true || content.length === 0 || ids.length >= pageData.totalElements;
    if (isLastPage) break;
    page++;
  }

  return ids.slice(0, cap);
}

function buildOrder(index, userIds, productIds) {
  const userId = randomItem(userIds);
  const itemCount = randomInt(MIN_ITEMS_PER_ORDER, MAX_ITEMS_PER_ORDER);
  const items = sampleUnique(productIds, itemCount).map((productId) => ({
    productId,
    quantity: randomInt(1, 10),
    discount: Math.random() < 0.2 ? randomDecimal(1, 20) : 0,
    notes: Math.random() < 0.1 ? `Gift wrap requested for order ${index}` : undefined,
  }));

  return {
    userId,
    shippingAddress: randomAddress(),
    billingAddress: randomAddress(),
    paymentMethod: randomItem(PAYMENT_METHODS),
    notes: Math.random() < 0.2 ? `Order ${index} generated for load testing.` : undefined,
    shippingFee: randomDecimal(0, 25),
    discountAmount: Math.random() < 0.3 ? randomDecimal(1, 50) : 0,
    estimatedDeliveryDate: new Date(Date.now() + randomInt(1, 14) * 86400000)
      .toISOString()
      .slice(0, 10),
    items,
  };
}

async function createOrder(order) {
  const response = await fetch(ORDERS_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(order),
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
  console.log(`Fetching users from ${USERS_URL}...`);
  const userIds = await fetchIds(USERS_URL, USER_POOL_SIZE);
  if (userIds.length === 0) {
    throw new Error(`No users found. Run "node import-users.js <count> ${BASE_URL}" first.`);
  }

  console.log(`Fetching products from ${PRODUCTS_URL}...`);
  const productIds = await fetchIds(PRODUCTS_URL, PRODUCT_POOL_SIZE);
  if (productIds.length === 0) {
    throw new Error(`No products found. Run "node import-products.js <count> ${BASE_URL}" first.`);
  }

  console.log(
    `Importing ${TOTAL} random orders (${MIN_ITEMS_PER_ORDER}-${MAX_ITEMS_PER_ORDER} items each) into ${ORDERS_URL} ` +
      `using ${userIds.length} users and ${productIds.length} products (concurrency: ${CONCURRENCY})`
  );

  const orders = Array.from({ length: TOTAL }, (_, i) => buildOrder(i + 1, userIds, productIds));

  const start = Date.now();
  const { success, failed } = await runBatch(orders, createOrder);
  const elapsed = ((Date.now() - start) / 1000).toFixed(1);

  console.log(`Done in ${elapsed}s — success: ${success}, failed: ${failed}`);
  process.exitCode = failed > 0 ? 1 : 0;
}

main().catch((err) => {
  console.error("Fatal error:", err);
  process.exitCode = 1;
});
