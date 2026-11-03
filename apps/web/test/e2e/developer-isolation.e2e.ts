/**
 * Layer A E2E: Platform Developer is limited to integrations and operations;
 * Product Maintainer owns catalogue/publishing; academic personas do not see either.
 */
import { test, expect } from '@playwright/test'
import { signInToDemo, switchDemoUser } from './_helpers'

test.describe('Layer A: Platform Developer and Product Maintainer isolation', () => {
  test('Platform Developer sees integrations but not product, learning or assignment tools', async ({ page }) => {
    await signInToDemo(page, 'developer@demo.uep')
    const navigation = page.getByRole('navigation', { name: 'Primary navigation' })
    await expect(navigation.getByRole('button')).toHaveText([
      'Platform overview',
      'Integrations & webhooks',
      'Client service operations',
    ])
    await navigation.getByRole('button', { name: 'Integrations & webhooks' }).click()
    const integrations = page.locator('.panel').filter({ has: page.getByRole('heading', { name: 'Webhook delivery summary' }) })
    await expect(integrations).toBeVisible()
    await expect(integrations.locator('article.local-module').first()).toBeVisible()

    await expect(navigation.getByRole('button', { name: 'Products & publishing' })).toHaveCount(0)
    await expect(navigation.getByRole('button', { name: 'Assignments' })).toHaveCount(0)
    await expect(navigation.getByRole('button', { name: 'My learning' })).toHaveCount(0)
  })

  test('Product Maintainer can manage educational products; Developer cannot', async ({ page }) => {
    await signInToDemo(page, 'product-maintainer@demo.uep')
    const navigation = page.getByRole('navigation', { name: 'Primary navigation' })
    await expect(navigation.getByRole('button')).toHaveText([
      'Product portfolio',
      'Products & publishing',
      'Product activity',
    ])
    await navigation.getByRole('button', { name: 'Products & publishing' }).click()
    await expect(page.getByRole('heading', { name: 'Educational products' })).toBeVisible()
    await expect(page.getByLabel('Product title')).toBeVisible()
    await expect(page.getByLabel('Description')).toBeVisible()

    await switchDemoUser(page)
    await signInToDemo(page, 'developer@demo.uep')
    await expect(page.getByRole('navigation', { name: 'Primary navigation' }).getByRole('button', { name: 'Products & publishing' })).toHaveCount(0)
  })

  test('Student and Client workspaces do not expose platform product controls', async ({ page }) => {
    await signInToDemo(page, 'student@demo.uep')
    let navigation = page.getByRole('navigation', { name: 'Primary navigation' })
    await expect(navigation.getByRole('button', { name: 'Products & publishing' })).toHaveCount(0)
    await expect(navigation.getByRole('button', { name: 'Integrations & webhooks' })).toHaveCount(0)
    await switchDemoUser(page)

    await signInToDemo(page, 'client@demo.uep')
    navigation = page.getByRole('navigation', { name: 'Primary navigation' })
    await expect(navigation.getByRole('button', { name: 'Products & publishing' })).toHaveCount(0)
    await expect(navigation.getByRole('button', { name: 'Integrations & webhooks' })).toHaveCount(0)
  })
})
