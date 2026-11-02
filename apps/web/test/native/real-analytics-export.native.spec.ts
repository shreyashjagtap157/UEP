import { expect, test } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

const frontend = 'http://localhost:5173'
const keycloakIssuer = 'http://localhost:8081/realms/education'

type LocalBootstrapState = {
  LocalAdminUsername?: unknown
  LocalAdminPassword?: unknown
}

type CurrentIdentity = {
  tenantId: string
  membershipStatus: string
  roles: string[]
  permissions: string[]
}

function loadLocalOwnerCredentials(): { username: string; password: string } {
  const localAppData = process.env.LOCALAPPDATA
  if (!localAppData) {
    throw new Error('Native Windows test requires the same Windows user profile that initialized UEP.')
  }

  let state: LocalBootstrapState
  try {
    const statePath = join(localAppData, 'UEP', 'local-native', 'state.json')
    state = JSON.parse(readFileSync(statePath, 'utf8').replace(/^\uFEFF/, '')) as LocalBootstrapState
  } catch {
    throw new Error('Protected local UEP bootstrap state is unavailable. Start Real mode under this Windows user first.')
  }

  if (
    typeof state.LocalAdminUsername !== 'string' ||
    !state.LocalAdminUsername ||
    typeof state.LocalAdminPassword !== 'string' ||
    !state.LocalAdminPassword
  ) {
    throw new Error('Protected local UEP owner credentials are missing. Re-run the native launcher without displaying the state file.')
  }

  // Credentials remain in process memory only. Never log, attach or persist them.
  return { username: state.LocalAdminUsername, password: state.LocalAdminPassword }
}

test('real OIDC owner exports a database-backed analytics report through the authenticated Vite proxy', async ({ page, request }) => {
  const credentials = loadLocalOwnerCredentials()
  const browserApiOrigins = new Set<string>()

  page.on('request', request => {
    const url = new URL(request.url())
    if (url.pathname.startsWith('/api/')) browserApiOrigins.add(url.origin)
  })

  const identityResponsePromise = page.waitForResponse(response => {
    const url = new URL(response.url())
    return url.origin === frontend
      && url.pathname === '/api/v1/me'
      && response.request().method() === 'GET'
  })

  await page.goto(frontend)
  const username = page.locator('input[name="username"]')
  const password = page.locator('input[name="password"]')
  await expect(username, 'Real mode must redirect to the local Keycloak login form').toBeVisible()
  await username.fill(credentials.username)
  await password.fill(credentials.password)
  await page.locator('#kc-login').click()

  const identityResponse = await identityResponsePromise
  expect(identityResponse.status()).toBe(200)
  const identity = await identityResponse.json() as CurrentIdentity
  expect(identity.membershipStatus).toBe('ACTIVE')
  expect(identity.roles).toContain('Organization Owner')
  expect(identity.permissions).toContain('ANALYTICS_VIEW')
  expect(identity.permissions).toContain('REPORTS_EXPORT')
  expect(identity.tenantId).toBeTruthy()
  await expect(page.locator('.app-frame')).toBeVisible()

  const overviewPromise = page.waitForResponse(response => {
    const url = new URL(response.url())
    return url.origin === frontend
      && url.pathname === '/api/v1/analytics/overview'
      && response.request().method() === 'GET'
  })
  await page.getByRole('button', { name: 'Analytics & Operations' }).click()
  const overviewResponse = await overviewPromise
  expect(overviewResponse.status()).toBe(200)

  const csvResponsePromise = page.waitForResponse(response => {
    const url = new URL(response.url())
    return url.origin === frontend
      && url.pathname === '/api/v1/analytics/exports/attendance'
      && response.request().method() === 'GET'
  })
  const downloadPromise = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Attendance CSV' }).click()
  const [csvResponse, download] = await Promise.all([csvResponsePromise, downloadPromise])

  expect(csvResponse.status()).toBe(200)
  expect(csvResponse.headers()['content-type']).toContain('text/csv')
  expect(await csvResponse.text()).toContain('present,partial,absent,excused,present_seconds')
  expect(download.suggestedFilename()).toBe('uep-attendance-report.csv')

  const requestHeaders = await csvResponse.request().allHeaders()
  // Assert only the presence of a bearer header; never include the token in test output.
  expect(requestHeaders.authorization?.startsWith('Bearer ') === true).toBe(true)
  expect([...browserApiOrigins]).toEqual([frontend])

  const discovery = await request.get(keycloakIssuer + '/.well-known/openid-configuration')
  expect(discovery.status()).toBe(200)
  const metadata = await discovery.json() as { issuer?: string }
  expect(metadata.issuer).toBe(keycloakIssuer)
})
