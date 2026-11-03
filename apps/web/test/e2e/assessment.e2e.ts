/**
 * Layer A E2E: the seeded exam path is managed by Exam Controller, submitted
 * by the linked student, and graded in the exam-controller workspace.
 */
import { test, expect } from '@playwright/test'
import { signInToDemo, switchDemoUser } from './_helpers'

test.describe('Layer A: Assessment exam lifecycle', () => {
  test('student submits an assigned exam attempt and controller publishes its result', async ({ page }) => {
    await signInToDemo(page, 'exam-controller@demo.uep')

    const navigation = page.getByRole('navigation', { name: 'Primary navigation' })
    await expect(navigation.getByRole('button', { name: 'Exam schedule' })).toBeVisible()
    await navigation.getByRole('button', { name: 'Exam schedule' }).click()
    const schedulePanel = page.locator('.panel').filter({ has: page.getByRole('heading', { name: 'Examination sessions' }) })
    await expect(schedulePanel).toContainText('Database Systems checkpoint')

    await navigation.getByRole('button', { name: 'Exam submissions' }).click()
    const examQueue = page.locator('.panel').filter({ has: page.getByRole('heading', { name: 'Exam-linked records' }) })
    await expect(examQueue).toContainText('Asha Sharma')

    await switchDemoUser(page)
    await signInToDemo(page, 'student@demo.uep')
    const studentNavigation = page.getByRole('navigation', { name: 'Primary navigation' })
    await studentNavigation.getByRole('button', { name: 'Assignments' }).click()
    const assignedExam = page.locator('.local-module')
      .filter({ hasText: 'Database Systems checkpoint' })
      .filter({ hasText: 'Asha Sharma' })
    await expect(assignedExam).toContainText('Assigned')
    await assignedExam.getByRole('button', { name: 'Submit exam attempt' }).click()
    await expect(assignedExam).toContainText('Submitted')

    await switchDemoUser(page)
    await signInToDemo(page, 'exam-controller@demo.uep')
    await page.getByRole('navigation', { name: 'Primary navigation' })
      .getByRole('button', { name: 'Exam submissions' }).click()
    const submittedExam = page.locator('.local-module')
      .filter({ hasText: 'Database Systems checkpoint' })
      .filter({ hasText: 'Asha Sharma' })
    await expect(submittedExam.getByRole('button', { name: 'Publish exam result (80%)' })).toBeVisible()
    await submittedExam.getByRole('button', { name: 'Publish exam result (80%)' }).click()
    await expect(submittedExam).toContainText('80/100')
  })

  test('student cannot see exam-controller scheduling or result-publishing actions', async ({ page }) => {
    await signInToDemo(page, 'student@demo.uep')
    const navigation = page.getByRole('navigation', { name: 'Primary navigation' })
    await expect(navigation.getByRole('button', { name: 'Exam schedule' })).toHaveCount(0)
    await expect(navigation.getByRole('button', { name: 'Exam submissions' })).toHaveCount(0)
    await navigation.getByRole('button', { name: 'Assignments' }).click()

    await expect(page.getByRole('button', { name: 'Submit exam attempt' }).first()).toBeVisible()
    await expect(page.getByRole('button', { name: 'Publish exam result (80%)' })).toHaveCount(0)
  })
})
