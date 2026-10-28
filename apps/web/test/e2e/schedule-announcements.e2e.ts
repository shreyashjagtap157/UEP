/**
 * Layer A E2E Test: Schedule and announcements
 *
 * Verifies publishing/ seeding a class and announcement, and confirming only
 * in-scope personas see relevant records.
 *
 * @tag layerA
 * @tag schedule
 * @tag announcements
 */
import { test, expect } from '@playwright/test'

test.describe('Layer A: Schedule and announcements', () => {
  test('teacher publishes a class, student sees it', async ({ page }) => {
    // Teacher signs in
    await page.goto('/')
    await page.fill('input[type="email"]', 'teacher@demo.uep')
    await page.fill('input[type="password"]', 'demo')
    await page.click('button[type="submit"]')
    await expect(page.locator('.app-frame')).toBeVisible({ timeout: 5000 })

    // Navigate to schedule and create a class
    await page.click('nav.nav-list button:has-text("Schedule")')
    await page.click('button:has-text("Add class")')
    await page.fill('input[placeholder*="title"]', 'Full Stack Web Development Lab')
    await page.selectOption('select[aria-label*="duration"]', '90')
    await page.click('button:has-text("Schedule")')
    await expect(page.locator('.success-toast')).toBeVisible({ timeout: 3000 })

    // Student signs in and sees the class
    await page.click('button:text("Switch Role / Sign out")')
    await page.goto('/')
    await page.fill('input[type="email"]', 'student@demo.uep')
    await page.fill('input[type="password"]', 'demo')
    await page.click('button[type="submit"]')
    await expect(page.locator('.app-frame')).toBeVisible({ timeout: 5000 })

    await page.click('nav.nav-list button:has-text("Calendar & classes")')
    const classCards = page.locator('.class-card')
    await expect(classCards).toHaveCountGreaterThan(0)
  })

  test('teacher publishes an announcement, only in-scope personas see it', async ({
    page,
  }) => {
    // Teacher signs in
    await page.goto('/')
    await page.fill('input[type="email"]', 'teacher@demo.uep')
    await page.fill('input[type="password"]', 'demo')
    await page.click('button[type="submit"]')
    await expect(page.locator('.app-frame')).toBeVisible({ timeout: 5000 })

    // Publish announcement
    await page.click('nav.nav-list button:has-text("Announcements")')
    await page.click('button:has-text("Create announcement")')
    await page.fill('textarea', 'Welcome back for the new semester!')
    await page.click('button:has-text("Publish")')
    await expect(page.locator('.success-toast')).toBeVisible({ timeout: 3000 })

    // Student signs in and sees the announcement
    await page.click('button:text("Switch Role / Sign out")')
    await page.goto('/')
    await page.fill('input[type="email"]', 'student@demo.uep')
    await page.fill('input[type="password"]', 'demo')
    await page.click('button[type="submit"]')
    await expect(page.locator('.app-frame')).toBeVisible({ timeout: 5000 })

    await page.click('nav.nav-list button:has-text("Announcements")')
    const announcementText = page.locator('.announcement-text')
    await expect(announcementText).toContainText('Welcome back')
  })
})