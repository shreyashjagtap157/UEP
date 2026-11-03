/**
 * Layer A E2E: assigned schedules are visible to in-scope participants and
 * communication uses the recipient-scoped Inbox workflow.
 */
import { test, expect } from '@playwright/test'
import { signInToDemo, switchDemoUser } from './_helpers'

test.describe('Layer A: Schedule and recipient-scoped communication', () => {
  test('teacher and student see only the classes in their schedule scope', async ({ page }) => {
    await signInToDemo(page, 'teacher@demo.uep')
    await page.getByRole('navigation', { name: 'Primary navigation' }).getByRole('button', { name: 'My classes' }).click()
    const teacherSchedule = page.locator('.panel').filter({ has: page.getByRole('heading', { name: 'Assigned classes' }) })
    await expect(teacherSchedule).toContainText('Full Stack Web Development')
    await expect(teacherSchedule).toContainText('Database Systems checkpoint')

    await switchDemoUser(page)
    await signInToDemo(page, 'student@demo.uep')
    await page.getByRole('navigation', { name: 'Primary navigation' }).getByRole('button', { name: 'Calendar & classes' }).click()
    const studentSchedule = page.locator('.panel').filter({ has: page.getByRole('heading', { name: 'Assigned classes' }) })
    await expect(studentSchedule).toContainText('Full Stack Web Development')
    await expect(studentSchedule).toContainText('Database Systems checkpoint')
  })

  test('teacher can send a recipient-scoped message that appears in the student Inbox', async ({ page }) => {
    await signInToDemo(page, 'teacher@demo.uep')
    await page.getByRole('navigation', { name: 'Primary navigation' }).getByRole('button', { name: 'Inbox' }).click()
    await page.getByLabel('Recipient').selectOption('student-asha')
    await page.getByLabel('Subject').fill('Class schedule confirmation')
    await page.getByLabel('Message').fill('The Database Systems checkpoint remains scheduled for the next class.')
    await page.getByRole('button', { name: 'Send message', exact: true }).click()
    await expect(page.getByText('Message sent to the selected recipient.')).toBeVisible()

    await switchDemoUser(page)
    await signInToDemo(page, 'student@demo.uep')
    await page.getByRole('navigation', { name: 'Primary navigation' }).getByRole('button', { name: 'Inbox' }).click()
    const inbox = page.locator('.panel').filter({ has: page.getByRole('heading', { name: 'Inbox' }) })
    await expect(inbox).toContainText('Class schedule confirmation')
    await expect(inbox).toContainText('The Database Systems checkpoint remains scheduled for the next class.')
  })
})
