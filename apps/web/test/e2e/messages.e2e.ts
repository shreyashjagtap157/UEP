/**
 * Layer A E2E: Inbox recipient options and message delivery obey the same
 * recipient-scope policy in the visible UI and persisted shared Demo state.
 */
import { test, expect } from '@playwright/test'
import { signInToDemo, switchDemoUser } from './_helpers'

test.describe('Layer A: Recipient-scoped inbox communication', () => {
  test('student can contact an assigned teaching role, and the teacher receives and reads the message', async ({ page }) => {
    await signInToDemo(page, 'student@demo.uep')
    const navigation = page.getByRole('navigation', { name: 'Primary navigation' })
    await navigation.getByRole('button', { name: 'Inbox', exact: true }).click()

    const recipient = page.getByLabel('Recipient')
    const recipientLabels = await recipient.locator('option').allTextContents()
    expect(recipientLabels).toContain('Rahul Deshmukh · Teacher')
    expect(recipientLabels).toContain('Ishaan Patil · Teaching Assistant')
    expect(recipientLabels).toContain('Rohan Mehta · Mentor')
    expect(recipientLabels.some(label => /Student|Client Administrator|Platform Developer|Finance Administrator/.test(label))).toBe(false)

    await recipient.selectOption({ label: 'Rahul Deshmukh · Teacher' })
    await page.getByLabel('Subject').fill('E2E learner question about the next class')
    await page.getByLabel('Message').fill('Could you share the preparation material before our next session?')
    await page.getByRole('button', { name: 'Send request', exact: true }).click()
    await expect(page.getByLabel('Subject')).toHaveValue('')
    await expect(page.getByLabel('Message')).toHaveValue('')

    await switchDemoUser(page)
    await signInToDemo(page, 'teacher@demo.uep')
    await page.getByRole('navigation', { name: 'Primary navigation' }).getByRole('button', { name: 'Inbox', exact: true }).click()

    const delivered = page.locator('article.local-module')
      .filter({ hasText: 'E2E learner question about the next class' })
    await expect(delivered).toContainText('Could you share the preparation material before our next session?')
    await expect(delivered).toContainText('From Asha Sharma')
    await expect(delivered.locator('.chip')).toHaveText('Unread')
    await delivered.getByRole('button', { name: 'Mark as read' }).click()
    await expect(delivered.locator('.chip')).toHaveText('Read')
  })

  test('guardian recipient options remain limited to same-institution teaching contacts', async ({ page }) => {
    await signInToDemo(page, 'guardian@demo.uep')
    await page.getByRole('navigation', { name: 'Primary navigation' }).getByRole('button', { name: 'Inbox', exact: true }).click()

    const labels = await page.getByLabel('Recipient').locator('option').allTextContents()
    expect(labels.length).toBeGreaterThan(0)
    expect(labels.every(label => /Teacher|Teaching Assistant|Mentor/.test(label))).toBe(true)
    expect(labels.some(label => /Student|Client Administrator|Platform Developer|Finance Administrator/.test(label))).toBe(false)
  })
})
