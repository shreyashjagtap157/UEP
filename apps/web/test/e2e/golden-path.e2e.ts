/**
 * Layer A E2E Test: Golden path - teacher assigns -> student submits -> teacher grades -> student sees grade
 *
 * This is the primary cross-persona workflow:
 * 1. Teacher publishes/assigns content or work
 * 2. Student sees it in their workspace
 * 3. Student submits assignment/work
 * 4. Teacher sees that exact submission in their queue
 * 5. Teacher grades it
 * 6. Student sees the same final grade/feedback after refresh and fresh login
 *
 * @tag layerA
 * @tag golden-path
 * @tag workflow
 */
import { test, expect } from '@playwright/test'

test.describe('Layer A: Golden path - teacher assigns -> student submits -> teacher grades', () => {
  test('teacher publishes a course -> student sees it -> student submits -> teacher grades -> student sees grade after refresh', async ({
    page,
  }) => {
    // Step 1: Teacher signs in
    await page.goto('/')
    await page.fill('input[type="email"]', 'teacher@demo.uep')
    await page.fill('input[type="password"]', 'demo')
    await page.click('button[type="submit"]')
    await expect(page.locator('.app-frame')).toBeVisible({ timeout: 5000 })

    // Step 2: Teacher publishes a course
    await page.click('nav.nav-list button:has-text("Course catalog")')
    // Create/publish course (teacher-only action)
    await page.click('button:has-text("Create course")')
    await page.fill('input[placeholder*="title"]', 'Full Stack Web Development')
    await page.fill('textarea[placeholder*="description"]', 'Modern web development course')
    await page.click('button:has-text("Publish")')
    await expect(page.locator('.success-toast')).toBeVisible({ timeout: 3000 })

    // Step 3: Student signs in and sees the course
    await page.click('button:text("Switch Role / Sign out")')
    await expect(page.locator('.local-login-intro')).toBeVisible()

    await page.goto('/')
    await page.fill('input[type="email"]', 'student@demo.uep')
    await page.fill('input[type="password"]', 'demo')
    await page.click('button[type="submit"]')
    await expect(page.locator('.app-frame')).toBeVisible({ timeout: 5000 })

    // Student should see the published course
    await page.click('nav.nav-list button:has-text("Today")')
    const courseCards = page.locator('.course-card')
    await expect(courseCards).toHaveCountGreaterThan(0)

    // Step 4: Student submits an assignment
    await page.click('nav.nav-list button:has-text("Assignments")')
    await page.click('button:has-text("Submit assignment")')
    await page.fill('textarea', 'My completed work on Full Stack Web Development')
    await page.click('button[type="submit"]')
    await expect(page.locator('.submission-success')).toBeVisible({ timeout: 3000 })

    // Step 5: Teacher sees the submission and grades it
    await page.click('button:text("Switch Role / Sign out")')
    await expect(page.locator('.local-login-intro')).toBeVisible()

    await page.goto('/')
    await page.fill('input[type="email"]', 'teacher@demo.uep')
    await page.fill('input[type="password"]', 'demo')
    await page.click('button[type="submit"]')
    await expect(page.locator('.app-frame')).toBeVisible({ timeout: 5000 })

    await page.click('nav.nav-list button:has-text("Submission queue")')
    const pendingSubmissions = page.locator('.pending-submission')
    await expect(pendingSubmissions).toBeVisible()
    // Should see the submission we just created
    await expect(pendingSubmissions.first()).toContainText('Full Stack Web Development')

    // Teacher grades the submission
    await page.click('button:has-text("Grade")')
    await page.fill('input[aria-label*="score"]', '91')
    await page.fill('textarea', 'Clear deployment notes.')
    await page.click('button[type="submit"]')
    await expect(page.locator('.grade-success')).toBeVisible({ timeout: 3000 })

    // Step 6: Student signs in again and sees the same grade/feedback
    await page.click('button:text("Switch Role / Sign out")')
    await expect(page.locator('.local-login-intro')).toBeVisible()

    await page.goto('/')
    await page.fill('input[type="email"]', 'student@demo.uep')
    await page.fill('input[type="password"]', 'demo')
    await page.click('button[type="submit"]')
    await expect(page.locator('.app-frame')).toBeVisible({ timeout: 5000 })

    // Refresh and check grade persists
    await page.reload()
    const gradeDisplay = page.locator('.graded-submission .score')
    await expect(gradeDisplay).toContainText('91')
    const feedbackDisplay = page.locator('.graded-submission .feedback')
    await expect(feedbackDisplay).toContainText('Clear deployment notes.')
  })
})