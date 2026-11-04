import { expect, type Page } from '@playwright/test'

export async function signInToDemo(page: Page, email: string): Promise<void> {
  await page.goto('/')
  await page.locator('input[type="email"]').fill(email)
  await page.locator('input[type="password"]').fill('demo')
  await page.locator('button[type="submit"]').click()
  await expect(page.locator('.app-frame')).toBeVisible({ timeout: 5000 })
  await expect(page.getByRole('button', { name: 'Switch user', exact: true })).toBeVisible()
}

export async function switchDemoUser(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Switch user', exact: true }).click()
  await expect(page.locator('.local-login-intro')).toBeVisible()
}
