import { expect, test } from '@playwright/test'
import { closeSync, existsSync, openSync, readSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'

const frontend = 'http://127.0.0.1:5173'
type LocalBootstrapState = {
  DatabaseProvider?: unknown
  SQLiteDatabasePath?: unknown
}
type CurrentIdentity = { oidcSubject: string; displayName: string; tenantId: string; membershipStatus: string; roles: string[] }

function localState(): LocalBootstrapState {
  const root = process.env.LOCALAPPDATA
  if (!root) throw new Error('SQLite native qualification requires the initializing Windows user profile.')
  const path = join(root, 'UEP', 'local-native', 'state.json')
  return JSON.parse(readFileSync(path, 'utf8').replace(/^\uFEFF/, '')) as LocalBootstrapState
}

test('SQLite mode: local-authenticated Real UI, API write/readback and durable SQLite file', async ({ page, request }) => {
  const state = localState()
  test.skip(state.DatabaseProvider !== 'SQLite', 'SQLite native checks run only in SQLite qualification mode')
  expect(typeof state.SQLiteDatabasePath).toBe('string')
  const databasePath = state.SQLiteDatabasePath as string
  const keycloakRequests: string[] = []
  page.on('request', browserRequest => {
    const url = new URL(browserRequest.url())
    if (url.port === '8081' || url.pathname.includes('/realms/education')) keycloakRequests.push(url.origin + url.pathname)
  })

  const directBackendAnonymous = await request.get('http://127.0.0.1:8080/api/v1/me')
  expect(directBackendAnonymous.status()).toBe(401)
  const identityPromise = page.waitForResponse(response => {
    const url = new URL(response.url())
    return url.origin === frontend && url.pathname === '/api/v1/me' && response.request().method() === 'GET'
  })
  await page.goto(frontend)
  const identityResponse = await identityPromise
  expect(identityResponse.status()).toBe(200)
  const identityHeaders = await identityResponse.request().allHeaders()
  expect(identityHeaders['x-uep-local-auth']).toBeUndefined()
  expect(identityHeaders.authorization).toBeUndefined()
  const identity = await identityResponse.json() as CurrentIdentity
  expect(identity.oidcSubject).toBe('uep-local-demo-owner')
  expect(identity.displayName).toBe('UEP Local Development Owner')
  expect(identity.membershipStatus).toBe('ACTIVE')
  expect(identity.roles).toContain('Organization Owner')
  expect(identity.tenantId).toBeTruthy()
  await expect(page.locator('.app-frame')).toBeVisible()
  expect(keycloakRequests).toEqual([])

  // Qualify the actual Real workspace navigation, not only the pure helper.
  const navigation = page.getByRole('navigation', { name: 'Primary navigation' })
  const navigationFilter = page.getByRole('searchbox', { name: 'Filter workspace sections' })
  await expect(navigation.getByRole('heading', { name: 'My workspace' })).toBeVisible()
  await expect(navigation.getByRole('heading', { name: 'Institution management' })).toBeVisible()
  await expect(navigation.getByRole('button', { name: 'Today', exact: true })).toHaveAttribute('aria-current', 'page')

  await navigationFilter.fill('organi')
  await expect(navigation.getByRole('button', { name: 'Organization', exact: true })).toBeVisible()
  await expect(navigation.getByRole('button', { name: 'Today', exact: true })).toHaveCount(0)
  await expect(navigation.getByRole('heading', { name: 'Institution management' })).toBeVisible()

  await navigationFilter.fill('definitely-no-workspace-section')
  await expect(navigation.getByRole('status')).toHaveText('No sections match “definitely-no-workspace-section”.')
  await navigationFilter.press('Escape')
  await expect(navigationFilter).toHaveValue('')
  await expect(navigation.getByRole('button', { name: 'Today', exact: true })).toBeVisible()
  await expect(navigation.getByRole('button', { name: 'Today', exact: true })).toHaveAttribute('aria-current', 'page')

  await page.setViewportSize({ width: 390, height: 844 })
  await expect(navigation).toHaveCSS('display', 'flex')
  await expect(navigation).toHaveCSS('overflow-x', 'auto')
  await expect(navigation).toHaveCSS('scroll-snap-type', 'x proximity')
  await page.setViewportSize({ width: 1440, height: 900 })

  await page.getByRole('button', { name: 'Organization', exact: true }).click()
  await expect(navigation.getByRole('button', { name: 'Organization', exact: true })).toHaveAttribute('aria-current', 'page')
  const branchCode = 'SQLITE-E2E-' + Date.now().toString()
  const branchName = 'SQLite persistence qualification ' + branchCode
  await page.getByLabel('Code').fill(branchCode)
  await page.getByLabel('Display name').fill(branchName)
  await page.getByLabel('Timezone', { exact: true }).fill('Asia/Kolkata')
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
  const databaseHandle = openSync(databasePath, 'r')
  try {
    const signature = Buffer.alloc(16)
    expect(readSync(databaseHandle, signature, 0, 16, 0)).toBe(16)
    expect(signature.toString('binary')).toBe('SQLite format 3\u0000')
  } finally {
    closeSync(databaseHandle)
  }
})
