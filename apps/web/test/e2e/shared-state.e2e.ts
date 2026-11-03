/**
 * Layer A E2E: Demo submissions, grades and browser-local persistence survive
 * reloads and are shared across browser tabs.
 */
import { test, expect } from '@playwright/test'
import { signInToDemo } from './_helpers'

test.describe('Layer A: Shared state persistence', () => {
  test('student assignment submission persists after reload', async ({ page }) => {
    await signInToDemo(page, 'student@demo.uep')
    await page.getByRole('navigation', { name: 'Primary navigation' }).getByRole('button', { name: 'Assignments' }).click()

    const assignment = page.locator('article.local-module').filter({ hasText: 'Deployment reflection' })
    await assignment.getByRole('button', { name: 'Submit work' }).click()
    await expect(assignment).toContainText('Submitted')

    await page.reload()
    await page.getByRole('navigation', { name: 'Primary navigation' }).getByRole('button', { name: 'Assignments' }).click()
    await expect(page.locator('article.local-module').filter({ hasText: 'Deployment reflection' })).toContainText('Submitted')
  })

  test('teacher grade persists after opening the same shared state in another tab', async ({ page }) => {
    await signInToDemo(page, 'teacher@demo.uep')
    await page.getByRole('navigation', { name: 'Primary navigation' }).getByRole('button', { name: 'Submission queue' }).click()

    const gradable = page.locator('article.local-module').filter({ has: page.getByRole('button', { name: /^Grade / }) }).first()
    const title = await gradable.locator('strong').textContent() ?? ''
    const detail = await gradable.locator('p').first().textContent() ?? ''
    await gradable.getByRole('button', { name: /^Grade / }).click()
    await expect(gradable).toContainText('85/100')

    const newPage = await page.context().newPage()
    await signInToDemo(newPage, 'teacher@demo.uep')
    await newPage.getByRole('navigation', { name: 'Primary navigation' }).getByRole('button', { name: 'Submission queue' }).click()
    const reloadedGrade = newPage.locator('article.local-module').filter({ hasText: title }).filter({ hasText: detail })
    await expect(reloadedGrade).toContainText('85/100')
    await newPage.close()
  })

  test('UI surfaces the current-tab-only warning when browser storage is unavailable', async ({ page }) => {
    await page.addInitScript(() => {
      const originalSetItem = Storage.prototype.setItem
      Storage.prototype.setItem = function (key: string, value: string) {
        if (key === 'uep.local-demo-data.v5') throw new DOMException('Storage disabled for this test', 'QuotaExceededError')
        return originalSetItem.call(this, key, value)
      }
    })
    await signInToDemo(page, 'student@demo.uep')
    await page.getByRole('navigation', { name: 'Primary navigation' }).getByRole('button', { name: 'My learning' }).click()
    await page.getByRole('button', { name: 'Record progress +10%' }).first().click()
    await expect(page.getByText('Change is visible in this tab only. Browser storage is unavailable, so reloading may discard it.')).toBeVisible()
  })
})
