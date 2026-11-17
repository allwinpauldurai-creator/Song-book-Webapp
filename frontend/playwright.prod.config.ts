import { defineConfig } from '@playwright/test';
import base from './playwright.config.ts';

/**
 * Same tests against the PRODUCTION build served by `vite preview`, with the Static Web Apps
 * security headers (incl. Content-Security-Policy) from dist/staticwebapp.config.json.
 * Run: npm run test:e2e:prod (builds, writes the config, then runs this).
 */
export default defineConfig({
  ...base,
  outputDir: 'test-results-prod',
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report-prod' }]],
  webServer: {
    command: 'npx vite preview',
    url: 'http://localhost:5173',
    reuseExistingServer: false, // never reuse a dev server: this run must test the build + headers
    timeout: 60_000,
  },
});
