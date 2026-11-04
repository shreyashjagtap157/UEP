/**
 * Layer A E2E: attendance records are scoped to the teacher's assigned courses
 * and to the currently signed-in learner / guardian relationship.
 */
import { test, expect } from '@playwright/test'
import { signInToDemo, switchDemoUser } from './_helpers'

test.describe('Layer A: Attendance isolation', () => {
  test('teacher can mark an allowed learner and student sees only their own attendance', async ({ page }) => {
    await signInToDemo(page, 'teacher@demo.uep')
    const teacherNavigation = page.getByRole('navigation', { name: 'Primary navigation' })
    await teacherNavigation.getByRole('button', { name: 'Attendance register' }).click()

    const teacherPanel = page.locator('.panel').filter({ has: page.getByRole('heading', { name: 'Attendance register' }) })
    const lateRecord = teacherPanel.locator('article.local-module')
      .filter({ hasText: 'Meera Iyer' })
      .filter({ hasText: 'Late' })
    await expect(lateRecord.getByRole('button', { name: 'Mark present' })).toBeVisible()
    await lateRecord.getByRole('button', { name: 'Mark present' }).click()
    await expect(page.locator('section[aria-live="polite"]')).toContainText('Attendance update saved.')
    const updatedRecord = teacherPanel.locator('article.local-module')
      .filter({ hasText: 'Meera Iyer' })
      .filter({ hasText: '2026-10-30' })
    await expect(updatedRecord).toContainText('Present')
    await expect(updatedRecord).not.toContainText('Late')

    await switchDemoUser(page)
    await signInToDemo(page, 'student@demo.uep')
    await page.getByRole('navigation', { name: 'Primary navigation' }).getByRole('button', { name: 'Attendance' }).click()
    const studentPanel = page.locator('.panel').filter({ has: page.getByRole('heading', { name: 'Attendance register' }) })
    const studentRows = studentPanel.locator('article.local-module')
    await expect(studentRows.first()).toBeVisible()
    const count = await studentRows.count()
    expect(count).toBeGreaterThan(0)
    for (let index = 0; index < count; index += 1) {
      await expect(studentRows.nth(index)).toContainText('Asha Sharma')
      await expect(studentRows.nth(index)).not.toContainText('Ravi Nair')
      await expect(studentRows.nth(index)).not.toContainText('Meera Iyer')
    }
  })

  test('guardian attendance is limited to the explicitly linked learner', async ({ page }) => {
    await signInToDemo(page, 'guardian@demo.uep')
    await page.getByRole('navigation', { name: 'Primary navigation' }).getByRole('button', { name: 'Attendance' }).click()
    const panel = page.locator('.panel').filter({ has: page.getByRole('heading', { name: 'Attendance register' }) })
    const rows = panel.locator('article.local-module')
    await expect(rows.first()).toBeVisible()
    const count = await rows.count()
    expect(count).toBeGreaterThan(0)
    for (let index = 0; index < count; index += 1) {
      await expect(rows.nth(index)).toContainText('Asha Sharma')
      await expect(rows.nth(index)).not.toContainText('Ravi Nair')
      await expect(rows.nth(index)).not.toContainText('Meera Iyer')
    }
  })
})
