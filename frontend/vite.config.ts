import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import pdfjsAssets from './vite-plugin-pdfjs-assets.ts';

// Dev: the SPA and the API share one origin (localhost:5173) through this proxy, so the
// SameSite=Strict refresh cookie and the backend's Origin check work without extra CORS setup.
// Port 5173 is fixed because the backend's email links and CORS allow-list point at it.
export default defineConfig({
  plugins: [react(), pdfjsAssets()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'], // e2e/*.spec.ts are Playwright tests
  },
});
