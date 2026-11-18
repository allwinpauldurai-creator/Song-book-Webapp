// Run from the repo root against a running backend: node scripts/perf-load-test.mjs http://localhost:8080 20
// Uses the dev admin account; each read-access call writes an audit row.
// Load test for NFR-07 (catalogue p95 < 500 ms). Hits the API directly (no dev proxy).
// Usage: node scripts/perf-load-test.mjs [baseUrl] [concurrency]   (defaults: http://localhost:8080 20)
const API = (process.argv[2] ?? 'http://localhost:8080') + '/api/v1';
const CONCURRENCY = Number(process.argv[3] ?? 20);

const token = (await (await fetch(`${API}/auth/login`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ mobile: '+919999999999', password: 'ChangeMe-Admin-123' }),
})).json()).accessToken;
const auth = { Authorization: `Bearer ${token}` };
const books = (await (await fetch(`${API}/books?size=20&sort=title,asc`)).json()).content;
const book = books[0];
const coverPath = new URL(book.coverUrl, 'http://x').pathname + new URL(book.coverUrl, 'http://x').search;

async function run(name, total, makeRequest) {
  const times = [];
  let errors = 0;
  let next = 0;
  const started = performance.now();
  await Promise.all(Array.from({ length: CONCURRENCY }, async () => {
    while (next < total) {
      next++;
      const t0 = performance.now();
      try {
        const res = await makeRequest();
        await res.arrayBuffer();
        if (!res.ok) errors++;
      } catch { errors++; }
      times.push(performance.now() - t0);
    }
  }));
  const secs = (performance.now() - started) / 1000;
  times.sort((a, b) => a - b);
  const pct = (p) => times[Math.min(times.length - 1, Math.floor((p / 100) * times.length))].toFixed(0);
  const row = { endpoint: name, requests: times.length, errors, 'req/s': (times.length / secs).toFixed(0), p50: pct(50), p95: pct(95), p99: pct(99), max: times.at(-1).toFixed(0) };
  console.log(Object.values(row).map((v, i) => String(v).padEnd(i === 0 ? 34 : 9)).join(''));
  return row;
}

console.log(`concurrency ${CONCURRENCY}; times in ms`);
console.log(['endpoint', 'requests', 'errors', 'req/s', 'p50', 'p95', 'p99', 'max'].map((v, i) => v.padEnd(i === 0 ? 34 : 9)).join(''));
// warm-up (JIT, connection pool)
await run('(warm-up) GET /books', 200, () => fetch(`${API}/books?size=20`));

const rows = [];
rows.push(await run('GET /books?size=20 (page 1)', 2000, () => fetch(`${API}/books?size=20&sort=title,asc`)));
rows.push(await run('GET /books?q=hymn (search)', 2000, () => fetch(`${API}/books?q=hymn&size=20`)));
rows.push(await run('GET /books?categoryId= (filter)', 1000, () => fetch(`${API}/books?categoryId=${book.category.id}`)));
rows.push(await run('GET /books/{id} (detail)', 2000, () => fetch(`${API}/books/${book.id}`)));
rows.push(await run('GET /categories', 2000, () => fetch(`${API}/categories`)));
rows.push(await run('GET /books/{id}/cover (image)', 1000, () => fetch(`http://localhost:8080${coverPath}`)));
rows.push(await run('GET /users/me (auth)', 1000, () => fetch(`${API}/users/me`, { headers: auth })));
rows.push(await run('GET read-access (auth, audited)', 200, () => fetch(`${API}/books/${book.id}/read-access`, { headers: auth })));
const grant = await (await fetch(`${API}/books/${book.id}/read-access`, { headers: auth })).json();
rows.push(await run('GET content Range 64 KB (ticket)', 500, () =>
  fetch(`http://localhost:8080${grant.contentUrl}`, { headers: { Range: 'bytes=0-65535' } })));

const catalogue = rows.filter((r) => /books|categories/.test(r.endpoint) && !/content|read-access|cover/.test(r.endpoint));
const worst = Math.max(...catalogue.map((r) => Number(r.p95)));
console.log(`\nNFR-07 catalogue p95 < 500 ms: worst catalogue p95 = ${worst} ms -> ${worst < 500 ? 'PASS' : 'FAIL'}`);
console.log(`Errors total: ${rows.reduce((n, r) => n + r.errors, 0)}`);
