/**
 * Layer A E2E Test: Common login page and persona sign-in/sign-out
 *
 * Verifies that the login page renders with clear "local/demo" labeling,
 * invalid credentials are rejected, all 14 canonical personas can sign in,
 * and the Switch user action returns to the grouped account selector.
 *
 * @tag layerA
 * @tag login
 * @tag signout
 */
import { test, expect } from '@playwright/test'

test.describe('Layer A: Common login and sign-out', () => {
  test('login page renders with local/demo labeling', async ({ page }) => {
    await page.goto('/')
    // The local login page should have a clear OFFLINE DEMO badge
    const localModeBadge = page.locator('.local-mode-badge')
    await expect(localModeBadge).toBeVisible()
    await expect(localModeBadge).toContainText('OFFLINE DEMO')

    const loginHeading = page.locator('h1')
    await expect(loginHeading).toContainText('One platform, one workspace for each relationship.')
    await expect(page.getByRole('heading', { name: 'Platform team' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Client & institution management' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Institution teaching & learning' })).toBeVisible()
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

  test('all 14 canonical personas can sign in and switch users', async ({ page }) => {
    const personaEmails = [
      'student@demo.uep',
      'teacher@demo.uep',
      'client@demo.uep',
      'developer@demo.uep',
      'product-maintainer@demo.uep',
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
      // Sign in must open the persona-specific workspace, not a generic role menu.
      await expect(page.locator('.app-frame')).toBeVisible({ timeout: 5000 })
      const navigation = page.getByRole('navigation', { name: 'Primary navigation' })
      const expectedNavigation: Record<string, string[]> = {
        'student@demo.uep': ['Today', 'My learning', 'Assignments', 'Results & feedback', 'Calendar & classes', 'Attendance', 'Inbox'],
        'teacher@demo.uep': ['Teaching overview', 'My classes', 'Learner roster', 'Submission queue', 'Attendance register', 'Course catalog', 'Class analytics', 'Inbox'],
        'client@demo.uep': ['Organization overview', 'Programs & actions', 'People', 'Outcomes & reports', 'Delivery schedule', 'Operations', 'Inbox'],
        'developer@demo.uep': ['Platform overview', 'Integrations & webhooks', 'Client service operations'],
        'product-maintainer@demo.uep': ['Product portfolio', 'Products & publishing', 'Product activity'],
        'admin@demo.uep': ['Platform overview', 'People & roles', 'Organizations', 'Course catalog', 'Academic records', 'Scheduling', 'Operations', 'Audit activity', 'Configuration'],
        'guardian@demo.uep': ['Learner overview', 'Learner progress', 'Assignments & results', 'Calendar', 'Attendance', 'Inbox'],
        'exam-controller@demo.uep': ['Exam overview', 'Exam schedule', 'Exam submissions', 'Exam activity'],
        'finance-admin@demo.uep': ['Finance overview', 'Invoices & payments'],
        'teaching-assistant@demo.uep': ['Teaching overview', 'Assigned classes', 'Submission triage', 'Attendance', 'My activity'],
        'evaluator@demo.uep': ['Evaluation overview', 'Grading queue', 'Evaluation activity'],
        'mentor@demo.uep': ['Mentor overview', 'Mentee progress', 'Learning evidence', 'Mentoring activity'],
        'support@demo.uep': ['Support overview', 'Support queue'],
        'auditor@demo.uep': ['Audit overview', 'Audit log'],
      }
      expect(expectedNavigation[email], `Expected a role-specific navigation contract for ${email}`).toBeDefined()
      await expect(navigation.getByRole('button')).toHaveText(expectedNavigation[email]!)
      await page.getByRole('button', { name: 'Switch user', exact: true }).click()
      // Should return to login page
      await expect(page.locator('.local-login-intro')).toBeVisible()
    }
  })
})