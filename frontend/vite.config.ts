import fs from 'node:fs';
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import pdfjsAssets from './vite-plugin-pdfjs-assets.ts';

// Dev: the SPA and the API share one origin (localhost:5173) through this proxy, so the
// SameSite=Strict refresh cookie and the backend's Origin check work without extra CORS setup.
// Port 5173 is fixed because the backend's email links and CORS allow-list point at it.
const apiProxy = { '/api': { target: 'http://localhost:8080', changeOrigin: true } };

export default defineConfig(({ mode }) => ({
  plugins: [react(), pdfjsAssets()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: apiProxy,
  },
  // `vite preview` serves the production build with the same security headers as Static Web Apps
  // (from dist/staticwebapp.config.json), so the browser suite can verify the real CSP locally.
  //  - default mode (npm run test:e2e:prod): /api is proxied, i.e. same origin.
  //  - --mode crossorigin (npm run test:e2e:cross-origin): NO proxy; the SPA must call the API at
  //    VITE_API_BASE_URL (.env.crossorigin), like production. A request that forgets the base URL fails.
  preview: {
    port: 5173,
    strictPort: true,
    headers: swaHeaders(),
    proxy: mode === 'crossorigin' ? {} : apiProxy,
  },
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'], // e2e/*.spec.ts are Playwright tests
  },
}));

function swaHeaders(): Record<string, string> | undefined {
  try {
    return JSON.parse(fs.readFileSync('dist/staticwebapp.config.json', 'utf8')).globalHeaders;
  } catch {
    return undefined;
  }
}
