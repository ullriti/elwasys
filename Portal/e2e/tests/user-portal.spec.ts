import { test, expect, Page } from '@playwright/test';

/**
 * Non-admin user frontend & permissions E2E (test plan P15–P19). Uses the
 * seeded non-admin accounts e2e_portal_user / e2e_pwchange_user, both with
 * password "test" (see start-portal.sh).
 */

async function login(page: Page, user: string, password: string) {
  await page.goto('/');
  await page.locator('input.v-textfield:not([type="password"])').first().fill(user);
  await page.locator('input[type="password"]').first().fill(password);
  await page.locator('.v-button', { hasText: 'Login' }).first().click();
}

/** Open the top-right user menu and click one of its entries. */
async function openUserMenu(page: Page, entry: string) {
  await page.locator('.user-menu .v-menubar-menuitem').first().click();
  await page.locator('.v-menubar-popup .v-menubar-menuitem', { hasText: entry }).click();
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

test('the password-forgot dialog opens from the login page (P19)', async ({ page }) => {
  await page.goto('/');
  await page.locator('.v-button', { hasText: 'Passwort vergessen?' }).first().click();

  const win = page.locator('.v-window').filter({ hasText: 'Passwort zurücksetzen' });
  await expect(win).toBeVisible();
  await expect(win.getByText('Email', { exact: true })).toBeVisible();
});

test('a user can change their own password and log in with it (P16)', async ({ page }) => {
  // Start from a known state: password "test".
  await login(page, 'e2e_pwchange_user', 'test');
  await expect(page.getByText('Guthaben', { exact: true })).toBeVisible();

  await openUserMenu(page, 'Passwort ändern');
  const win = page.locator('.v-window').filter({ hasText: 'Passwort ändern' });
  await expect(win).toBeVisible();

  const pw = win.locator('input[type="password"]');
  await pw.nth(0).fill('test');       // old
  await pw.nth(1).fill('geheim123');  // new
  await pw.nth(2).fill('geheim123');  // repeat
  await win.locator('.v-button', { hasText: 'OK' }).click();
  await expect(win).toHaveCount(0);

  // Log out and back in with the new password.
  await openUserMenu(page, 'Logout');
  await expect(page.locator('.v-button', { hasText: 'Login' })).toBeVisible();
  await login(page, 'e2e_pwchange_user', 'geheim123');
  await expect(page.getByText('Guthaben', { exact: true })).toBeVisible();

  // Restore the original password so the test stays repeatable.
  await openUserMenu(page, 'Passwort ändern');
  const win2 = page.locator('.v-window').filter({ hasText: 'Passwort ändern' });
  const pw2 = win2.locator('input[type="password"]');
  await pw2.nth(0).fill('geheim123');
  await pw2.nth(1).fill('test');
  await pw2.nth(2).fill('test');
  await win2.locator('.v-button', { hasText: 'OK' }).click();
  await expect(win2).toHaveCount(0);
});

test('a user can edit their settings and they persist (P17)', async ({ page }) => {
  await login(page, 'e2e_portal_user', 'test');
  await expect(page.getByText('Guthaben', { exact: true })).toBeVisible();

  const email = `e2e_portal_user_${Date.now()}@example.com`;

  await openUserMenu(page, 'Einstellungen');
  const win = page.locator('.v-window').filter({ hasText: 'Benutzer ändern' });
  await expect(win).toBeVisible();

  // The settings form has two text fields (Email, Pushover-Key) and one
  // checkbox (Email notification). Email is the first text field.
  await win.locator('input.v-textfield').first().fill(email);
  // Enable the email notification checkbox (needs a valid email, set above).
  await win.locator('input[type="checkbox"]').first().check({ force: true });
  await win.locator('.v-button', { hasText: 'OK' }).click();
  await expect(win).toHaveCount(0);

  // Re-open the settings and verify the values were persisted.
  await openUserMenu(page, 'Einstellungen');
  const win2 = page.locator('.v-window').filter({ hasText: 'Benutzer ändern' });
  await expect(win2).toBeVisible();
  await expect(win2.locator('input.v-textfield').first()).toHaveValue(email);
  await expect(win2.locator('input[type="checkbox"]').first()).toBeChecked();
});
