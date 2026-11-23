/**
 * Layer A E2E: finance is read-only, Support Operator can triage service cases,
 * and Auditor can inspect activity without mutation controls.
 */
import { test, expect } from '@playwright/test'
import { signInToDemo } from './_helpers'

test.describe('Layer A: Finance, support, and auditor role boundaries', () => {
  test('Auditor receives a read-only audit workspace', async ({ page }) => {
    await signInToDemo(page, 'auditor@demo.uep')
    const navigation = page.getByRole('navigation', { name: 'Primary navigation' })
    await expect(navigation.getByRole('button')).toHaveText(['Audit overview', 'Audit log'])
    await navigation.getByRole('button', { name: 'Audit log' }).click()
    const auditPanel = page.locator('.panel').filter({ has: page.getByRole('heading', { name: 'Audit log' }) })
    await expect(auditPanel).toBeVisible()
    await expect(auditPanel.getByRole('button')).toHaveCount(0)
  })

  test('Finance Administrator can view invoices but has no edit controls', async ({ page }) => {
    await signInToDemo(page, 'finance-admin@demo.uep')
    await page.getByRole('navigation', { name: 'Primary navigation' }).getByRole('button', { name: 'Invoices & payments' }).click()
    const financePanel = page.locator('.panel').filter({ has: page.getByRole('heading', { name: 'Invoices and account status' }) })
    await expect(financePanel).toBeVisible()
    await expect(financePanel).toContainText('NLI-2026-041')
    await expect(financePanel.locator('article.local-module')).toHaveCount(3)
    await expect(financePanel.getByRole('button')).toHaveCount(0)
  })

  test('Support Operator can triage and resolve existing scoped cases', async ({ page }) => {
    await signInToDemo(page, 'support@demo.uep')
    await page.getByRole('navigation', { name: 'Primary navigation' }).getByRole('button', { name: 'Support queue' }).click()
    const supportPanel = page.locator('.panel').filter({ has: page.getByRole('heading', { name: 'Support cases' }) })
    await expect(supportPanel).toBeVisible()
    await expect(supportPanel.locator('article.local-module')).toHaveCount(3)

    const caseRow = supportPanel.locator('article.local-module').filter({ hasText: 'Open' }).first()
    await caseRow.getByRole('button', { name: 'Start triage' }).click()
    await expect(caseRow).toContainText('In progress')
    await caseRow.getByRole('button', { name: 'Resolve case' }).click()
    await expect(caseRow).toContainText('Resolved')
  })
})
