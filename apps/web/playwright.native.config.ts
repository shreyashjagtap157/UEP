import { defineConfig } from '@playwright/test'

// This suite validates the already-running native Real environment.
// It deliberately does not start Vite, Keycloak or Spring Boot itself.
export default defineConfig({
  testDir: './test/native',
  testMatch: '**/*.native.spec.ts',
  timeout: 90_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: 'list',
  outputDir: 'e2e-native-results/test-output',
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'off',
    screenshot: 'off',
    video: 'off',
    viewport: { width: 1440, height: 900 },
  },
})
