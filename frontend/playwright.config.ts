import { defineConfig, devices } from '@playwright/test';

/**
 * Browser E2E + accessibility tests (Phase 11).
 *
 * Prerequisite: the backend runs locally with the sample data (cd backend && mvnw spring-boot:test-run,
 * then the seed script). Playwright starts (or reuses) the Vite dev server on :5173 itself.
 *
 * Runs serially with one worker: the backend rate-limits logins (10 per email per 5 min) and
 * revokes a whole session if a rotated refresh token is reused, so signed-in tests share one login.
 */
export default defineConfig({
  testDir: './e2e',
  globalSetup: './e2e/global-setup.ts',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
    timeout: 60_000,
  },
});
