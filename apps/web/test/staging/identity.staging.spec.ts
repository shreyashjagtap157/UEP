import { expect, test } from '@playwright/test'
import { accounts, backendUrl, issuerUrl, requiredRoles, baseUrl } from '../../playwright.staging.config'

function assertOpaqueIdMatches(actual: unknown, expected: unknown, label: string): void {
  if (typeof actual !== 'string' || typeof expected !== 'string' || actual !== expected) {
    throw new Error(label + ' did not match the configured identity.')
  }
}

function assertOpaqueIdPresent(value: unknown, label: string): void {
  if (typeof value !== 'string' || value.trim().length === 0) {
    throw new Error(label + ' was missing from the identity response.')
  }
}

function assertSafeTextMatches(actual: string | null, expected: string, label: string): void {
  if (actual !== expected) throw new Error(label + ' did not match the configured account.')
}

test.describe('Deployed UEP product — staging integration', () => {
  test('frontend, backend database health, and Keycloak discovery are reachable', async ({ request }) => {
    const [healthResponse, oidcResponse] = await Promise.all([
      request.get(`${backendUrl}/actuator/health`),
      request.get(`${issuerUrl}/.well-known/openid-configuration`),
    ])

    expect(healthResponse.status(), 'deployed backend health endpoint').toBe(200)
    const health = await healthResponse.json() as { status?: string }
    expect(health.status).toBe('UP')

    expect(oidcResponse.status(), 'deployed Keycloak OIDC discovery').toBe(200)
    const oidc = await oidcResponse.json() as {
      issuer?: string
      authorization_endpoint?: string
      jwks_uri?: string
    }
    expect(oidc.issuer).toBe(issuerUrl)
    expect(oidc.authorization_endpoint).toBe(`${issuerUrl}/protocol/openid-connect/auth`)
    expect(oidc.jwks_uri).toBeTruthy()

    const frontendResponse = await request.get(baseUrl)
    expect(frontendResponse.status(), 'deployed Cloudflare Pages frontend').toBe(200)
    expect(frontendResponse.headers()['content-type']).toContain('text/html')
  })

  for (const [roleKey, roleName] of requiredRoles) {
    test(`real Keycloak sign-in resolves a database-backed ${roleName} membership`, async ({ page }) => {
      const account = accounts[roleKey]
      const identityResponsePromise = page.waitForResponse(response => {
        try {
          const url = new URL(response.url())
          return url.origin === new URL(backendUrl).origin
            && url.pathname === '/api/v1/me'
            && response.request().method() === 'GET'
        } catch {
          return false
        }
      })

      await page.goto(baseUrl)
      const username = page.locator('input[name="username"]')
      const password = page.locator('input[name="password"]')
      await expect(username, 'Keycloak login form must be displayed by the configured realm').toBeVisible()
      await username.fill(account.username)
      await password.fill(account.password)
      await page.locator('#kc-login').click()

      const identityResponse = await identityResponsePromise
      expect(identityResponse.status(), 'frontend must receive authenticated /api/v1/me response').toBe(200)
      const identity = await identityResponse.json() as {
        tenantId: string
        membershipStatus: string
        displayName: string
        roles: string[]
        membershipId: string
        userId: string
      }

      assertOpaqueIdMatches(identity.tenantId, account.tenantId, 'Authenticated tenant identifier')
      expect(identity.membershipStatus).toBe('ACTIVE')
      assertSafeTextMatches(identity.displayName, account.displayName, 'Authenticated display name')
      assertOpaqueIdPresent(identity.membershipId, 'Membership identifier')
      assertOpaqueIdPresent(identity.userId, 'User identifier')
      expect(identity.roles.map(role => role.toLowerCase())).toContain(roleName.toLowerCase())

      await expect(page.locator('.app-frame')).toBeVisible()
      const signedInLabel = await page.locator('.sidebar-footer .signed-in').textContent()
      assertSafeTextMatches(signedInLabel, account.displayName, 'Signed-in account label')
      await expect(page.locator('.local-mode-badge')).toHaveCount(0)
      await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()
    })
  }
})
