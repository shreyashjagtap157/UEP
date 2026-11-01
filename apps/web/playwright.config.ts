/**
 * Playwright Configuration for UEP Role-Based E2E Tests
 *
 * Layer A: Local Demo Mode (VITE_LOCAL_MODE=true)
 * Tests interact with the rendered React application in a real browser,
 * exercising the local/demo workspace with 13 seeded personas.
 *
 * Prerequisites for execution:
 * 1. npm install (or pnpm install) to install dependencies
 * 2. Node.js >= 22.22.0
 * 3. Playwright browsers installed: npx playwright install
 * 4. Vite dev server running with VITE_LOCAL_MODE=true
 *
 * See: docs/DEMO_INTERACTION_MATRIX.md for test-to-role mapping
 */

import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './test/e2e',
  timeout: 30000,
  expect: {
    timeout: 5000,
  },
  retries: 0,
  fullyParallel: true,
  workers: 4,
  reporter: [
    ['list'],
    ['html', { outputFolder: 'e2e-test-results/html' }],
    ['junit', { outputFolder: 'e2e-test-results/junit' }],
  ],
  // Top-level webServer configuration (NOT inside 'use')
  // Playwright manages the Vite server lifecycle reliably
  webServer: {
    command: 'npm run dev',
    url: 'http://127.0.0.1:5173',
    timeout: 120000,
    reuseExistingServer: !process.env.CI,
    env: {
      VITE_LOCAL_MODE: 'true',
    },
  },
  use: {
    baseURL: 'http://127.0.0.1:5173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    viewport: { width: 1280, height: 720 },
  },
  // Project-specific configurations
  projects: [
    // Layer A: Local Demo Mode tests
    {
      name: 'Layer A: Local Demo',
      testMatch: '**/*.e2e.ts',
    },
    // Layer B: Remote Staging acceptance suite
    {
      name: 'Layer B: Remote Staging',
      testMatch: '**/*.e2e.ts',
      use: {
        baseURL: process.env.UEP_E2E_BASE_URL,
        trace: 'on-first-retry',
        screenshot: 'only-on-failure',
        viewport: { width: 1280, height: 700 },
        actionTimeout: 30000,
        navigationTimeout: 60000,
        // Fail closed if local mode is enabled or base URL is localhost
        ignoreHTTPSErrors: true,
        // Environment validation — these are checked at test runtime
        // VITE_LOCAL_MODE must be 'false' for remote execution
      },
      grep: /production|staging/i,
    },
  ],
})
