import { expect, test } from '@playwright/test'
import { existsSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'

const frontend = 'http://localhost:5173'
const keycloakIssuer = 'http://localhost:8081/realms/education'

type LocalBootstrapState = {
  LocalAdminUsername?: unknown
  LocalAdminPassword?: unknown
  DatabaseProvider?: unknown
  SQLiteDatabasePath?: unknown
}
type CurrentIdentity = { tenantId: string; membershipStatus: string; roles: string[] }

function localState(): LocalBootstrapState {
  const root = process.env.LOCALAPPDATA
  if (!root) throw new Error('SQLite native qualification requires the initializing Windows user profile.')
  const path = join(root, 'UEP', 'local-native', 'state.json')
  return JSON.parse(readFileSync(path, 'utf8').replace(/^\uFEFF/, '')) as LocalBootstrapState
}

test('SQLite mode: OIDC login, authenticated branch write/readback and durable SQLite file', async ({ page, request }) => {
  const state = localState()
  test.skip(state.DatabaseProvider !== 'SQLite', 'SQLite native checks run only in SQLite qualification mode')
  expect(typeof state.LocalAdminUsername).toBe('string')
  expect(typeof state.LocalAdminPassword).toBe('string')
  expect(typeof state.SQLiteDatabasePath).toBe('string')
  const usernameValue = state.LocalAdminUsername as string
  const passwordValue = state.LocalAdminPassword as string
  const databasePath = state.SQLiteDatabasePath as string

  const identityPromise = page.waitForResponse(response => {
    const url = new URL(response.url())
    return url.origin === frontend && url.pathname === '/api/v1/me' && response.request().method() === 'GET'
  })
  const authorizationPromise = page.waitForRequest(request => {
    const url = new URL(request.url())
    return url.origin === 'http://localhost:8081'
      && url.pathname === '/realms/education/protocol/openid-connect/auth'
  })
  await page.goto(frontend)
  const authorization = await authorizationPromise
  const authorizationUrl = new URL(authorization.url())
  expect(authorizationUrl.searchParams.get('client_id')).toBe('platform-web')
  expect(authorizationUrl.searchParams.get('response_type')).toBe('code')
  expect(authorizationUrl.searchParams.get('code_challenge_method')).toBe('S256')
  expect(authorizationUrl.searchParams.get('code_challenge')).toBeTruthy()

  await expect(page.locator('input[name="username"]')).toBeVisible()
  await page.locator('input[name="username"]').fill(usernameValue)
  await page.locator('input[name="password"]').fill(passwordValue)
  await page.locator('#kc-login').click()
  const identityResponse = await identityPromise
  expect(identityResponse.status()).toBe(200)
  const identity = await identityResponse.json() as CurrentIdentity
  expect(identity.membershipStatus).toBe('ACTIVE')
  expect(identity.roles).toContain('Organization Owner')
  expect(identity.tenantId).toBeTruthy()
  await expect(page.locator('.app-frame')).toBeVisible()

  const discovery = await request.get(keycloakIssuer + '/.well-known/openid-configuration')
  expect(discovery.status()).toBe(200)
  expect((await discovery.json() as { issuer?: string }).issuer).toBe(keycloakIssuer)

  await page.getByRole('button', { name: 'Organization', exact: true }).click()
  const branchCode = 'SQLITE-E2E-' + Date.now().toString()
  const branchName = 'SQLite persistence qualification ' + branchCode
  await page.getByLabel('Code').fill(branchCode)
  await page.getByLabel('Display name').fill(branchName)
  await page.getByLabel('Timezone').fill('Asia/Kolkata')
  const createPromise = page.waitForResponse(response => {
    const url = new URL(response.url())
    return url.origin === frontend && url.pathname === '/api/v1/organization/branches'
      && response.request().method() === 'POST'
  })
  await page.getByRole('button', { name: 'Create branch', exact: true }).click()
  const createResponse = await createPromise
  expect(createResponse.status()).toBe(200)
  const branch = await createResponse.json() as { id: string; code: string; displayName: string; status: string }
  expect(branch.code).toBe(branchCode)
  expect(branch.displayName).toBe(branchName)
  expect(branch.status).toBe('ACTIVE')

  const reloadIdentity = page.waitForResponse(response => {
    const url = new URL(response.url())
    return url.origin === frontend && url.pathname === '/api/v1/me' && response.request().method() === 'GET'
  })
  await page.reload()
  expect((await reloadIdentity).status()).toBe(200)
  await expect(page.locator('.app-frame')).toBeVisible()
  const branchListPromise = page.waitForResponse(response => {
    const url = new URL(response.url())
    return url.origin === frontend && url.pathname === '/api/v1/organization/branches'
      && response.request().method() === 'GET'
  })
  await page.getByRole('button', { name: 'Organization', exact: true }).click()
  const branchListResponse = await branchListPromise
  expect(branchListResponse.status()).toBe(200)
  const branchList = await branchListResponse.json() as {
    items: Array<{ id: string; code: string; displayName: string; status: string }>
  }
  expect(branchList.items).toContainEqual(expect.objectContaining({
    id: branch.id, code: branchCode, displayName: branchName, status: 'ACTIVE',
  }))

  expect(existsSync(databasePath)).toBe(true)
  expect(statSync(databasePath).size).toBeGreaterThan(100)
  expect(readFileSync(databasePath).subarray(0, 16).toString('binary')).toBe('SQLite format 3\u0000')
})
