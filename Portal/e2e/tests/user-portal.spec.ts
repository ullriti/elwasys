import { test, expect, Page } from '@playwright/test';

/**
 * Non-admin user frontend & permissions E2E (test plan P15 + P18). Uses the
 * seeded non-admin account e2e_portal_user / "test" (see start-portal.sh).
 */

async function login(page: Page, user: string, password: string) {
  await page.goto('/');
  await page.locator('input.v-textfield:not([type="password"])').first().fill(user);
  await page.locator('input[type="password"]').first().fill(password);
  await page.locator('.v-button', { hasText: 'Login' }).first().click();
}

test('a non-admin user reaches the user dashboard without admin access', async ({ page }) => {
  await login(page, 'e2e_portal_user', 'test');

  // The user dashboard shows the personal credit and the "Übersicht" menu.
  await expect(page.getByText('Guthaben', { exact: true })).toBeVisible();
  await expect(page.getByText('Übersicht', { exact: true })).toBeVisible();

  // Admin-only sections must not be available to a normal user.
  await expect(page.getByText('Benutzergruppen', { exact: true })).toHaveCount(0);
  await expect(page.getByText('Geräte', { exact: true })).toHaveCount(0);
});
