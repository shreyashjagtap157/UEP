/**
 * Layer A E2E: persona menus and rendered actions remain role-scoped.
 * These browser assertions complement LocalDemoStore mutation-guard unit tests.
 */
import { test, expect } from '@playwright/test'
import { signInToDemo, switchDemoUser } from './_helpers'

test.describe('Layer A: Negative navigation and boundary checks', () => {
  test('Student sees submission actions but not teacher grading or platform operations', async ({ page }) => {
    await signInToDemo(page, 'student@demo.uep')
    const navigation = page.getByRole('navigation', { name: 'Primary navigation' })
    await expect(navigation.getByRole('button', { name: 'Assignments' })).toBeVisible()
    await expect(navigation.getByRole('button', { name: 'Submission queue' })).toHaveCount(0)
    await expect(navigation.getByRole('button', { name: 'Learner roster' })).toHaveCount(0)
    await expect(navigation.getByRole('button', { name: 'Integrations & webhooks' })).toHaveCount(0)

    await navigation.getByRole('button', { name: 'Assignments' }).click()
    await expect(page.getByRole('button', { name: 'Submit work' }).first()).toBeVisible()
    await expect(page.getByRole('button', { name: /^Grade / })).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'Publish exam result (80%)' })).toHaveCount(0)
  })

  test('Platform Developer cannot navigate to academic or product-authoring controls', async ({ page }) => {
    await signInToDemo(page, 'developer@demo.uep')
    const navigation = page.getByRole('navigation', { name: 'Primary navigation' })
    await expect(navigation.getByRole('button')).toHaveText([
      'Platform overview',
      'Integrations & webhooks',
      'Client service operations',
    ])
    await expect(navigation.getByRole('button', { name: 'Assignments' })).toHaveCount(0)
    await expect(navigation.getByRole('button', { name: 'Products & publishing' })).toHaveCount(0)
  })

  test('Switch user ends the current persona and starts a separate workspace', async ({ page }) => {
    await signInToDemo(page, 'student@demo.uep')
    await expect(page.locator('.workspace-header .eyebrow')).toContainText('Student')
    await switchDemoUser(page)

    await signInToDemo(page, 'teacher@demo.uep')
    await expect(page.locator('.workspace-header .eyebrow')).toContainText('Teacher')
    const navigation = page.getByRole('navigation', { name: 'Primary navigation' })
    await expect(navigation.getByRole('button', { name: 'Submission queue' })).toBeVisible()
    await expect(navigation.getByRole('button', { name: 'My learning' })).toHaveCount(0)

    await switchDemoUser(page)
    await expect(page.locator('.local-login-intro')).toBeVisible()
  })
})
