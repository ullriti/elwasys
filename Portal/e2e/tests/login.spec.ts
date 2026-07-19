import { test, expect, Page } from '@playwright/test';

/**
 * Login smoke test for the elwasys web portal.
 *
 * Uses the seeded administrator account (username "admin", password "admin",
 * created by Common/resources/database-init.sql). Vaadin 7 does not assign
 * stable element ids, so we locate by Vaadin's CSS classes and visible text.
 */

const USERNAME = 'admin';
const PASSWORD = 'admin';

/** The Vaadin text inputs on the public login form. */
function usernameInput(page: Page) {
  // TextField "Benutzername": a v-textfield that is not the password field.
  return page.locator('input.v-textfield:not([type="password"])').first();
}
function passwordInput(page: Page) {
  return page.locator('input[type="password"]').first();
}
function loginButton(page: Page) {
  return page.locator('.v-button', { hasText: 'Login' }).first();
}

test('login page renders', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveTitle(/Waschportal/i);
  await expect(usernameInput(page)).toBeVisible();
  await expect(passwordInput(page)).toBeVisible();
  await expect(loginButton(page)).toBeVisible();
});

test('admin can log in and reach the dashboard', async ({ page }) => {
  await page.goto('/');

  await usernameInput(page).fill(USERNAME);
  await passwordInput(page).fill(PASSWORD);
  await loginButton(page).click();

  // After a successful admin login the administrator menu is shown. These
  // captions do not exist on the login page, so they prove we are logged in.
  await expect(page.getByText('Benutzergruppen', { exact: true })).toBeVisible();
  await expect(page.getByText('Geräte', { exact: true })).toBeVisible();

  // ...and the login form is gone.
  await expect(loginButton(page)).toHaveCount(0);
});
