/**
 * Layer A E2E: the seeded golden path uses an existing assigned item:
 * Student submits, Teacher grades the same record, and Student sees the result.
 */
import { test, expect } from '@playwright/test'
import { signInToDemo, switchDemoUser } from './_helpers'

test.describe('Layer A: Golden path - student submits -> teacher grades -> student sees grade', () => {
  test('a seeded assignment is submitted, graded and visible with feedback after re-login', async ({ page }) => {
    await signInToDemo(page, 'student@demo.uep')
    await page.getByRole('navigation', { name: 'Primary navigation' }).getByRole('button', { name: 'Assignments' }).click()

    const assignment = page.locator('article.local-module')
      .filter({ hasText: 'Deployment reflection' })
      .filter({ hasText: 'Asha Sharma' })
    await expect(assignment).toContainText('Assigned')
    await assignment.getByRole('button', { name: 'Submit work' }).click()
    await expect(assignment).toContainText('Submitted')

    await switchDemoUser(page)
    await signInToDemo(page, 'teacher@demo.uep')
    await page.getByRole('navigation', { name: 'Primary navigation' }).getByRole('button', { name: 'Submission queue' }).click()
    const teacherSubmission = page.locator('article.local-module')
      .filter({ hasText: 'Deployment reflection' })
      .filter({ hasText: 'Asha Sharma' })
    await expect(teacherSubmission).toContainText('Submitted')
    await teacherSubmission.getByRole('button', { name: 'Grade 85%' }).click()
    await expect(teacherSubmission).toContainText('85/100')
    await expect(teacherSubmission).toContainText('Reviewed in the local demonstration workspace.')

    await switchDemoUser(page)
    await signInToDemo(page, 'student@demo.uep')
    await page.getByRole('navigation', { name: 'Primary navigation' }).getByRole('button', { name: 'Results & feedback' }).click()
    const result = page.locator('article.local-module').filter({ hasText: 'Deployment reflection' })
    await expect(result).toContainText('85/100')
    await expect(result).toContainText('Reviewed in the local demonstration workspace.')
  })
})
