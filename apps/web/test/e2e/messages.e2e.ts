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

  test('an open Inbox drops a suspended recipient and selects a still-authorized contact', async ({ page }) => {
    await signInToDemo(page, 'student@demo.uep')
    await page.getByRole('navigation', { name: 'Primary navigation' }).getByRole('button', { name: 'Inbox', exact: true }).click()

    const recipient = page.getByLabel('Recipient')
    await expect(recipient).toHaveValue('teacher-rahul')
    const initialRecipientLabels = await recipient.locator('option').allTextContents()
    expect(initialRecipientLabels).toContain('Rahul Deshmukh · Teacher')

    // Suspend the selected contact from a second same-origin tab. The first
    // tab must react through the browser's real cross-document StorageEvent.
    const updater = await page.context().newPage()
    await updater.goto('/')
    await updater.evaluate(() => {
      const key = 'uep.local-demo-data.v5'
      const raw = window.localStorage.getItem(key)
      if (!raw) throw new Error('The seeded Demo state was not persisted.')
      const state = JSON.parse(raw) as {
        users: Array<{ id: string; status: string }>
      }
      state.users = state.users.map(user =>
        user.id === 'teacher-rahul' ? { ...user, status: 'Suspended' } : user,
      )
      window.localStorage.setItem(key, JSON.stringify(state))
    })
    await updater.close()

    await expect(recipient.locator('option').filter({ hasText: 'Rahul Deshmukh' })).toHaveCount(0)
    await expect(recipient).toHaveValue('')

    // Re-activating the same contact must not resurrect the old selection.
    const reactivator = await page.context().newPage()
    await reactivator.goto('/')
    await reactivator.evaluate(() => {
      const key = 'uep.local-demo-data.v5'
      const raw = window.localStorage.getItem(key)
      if (!raw) throw new Error('The updated Demo state was not persisted.')
      const state = JSON.parse(raw) as {
        users: Array<{ id: string; status: string }>
      }
      state.users = state.users.map(user =>
        user.id === 'teacher-rahul' ? { ...user, status: 'Active' } : user,
      )
      window.localStorage.setItem(key, JSON.stringify(state))
    })
    await reactivator.close()
    await expect(recipient.locator('option').filter({ hasText: 'Rahul Deshmukh' })).toHaveCount(1)
    await expect(recipient).toHaveValue('')

    await page.getByLabel('Subject').fill('Question for the active teaching assistant')
    await page.getByLabel('Message').fill('Could you help with the assigned class?')
    await expect(page.getByRole('button', { name: 'Send request', exact: true })).toBeDisabled()

    // Removing a selected recipient must not silently redirect the message.
    await recipient.selectOption('ta-ishaan')
    await expect(page.getByRole('button', { name: 'Send request', exact: true })).toBeEnabled()
    await page.getByRole('button', { name: 'Send request', exact: true }).click()
    await expect(page.getByLabel('Subject')).toHaveValue('')
    await expect(page.getByLabel('Message')).toHaveValue('')

    await switchDemoUser(page)
    await signInToDemo(page, 'teaching-assistant@demo.uep')
    await page.getByRole('navigation', { name: 'Primary navigation' }).getByRole('button', { name: 'Inbox', exact: true }).click()
    const explicitlySelectedRecipient = page.locator('article.local-module')
      .filter({ hasText: 'Question for the active teaching assistant' })
    await expect(explicitlySelectedRecipient).toContainText('Could you help with the assigned class?')
    await expect(explicitlySelectedRecipient).toContainText('From Asha Sharma')
    await expect(explicitlySelectedRecipient.locator('.chip')).toHaveText('Unread')
  })

  test('suspending the signed-in sender revokes the session and explains the restriction', async ({ page }) => {
    await signInToDemo(page, 'student@demo.uep')
    await page.getByRole('navigation', { name: 'Primary navigation' }).getByRole('button', { name: 'Inbox', exact: true }).click()
    await expect(page.getByLabel('Recipient')).toBeVisible()

    const updater = await page.context().newPage()
    await updater.goto('/')
    await updater.evaluate(() => {
      const key = 'uep.local-demo-data.v5'
      const raw = window.localStorage.getItem(key)
      if (!raw) throw new Error('The seeded Demo state was not persisted.')
      const state = JSON.parse(raw) as {
        users: Array<{ id: string; status: string }>
      }
      state.users = state.users.map(user =>
        user.id === 'student-asha' ? { ...user, status: 'Suspended' } : user,
      )
      window.localStorage.setItem(key, JSON.stringify(state))
    })
    await updater.close()

    // A suspended sender is signed out at the application boundary. The
    // workspace and composer must be unmounted, and the login screen explains
    // why the current session was revoked.
    await expect(page.locator('.local-login-intro')).toBeVisible()
    await expect(page.getByText(
      'Your demo session ended because this account is inactive. Sign in with an active account or ask an administrator to reactivate it.',
      { exact: true },
    )).toBeVisible()
    await expect(page.locator('.app-frame')).toHaveCount(0)
    await expect(page.getByLabel('Recipient')).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'Switch user', exact: true })).toHaveCount(0)
    expect(await page.evaluate(() => sessionStorage.getItem('uep-local-account'))).toBeNull()

    // Reactivating the record must not restore the revoked browser session.
    const reactivator = await page.context().newPage()
    await reactivator.goto('/')
    await reactivator.evaluate(() => {
      const key = 'uep.local-demo-data.v5'
      const raw = window.localStorage.getItem(key)
      if (!raw) throw new Error('The suspended Demo state was not persisted.')
      const state = JSON.parse(raw) as {
        users: Array<{ id: string; status: string }>
      }
      state.users = state.users.map(user =>
        user.id === 'student-asha' ? { ...user, status: 'Active' } : user,
      )
      window.localStorage.setItem(key, JSON.stringify(state))
    })
    await reactivator.close()
    await expect(page.locator('.local-login-intro')).toBeVisible()
    await expect(page.locator('.app-frame')).toHaveCount(0)
    expect(await page.evaluate(() => sessionStorage.getItem('uep-local-account'))).toBeNull()
  })

  test('guardian recipient options remain limited to same-institution teaching contacts', async ({ page }) => {
    await signInToDemo(page, 'guardian@demo.uep')
    await page.getByRole('navigation', { name: 'Primary navigation' }).getByRole('button', { name: 'Inbox', exact: true }).click()

    const labels = await page.getByLabel('Recipient').locator('option:not([value=""])').allTextContents()
    expect(labels.length).toBeGreaterThan(0)
    expect(labels.every(label => /Teacher|Teaching Assistant|Mentor/.test(label))).toBe(true)
    expect(labels.some(label => /Student|Client Administrator|Platform Developer|Finance Administrator/.test(label))).toBe(false)
  })
})
