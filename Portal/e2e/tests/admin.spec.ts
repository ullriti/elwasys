import { test, expect, Page } from '@playwright/test';

/**
 * Admin auth & navigation E2E (test plan P3–P5): wrong-password handling,
 * logout, and navigating every admin section. Uses the seeded administrator
 * (admin / admin). Vaadin 7 has no stable ids, so we locate by CSS classes,
 * visible captions and — for navigation — the URI fragment the Vaadin
 * Navigator sets (#!users, #!devices, …).
 */

async function login(page: Page, user = 'admin', password = 'admin') {
  await page.goto('/');
  await page.locator('input.v-textfield:not([type="password"])').first().fill(user);
  await page.locator('input[type="password"]').first().fill(password);
  await page.locator('.v-button', { hasText: 'Login' }).first().click();
}

test('wrong password shows an error and stays on the login page', async ({ page }) => {
  await login(page, 'admin', 'definitely-wrong');

  await expect(page.locator('.v-Notification')).toContainText('Login fehlgeschlagen');
  // Still on the login page.
  await expect(page.locator('.v-button', { hasText: 'Login' }).first()).toBeVisible();
});

test('admin can log out again', async ({ page }) => {
  await login(page);
  await expect(page.getByText('Benutzergruppen', { exact: true })).toBeVisible();

  // The user menu is a Vaadin MenuBar whose top item is captioned with the
  // current user's name; open it and click "Logout".
  await page.locator('.v-menubar-menuitem').first().click();
  await page.locator('.v-menubar-menuitem', { hasText: 'Logout' }).click();

  // Back on the login screen.
  await expect(page.locator('input[type="password"]').first()).toBeVisible();
});

test('admin can navigate to every section', async ({ page }) => {
  await login(page);
  await expect(page.getByText('Benutzergruppen', { exact: true })).toBeVisible();

  const sections = [
    { caption: 'Benutzer', fragment: 'users' },
    { caption: 'Benutzergruppen', fragment: 'groups' },
    { caption: 'Programme', fragment: 'programs' },
    { caption: 'Geräte', fragment: 'devices' },
    { caption: 'Dashboard', fragment: 'dashboard' },
  ];

  const menu = page.locator('.valo-menuitems');
  for (const section of sections) {
    await menu.getByText(section.caption, { exact: true }).click();
    await expect(page).toHaveURL(new RegExp('#!' + section.fragment + '\\b'));
  }
});
