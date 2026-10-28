<#===============================================================================
UEP Layer A E2E Test Runner - Local Demo Mode
===============================================================================
Run Playwright E2E tests against the UEP application in local/demo mode.

Prerequisites (must be installed before running):
  - Node.js >= 22.22.0
  - npm >= 10.x (or pnpm)
  - Playwright browsers: npx playwright install
  - Vite dev server dependencies

Configuration:
  - VITE_LOCAL_MODE=true must be set when starting the Vite dev server
  - The app serves at http://localhost:3000 (default baseURL)
  - All 13 demo personas use password "demo"
  - Base URL is configured in playwright.config.ts

Test Organization:
  - Tests are in apps/web/test/e2e/*.e2e.ts
  - Test config: apps/web/playwright.config.ts
  - Reports generated in test-results/ directory

Usage:
  # Step 1: Install dependencies (once)
  npm install

  # Step 2: Install Playwright browsers
  npx playwright install

  # Step 3: Start the Vite dev server with local mode
  set VITE_LOCAL_MODE=true
  set VITE_KEYCLOAK_URL=http://localhost:8080  # optional, if Keycloak available
  set VITE_KEYCLOAK_REALM=education
  set VITE_KEYCLOAK_CLIENT_id=platform-web
  vite --mode local

  # Step 4: Run the E2E tests
  .\scripts\test-runner.ps1

  # Or run specific test files:
  npx playwright test test/e2e\golden-path.e2e.ts
  npx playwright test test/e2e\attendance.e2e.ts

  # Run with HTML report
  npx playwright test --reporter=html

  # View report
  npx playwright test --output=test-results/html
===============================================================================>