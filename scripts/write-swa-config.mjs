// Writes the Azure Static Web Apps config (security headers, CSP, SPA routing) into a build folder.
// The CSP depends on the API origin, so it is generated per environment at deploy time.
//
// Usage: node scripts/write-swa-config.mjs <apiOrigin|same-origin> <outFile>
//   e.g. node scripts/write-swa-config.mjs https://api.songbook.example.org frontend/dist/staticwebapp.config.json
//        node scripts/write-swa-config.mjs same-origin frontend/dist/staticwebapp.config.json   (local preview tests)
import fs from 'node:fs';

const [apiArg, outFile] = process.argv.slice(2);
if (!apiArg || !outFile) {
  console.error('Usage: node scripts/write-swa-config.mjs <apiOrigin|same-origin> <outFile>');
  process.exit(2);
}
const sameOrigin = apiArg === 'same-origin';
// http://localhost:<port> is accepted only to test the cross-origin production topology locally.
const localTest = /^http:\/\/localhost:\d+$/.test(apiArg);
if (!sameOrigin && !localTest && !/^https:\/\/[a-z0-9.-]+(:\d+)?$/i.test(apiArg)) {
  console.error(`API origin must look like https://api.example.org (got "${apiArg}")`);
  process.exit(2);
}
const api = sameOrigin ? '' : ` ${apiArg}`;

// Why each source is needed:
//  script-src 'wasm-unsafe-eval'   PDF.js compiles its JBIG2/JPEG 2000 WebAssembly decoders (no JS eval)
//  style-src 'unsafe-inline'       MUI/Emotion inject <style> tags at runtime
//  img-src  <api> data: blob:      book covers come from the API; MUI/PDF.js use data:/blob: images
//  connect-src <api>               JSON API, PDF ranges, admin uploads/downloads
//  worker-src 'self' blob:         PDF.js worker (same origin); blob: for its internal workers
const csp = [
  "default-src 'self'",
  "script-src 'self' 'wasm-unsafe-eval'",
  "style-src 'self' 'unsafe-inline'",
  `img-src 'self' data: blob:${api}`,
  "font-src 'self' data:",
  `connect-src 'self'${api}`,
  "worker-src 'self' blob:",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  "frame-ancestors 'none'",
  ...(sameOrigin || localTest ? [] : ['upgrade-insecure-requests']),
].join('; ');

const config = {
  navigationFallback: {
    rewrite: '/index.html',
    exclude: ['/assets/*', '/pdfjs/*', '/favicon.svg', '/staticwebapp.config.json'],
  },
  globalHeaders: {
    'Content-Security-Policy': csp,
    'X-Content-Type-Options': 'nosniff',
    'X-Frame-Options': 'DENY',
    'Referrer-Policy': 'no-referrer',
    'Permissions-Policy': 'camera=(), microphone=(), geolocation=(), payment=(), usb=()',
    'Cross-Origin-Opener-Policy': 'same-origin',
    ...(sameOrigin || localTest ? {} : { 'Strict-Transport-Security': 'max-age=31536000; includeSubDomains' }),
  },
  routes: [
    // Hashed build assets never change: cache forever. index.html must always be revalidated.
    { route: '/assets/*', headers: { 'Cache-Control': 'public, max-age=31536000, immutable' } },
    { route: '/pdfjs/*', headers: { 'Cache-Control': 'public, max-age=604800' } },
    { route: '/index.html', headers: { 'Cache-Control': 'no-cache' } },
  ],
  mimeTypes: {
    '.wasm': 'application/wasm',
    '.mjs': 'text/javascript',
    '.bcmap': 'application/octet-stream',
    '.pfb': 'application/octet-stream',
    '.icc': 'application/vnd.iccprofile',
  },
};

fs.writeFileSync(outFile, JSON.stringify(config, null, 2) + '\n');
console.log(`Wrote ${outFile} (API: ${sameOrigin ? 'same origin' : apiArg})`);
