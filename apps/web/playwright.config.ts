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

import { defineConfig, devices } from '@playwright/test'

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
    ['html', { outputFolder: 'test-results/html' }],
    ['junit', { outputFolder: 'test-results/junit' }],
  ],
  use: {
    baseURL: 'http://localhost://3000', // Will be overridden by test runner
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    viewport: { width: 1280, height: 720 },
    // Local demo mode is enabled via environment variable injected by the test server
    // VITE_LOCAL_MODE=true is set when the Vite dev server starts with this env var
  },
  // Project-specific configurations
  projects: [
    // Layer A: Local Demo Mode tests
    {
      name: 'Layer A: Local Demo',
      testMatch: '**/*.e2e.ts',
      grep: /@localDemo|@demoAccount|@layerA/,
      // Dependencies are resolved at test runtime; see test runner scripts
    },
  ],
})

// Configure test environment for local demo mode
// The VITE_LOCAL_MODE=true is injected when the Vite dev server starts with this env var
// See scripts/test-runner.ps1 for the complete launch configuration