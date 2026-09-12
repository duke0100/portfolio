#!/usr/bin/env node

// Imports N random users into project2's User API.
// Usage: node import-users.js [count] [baseUrl] [concurrency]
// Example: node import-users.js 1000000 http://localhost:8082 50

const TOTAL = parseInt(process.argv[2] || "1000000", 10);
const BASE_URL = process.argv[3] || "http://localhost:8082";
const CONCURRENCY = parseInt(process.argv[4] || "50", 10);

const USERS_URL = `${BASE_URL}/api/v1/users`;

const FIRST_NAMES = [
  "James", "Mary", "John", "Patricia", "Robert", "Jennifer", "Michael", "Linda",
  "William", "Elizabeth", "David", "Barbara", "Richard", "Susan", "Joseph", "Jessica",
  "Thomas", "Sarah", "Charles", "Karen",
];

const LAST_NAMES = [
  "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis",
  "Rodriguez", "Martinez", "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson",
  "Thomas", "Taylor", "Moore", "Jackson", "Martin",
];

const GENDERS = ["MALE", "FEMALE", "OTHER"];

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

function randomDateOfBirth() {
  const start = new Date(1960, 0, 1).getTime();
  const end = new Date(2005, 11, 31).getTime();
  const date = new Date(start + Math.random() * (end - start));
  return date.toISOString().slice(0, 10);
}

function randomPhoneNumber() {
  return `+1${randomInt(200, 999)}${randomInt(200, 999)}${randomInt(1000, 9999)}`;
}

function buildUser(index) {
  const firstName = randomItem(FIRST_NAMES);
  const lastName = randomItem(LAST_NAMES);
  const username = `${firstName}.${lastName}.${index}`.toLowerCase();
  return {
    username,
    email: `${username}@example.com`,
    password: `Passw0rd-${index}`,
    firstName,
    lastName,
    phoneNumber: randomPhoneNumber(),
    address: `${randomInt(1, 9999)} ${randomItem(STREETS)}, ${randomItem(CITIES)}`,
    dateOfBirth: randomDateOfBirth(),
    gender: randomItem(GENDERS),
    avatarUrl: `https://picsum.photos/seed/user-${index}/200/200`,
  };
}

async function createUser(user) {
  const response = await fetch(USERS_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(user),
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
  console.log(`Importing ${TOTAL} random users into ${USERS_URL} (concurrency: ${CONCURRENCY})`);

  const users = Array.from({ length: TOTAL }, (_, i) => buildUser(i + 1));
  const start = Date.now();
  const { success, failed } = await runBatch(users, createUser);
  const elapsed = ((Date.now() - start) / 1000).toFixed(1);

  console.log(`Done in ${elapsed}s — success: ${success}, failed: ${failed}`);
  process.exitCode = failed > 0 ? 1 : 0;
}

main().catch((err) => {
  console.error("Fatal error:", err);
  process.exitCode = 1;
});
