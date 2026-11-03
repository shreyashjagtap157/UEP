/**
 * Layer A E2E: Guardian and Mentor workspaces expose only the explicitly
 * linked learner's course and progress records.
 */
import { test, expect } from '@playwright/test'
import { signInToDemo } from './_helpers'

test.describe('Layer A: Guardian and Mentor scope', () => {
  test('Parent / Guardian sees only the linked learner progress', async ({ page }) => {
    await signInToDemo(page, 'guardian@demo.uep')
    const navigation = page.getByRole('navigation', { name: 'Primary navigation' })
    await navigation.getByRole('button', { name: 'Learner progress' }).click()

    const progressPanel = page.locator('.panel').filter({ has: page.getByRole('heading', { name: 'Asha Sharma · learning progress' }) })
    await expect(progressPanel).toBeVisible()
    await expect(progressPanel).toContainText('Full Stack Web Development')
    await expect(progressPanel).not.toContainText('Ravi Nair')
    await expect(progressPanel).not.toContainText('Meera Iyer')
  })

  test('Mentor sees progress for the linked learner and assigned courses only', async ({ page }) => {
    await signInToDemo(page, 'mentor@demo.uep')
    const navigation = page.getByRole('navigation', { name: 'Primary navigation' })
    await navigation.getByRole('button', { name: 'Mentee progress' }).click()

    const progressPanel = page.locator('.panel').filter({ has: page.getByRole('heading', { name: 'Asha Sharma · learning progress' }) })
    await expect(progressPanel).toBeVisible()
    await expect(progressPanel).toContainText('Full Stack Web Development')
    await expect(progressPanel).not.toContainText('Ravi Nair')
    await expect(progressPanel).not.toContainText('Meera Iyer')
  })

  test('Student navigation does not expose Guardian or Mentor relationship views', async ({ page }) => {
    await signInToDemo(page, 'student@demo.uep')
    const navigation = page.getByRole('navigation', { name: 'Primary navigation' })
    await expect(navigation.getByRole('button', { name: 'Learner progress' })).toHaveCount(0)
    await expect(navigation.getByRole('button', { name: 'Mentee progress' })).toHaveCount(0)
  })
})
