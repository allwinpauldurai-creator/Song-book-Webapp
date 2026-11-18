// Post-deployment smoke test. Read-only: no login, no data changes.
// Usage: node scripts/smoke-test.mjs <frontendOrigin> <apiOrigin>
//   e.g. node scripts/smoke-test.mjs https://songbook.example.org https://api.songbook.example.org
// Exit code 1 if any check fails.
const [frontend, api] = process.argv.slice(2).map((s) => s?.replace(/\/+$/, ''));
if (!frontend || !api) {
  console.error('Usage: node scripts/smoke-test.mjs <frontendOrigin> <apiOrigin>');
  process.exit(2);
}

let failed = 0;
async function check(name, fn) {
  try {
    const detail = await fn();
    console.log(`PASS  ${name}${detail ? ` (${detail})` : ''}`);
  } catch (e) {
    failed++;
    console.log(`FAIL  ${name}: ${e.message}`);
  }
}
const expect = (cond, msg) => {
  if (!cond) throw new Error(msg);
};
async function get(url, init) {
  const res = await fetch(url, { redirect: 'manual', ...init });
  return { res, text: await res.text() };
}

await check('API readiness', async () => {
  const { res, text } = await get(`${api}/actuator/health/readiness`);
  expect(res.status === 200 && text.includes('"UP"'), `status ${res.status}: ${text.slice(0, 120)}`);
});

await check('API catalogue', async () => {
  const { res, text } = await get(`${api}/api/v1/books?size=1`);
  expect(res.status === 200, `status ${res.status}`);
  const body = JSON.parse(text);
  expect(Array.isArray(body.content), 'no content array');
  return `${body.totalElements} public book(s)`;
});

await check('API security headers', async () => {
  const { res } = await get(`${api}/api/v1/categories`);
  expect(res.headers.get('x-content-type-options') === 'nosniff', 'missing nosniff');
  expect(res.headers.get('x-frame-options') === 'DENY', 'missing X-Frame-Options DENY');
});

await check('API docs disabled in production', async () => {
  const { res } = await get(`${api}/v3/api-docs`);
  expect(res.status !== 200, `status ${res.status} (Swagger/OpenAPI must be off)`);
});

await check('CORS allows the frontend with credentials (refresh cookie)', async () => {
  const res = await fetch(`${api}/api/v1/auth/refresh`, {
    method: 'OPTIONS',
    headers: { Origin: frontend, 'Access-Control-Request-Method': 'POST' },
  });
  expect(res.headers.get('access-control-allow-origin') === frontend, `allow-origin = ${res.headers.get('access-control-allow-origin')}`);
  expect(res.headers.get('access-control-allow-credentials') === 'true', 'credentials not allowed');
});

await check('CORS rejects other origins', async () => {
  const res = await fetch(`${api}/api/v1/auth/refresh`, {
    method: 'OPTIONS',
    headers: { Origin: 'https://evil.example', 'Access-Control-Request-Method': 'POST' },
  });
  expect(!res.headers.get('access-control-allow-origin'), 'foreign origin was allowed');
});

await check('Frontend index + CSP', async () => {
  const { res, text } = await get(`${frontend}/`);
  expect(res.status === 200 && text.includes('id="root"'), `status ${res.status}`);
  const csp = res.headers.get('content-security-policy') ?? '';
  expect(csp.includes(`connect-src 'self' ${api}`), `CSP does not allow the API origin: ${csp}`);
  expect(csp.includes("frame-ancestors 'none'"), 'CSP missing frame-ancestors');
});

await check('Frontend SPA deep link', async () => {
  const { res, text } = await get(`${frontend}/books`);
  expect(res.status === 200 && text.includes('id="root"'), `status ${res.status}`);
});

await check('PDF.js decoders served as WebAssembly', async () => {
  const res = await fetch(`${frontend}/pdfjs/wasm/jbig2.wasm`);
  expect(res.status === 200, `status ${res.status}`);
  expect(res.headers.get('content-type')?.startsWith('application/wasm'), `content-type ${res.headers.get('content-type')}`);
});

console.log(failed ? `\n${failed} check(s) FAILED` : '\nAll smoke checks passed');
process.exit(failed ? 1 : 0);
