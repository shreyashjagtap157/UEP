/**
 * Layer A E2E Test: Common login page and persona sign-in/sign-out
 *
 * Verifies that the login page renders with clear "local/demo" labeling,
 * invalid credentials are rejected, and all 13 canonical personas can sign in
 * and sign out successfully.
 *
 * @tag layerA
 * @tag login
 * @tag signout
 */
import { test, expect } from '@playwright/test'

test.describe('Layer A: Common login and sign-out', () => {
  test('login page renders with local/demo labeling', async ({ page }) => {
    await page.goto('/')
    // The local login page should have a clear LOCAL MODE badge
    const localModeBadge = page.locator('.local-mode-badge')
    await expect(localModeBadge).toBeVisible()
    await expect(localModeBadge).toContainText('LOCAL MODE')

    const loginHeading = page.locator('h1')
    await expect(loginHeading).toContainText('One platform, one workspace for each relationship.')
  })

  test('invalid email/password are rejected', async ({ page }) => {
    await page.goto('/')
    await page.fill('input[type="email"]', 'invalid@test.com')
    await page.fill('input[type="password"]', 'wrongpassword')
    await page.click('button[type="submit"]')
    const error = page.locator('.error-text[role="alert"]')
    await expect(error).toBeVisible()
    await expect(error).toContainText('Use a supplied demo account and the password demo.')
  })

  test('all 13 canonical personas can sign in and sign out', async ({ page }) => {
    const personaEmails = [
      'student@demo.uep',
      'teacher@demo.uep',
      'client@demo.uep',
      'developer@demo.uep',
      'admin@demo.uep',
      'guardian@demo.uep',
      'exam-controller@demo.uep',
      'finance-admin@demo.uep',
      'teaching-assistant@demo.uep',
      'evaluator@demo.uep',
      'mentor@demo.uep',
      'support@demo.uep',
      'auditor@demo.uep',
    ]

    for (const email of personaEmails) {
      await page.goto('/')
      await page.fill('input[type="email"]', email)
      await page.fill('input[type="password"]', 'demo')
      await page.click('button[type="submit"]')
      // Should successfully sign in - the workspace should be visible
      await expect(page.locator('.app-frame')).toBeVisible({ timeout: 5000 })
      // Sign out
      await page.click('button:text("Switch Role / Sign out")')
      // Should return to login page
      await expect(page.locator('.local-login-intro')).toBeVisible()
    }
  })
})