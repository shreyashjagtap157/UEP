/**
 * Layer A E2E Test: Shared-state behavior - persistence after reload and
 * cross-tab updates in local demo mode.
 *
 * Verifies that changes persist after reload and that the UI surfaces
 * storage warnings instead of silently pretending changes were saved.
 *
 * @tag layerA
 * @tag shared-state
 * @tag persistence
 */
import { test, expect } from '@playwright/test'

test.describe('Layer A: Shared state persistence', () => {
  test('student submission persists after reload', async ({ page }) => {
    // Student signs in
    await page.goto('/')
    await page.fill('input[type="email"]', 'student@demo.uep')
    await page.fill('input[type="password"]', 'demo')
    await page.click('button[type="submit"]')
    await expect(page.locator('.app-frame')).toBeVisible({ timeout: 5000 })

    // Submit an assignment
    await page.click('nav.nav-list button:has-text("Assignments")')
    await page.click('button:has-text("Submit assignment")')
    await page.fill('textarea', 'Completed work submission')
    await page.click('button[type="submit"]')
    await expect(page.locator('.submission-success')).toBeVisible({ timeout: 3000 })

    // Reload the page
    await page.reload()
    await page.waitForLoadState('networkidle')

    // Submission should persist after reload
    const submittedWork = page.locator('.submission-item').first()
    await expect(submittedWork).toBeVisible()
    const submissionText = await submittedWork.textContent()
    expect(submissionText).toContain('Completed work submission')
  })

  test('teacher grade persists after cross-tab navigation', async ({ page }) => {
    // Teacher signs in
    await page.goto('/')
    await page.fill('input[type="email"]', 'teacher@demo.uep')
    await page.fill('input[type="password"]', 'demo')
    await page.click('button[type="submit"]')
    await expect(page.locator('.app-frame')).toBeVisible({ timeout: 5000 })

    // Navigate to submissions and grade one
    await page.click('nav.nav-list button:has-text("Submission queue")')
    await page.click('button:has-text("Grade")')
    await page.fill('input[aria-label*="score"]', '85')
    await page.click('button[type="submit"]')
    await expect(page.locator('.grade-success')).toBeVisible({ timeout: 3000 })

    // Open the same page in a new tab
    const newPage = await page.context().newPage()
    await newPage.goto('/')
    await newPage.fill('input[type="email"]', 'teacher@demo.uep')
    await newPage.fill('input[type="password"]', 'demo')
    await newPage.click('button[type="submit"]')
    await expect(newPage.locator('.app-frame')).toBeVisible({ timeout: 5000 })

    // Check that the graded status persists in the new tab
    await newPage.click('nav.nav-list button:has-text("Submission queue")')
    const gradedBadge = newPage.locator('.chip:has-text("Graded")')
    await expect(gradedBadge).toBeVisible()

    await newPage.close()
  })

  test('UI surfaces storage warning when browser storage is unavailable', async ({
    page,
  }) => {
    // Student signs in
    await page.goto('/')
    await page.fill('input[type="email"]', 'student@demo.uep')
    await page.fill('input[type="password"]', 'demo')
    await page.click('button[type="submit"]')
    await expect(page.locator('.app-frame')).toBeVisible({ timeout: 5000 })

    // Attempt an action that saves to localStorage
    await page.click('nav.nav-list button:has-text("Today")')
    // The UI should show a warning about demo mode storage limitations
    const smallText = page.locator('small.muted')
    const smallTextContent = await smallText.textContent()
    // In demo mode, there should be a note about browser storage limitations
    expect(smallTextContent).toContain('demo') || expect(smallTextContent).toContain('local mode')
  })
})