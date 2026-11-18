import { defineConfig } from '@playwright/test';
import prod from './playwright.prod.config.ts';

/**
 * Production topology test: the SPA (localhost:5173) calls the API on ANOTHER origin of the same
 * site (localhost:8080), like songbook.example.org -> api.songbook.example.org. The preview server
 * has no /api proxy in this mode, so CORS, the CSP's API origin, cross-origin PDF ranges and the
 * SameSite=Strict refresh cookie are all exercised for real.
 * Run: npm run test:e2e:cross-origin
 */
export default defineConfig({
  ...prod,
  webServer: { ...prod.webServer, command: 'npx vite preview --mode crossorigin' } as typeof prod.webServer,
});
