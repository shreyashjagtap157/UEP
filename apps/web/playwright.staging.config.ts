import { defineConfig } from '@playwright/test'

type StagingTestAccount = {
  username: string
  password: string
  roleName: string
  displayName: string
  tenantId: string
}

const baseUrl = requiredHttpsUrl('UEP_E2E_BASE_URL')
const backendUrl = requiredHttpsUrl('UEP_E2E_BACKEND_URL')
const issuerUrl = requiredHttpsUrl('UEP_E2E_KEYCLOAK_ISSUER')

if (process.env.VITE_LOCAL_MODE === 'true') {
  throw new Error('Staging E2E refuses VITE_LOCAL_MODE=true.')
}

const requiredRoles = [
  ['organization_owner', 'Organization Owner'],
  ['organization_administrator', 'Organization Administrator'],
  ['branch_administrator', 'Branch Administrator'],
  ['academic_administrator', 'Academic Administrator'],
  ['exam_controller', 'Exam Controller'],
  ['finance_administrator', 'Finance Administrator'],
  ['teacher', 'Teacher'],
  ['evaluator', 'Evaluator'],
  ['teaching_assistant', 'Teaching Assistant'],
  ['mentor', 'Mentor'],
  ['student', 'Student'],
  ['guardian', 'Guardian'],
  ['support_operator', 'Support Operator'],
  ['auditor', 'Auditor'],
] as const

const accounts = loadAccounts()

function requiredHttpsUrl(name: string): string {
  const raw = process.env[name]?.trim()
  if (!raw) throw new Error(`${name} is required for remote staging E2E.`)
  const parsed = new URL(raw)
  if (parsed.protocol !== 'https:' || ['localhost', '127.0.0.1', '::1'].includes(parsed.hostname)) {
    throw new Error(`${name} must be a public HTTPS URL, not a local/demo target.`)
  }
  return parsed.toString().replace(/\/$/, '')
}

function loadAccounts(): Record<string, StagingTestAccount> {
  const raw = process.env.UEP_E2E_TEST_ACCOUNTS_JSON
  if (!raw) throw new Error('UEP_E2E_TEST_ACCOUNTS_JSON is required; supply the 14-role staging identity matrix as a GitHub Environment secret.')
  let parsed: unknown
  try {
    parsed = JSON.parse(raw)
  } catch {
    throw new Error('UEP_E2E_TEST_ACCOUNTS_JSON must be valid JSON.')
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error('UEP_E2E_TEST_ACCOUNTS_JSON must be a JSON object keyed by system role.')
  }
  const result = parsed as Record<string, unknown>
  const missing: string[] = []
  for (const [key, roleName] of requiredRoles) {
    const account = result[key]
    if (!account || typeof account !== 'object' || Array.isArray(account)) {
      missing.push(key)
      continue
    }
    const candidate = account as Partial<StagingTestAccount>
    if (
      !candidate.username?.trim() ||
      !candidate.password ||
      candidate.roleName !== roleName ||
      !candidate.displayName?.trim() ||
      !candidate.tenantId?.trim()
    ) {
      missing.push(key)
    }
  }
  if (missing.length) {
    throw new Error(`Staging identity matrix is incomplete or invalid for roles: ${missing.join(', ')}.`)
  }
  return result as Record<string, StagingTestAccount>
}

export { baseUrl, backendUrl, issuerUrl, requiredRoles, accounts }

export default defineConfig({
  testDir: './test/staging',
  testMatch: '**/*.staging.spec.ts',
  timeout: 60_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [
    ['list'],
    ['html', { outputFolder: 'e2e-staging-results/html' }],
    ['junit', { outputFile: 'e2e-staging-results/junit/results.xml' }],
  ],
  outputDir: 'e2e-staging-results/test-output',
  use: {
    baseURL: baseUrl,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    viewport: { width: 1440, height: 900 },
  },
})
