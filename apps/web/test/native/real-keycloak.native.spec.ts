import { expect, test } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

type LocalBootstrapState = {
  IdentityProvider?: unknown
  KeycloakOwnerPassword?: unknown
}

function localState(): LocalBootstrapState {
  const root = process.env.LOCALAPPDATA
  if (!root) throw new Error('Local Keycloak browser qualification requires the initializing Windows user profile.')
  const path = join(root, 'UEP', 'local-native', 'state.json')
  return JSON.parse(readFileSync(path, 'utf8').replace(/^\uFEFF/, '')) as LocalBootstrapState
}

test('local Keycloak: OIDC authorization-code PKCE login resolves the database-backed owner', async ({ page, request }) => {
  const state = localState()
  test.skip(state.IdentityProvider !== 'Keycloak', 'Keycloak browser coverage runs only with -IdentityProvider Keycloak')
  expect(typeof state.KeycloakOwnerPassword).toBe('string')
  expect((state.KeycloakOwnerPassword as string).length).toBeGreaterThanOrEqual(32)

  const issuer = 'http://localhost:8081/realms/education'
  const discoveryResponse = await request.get(issuer + '/.well-known/openid-configuration')
  expect(discoveryResponse.status()).toBe(200)
  const discovery = await discoveryResponse.json() as {
    issuer?: string
    authorization_endpoint?: string
    jwks_uri?: string
  }
  expect(discovery.issuer).toBe(issuer)
  expect(discovery.authorization_endpoint).toBe(issuer + '/protocol/openid-connect/auth')
  expect(discovery.jwks_uri).toBeTruthy()

  const anonymousApi = await request.get('http://127.0.0.1:8080/api/v1/me')
  expect(anonymousApi.status()).toBe(401)

  let authorizationRequest: URL | undefined
  page.on('request', browserRequest => {
    try {
      const url = new URL(browserRequest.url())
      if (url.origin === 'http://localhost:8081' && url.pathname.endsWith('/protocol/openid-connect/auth')) {
        authorizationRequest = url
      }
    } catch {
      // Non-URL browser request is irrelevant to the OIDC authorization contract.
    }
  })

  const identityPromise = page.waitForResponse(response => {
    const url = new URL(response.url())
    return url.origin === 'http://127.0.0.1:5173'
      && url.pathname === '/api/v1/me'
      && response.request().method() === 'GET'
  })
  await page.goto('http://127.0.0.1:5173')
  await expect(page.locator('#username'), 'Keycloak login page should be displayed').toBeVisible()
  await page.locator('#username').fill('uep-owner')
  await page.locator('#password').fill(state.KeycloakOwnerPassword as string)
  await page.locator('#kc-login').click()

  const identityResponse = await identityPromise
  expect(identityResponse.status()).toBe(200)
  const identityHeaders = await identityResponse.request().allHeaders()
  expect(identityHeaders.authorization).toMatch(/^Bearer /)
  expect(identityHeaders['x-uep-local-auth']).toBeUndefined()

  expect(authorizationRequest?.searchParams.get('client_id')).toBe('platform-web')
  expect(authorizationRequest?.searchParams.get('response_type')).toBe('code')
  expect(authorizationRequest?.searchParams.get('code_challenge_method')).toBe('S256')
  expect(authorizationRequest?.searchParams.get('code_challenge')).toMatch(/^[A-Za-z0-9_-]{43}$/)
  expect(authorizationRequest?.searchParams.get('state')).toBeTruthy()
  expect(authorizationRequest?.searchParams.get('nonce')).toBeTruthy()
  expect(authorizationRequest?.searchParams.get('redirect_uri')).toBe('http://127.0.0.1:5173/')

  const identity = await identityResponse.json() as {
    oidcSubject: string
    displayName: string
    email: string
    tenantId: string
    membershipStatus: string
    roles: string[]
  }
  expect(identity.oidcSubject).toBe('47bcafc8-b3ee-4c84-920f-0c21f4468c55')
  expect(identity.displayName).toBe('UEP Local Keycloak Owner')
  expect(identity.email).toBe('owner@local.uep')
  expect(identity.tenantId).toBe('b1b2789e-9b87-4c9d-a6f7-6f5d7e8a1d44')
  expect(identity.membershipStatus).toBe('ACTIVE')
  expect(identity.roles).toContain('Organization Owner')

  await expect(page.locator('.app-frame')).toBeVisible()
  await expect(page.locator('.local-mode-badge')).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Sign out', exact: true })).toBeVisible()
})
