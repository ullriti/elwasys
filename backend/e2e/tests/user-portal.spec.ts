import { test, expect } from '@playwright/test';
import { login, openUserMenu, dialog, expectNoDialog } from './helpers';

/**
 * Non-admin user frontend & permissions E2E (test plan P15-P19) - fachlicher Nachfolger von
 * Portal/e2e/tests/user-portal.spec.ts (Vaadin 7). Uses the seeded non-admin accounts
 * e2e_portal_user / e2e_pwchange_user, both with password "testpass1" (see ../global-setup.ts).
 */

test('a non-admin user reaches the user dashboard without admin access (P15/P18)', async ({ page }) => {
  await login(page, 'e2e_portal_user', 'testpass1');

  // The user dashboard shows the personal credit tile and the "Übersicht" side-nav entry (see
  // UserDashboardView/UserLayout).
  await expect(page).toHaveURL(/\/user(\?|$)/);
  await expect(page.getByText('Guthaben', { exact: true })).toBeVisible();
  await expect(page.getByText('Übersicht', { exact: true })).toBeVisible();

  // Admin-only sections must not be available to a normal user (P18) - neither the side-nav
  // caption nor the route itself (NavigationAccessControl, see AdminLayout Javadoc).
  await expect(page.getByText('Benutzergruppen', { exact: true })).toHaveCount(0);
  await expect(page.getByText('Geräte', { exact: true })).toHaveCount(0);

  // Direct URL access is blocked too, not just the menu entry (Vaadin's
  // NavigationAccessControl denies client-side navigation to an @RolesAllowed("ADMIN") route
  // for a ROLE_USER principal - it does not redirect, it renders an in-place "access denied"
  // notice instead, so the user grid itself must not appear).
  await page.goto('/admin/users');
  await expect(page.getByText('Could not navigate')).toBeVisible();
  await expect(page.locator('vaadin-grid')).toHaveCount(0);
});

test('the password-forgot dialog opens from the login page and handles errors without crashing (P19)', async ({
  page,
}) => {
  await page.goto('/login');
  await page.getByRole('button', { name: 'Passwort vergessen?' }).click();

  const win = dialog(page);
  await expect(win.locator('h2[slot="title"]')).toHaveText('Passwort zurücksetzen');
  await expect(win.getByLabel('Email', { exact: true })).toBeVisible();

  // No real mail is sent in the test environment (SMTP is not configured, see
  // backend/e2e/scripts/start-backend.sh). Seit Issue #24/ADR 0018 verrät der Reset die
  // Kontenexistenz NICHT mehr: eine unbekannte Adresse führt (wie eine bekannte) zur SELBEN
  // neutralen Bestätigung, still ohne Fehler - der Dialog schließt und zeigt die neutrale
  // Meldung (statt der früheren "kein Benutzer gefunden"-Fehlermeldung).
  await win.getByLabel('Email', { exact: true }).fill('nobody-e2e@example.com');
  await win.getByRole('button', { name: 'OK' }).click();

  await expect(
    page.getByText('Falls ein Konto zu dieser Adresse existiert, wurde eine Email versandt.'),
  ).toBeVisible();
  await expectNoDialog(page);
});

test('a user can change their own password and log in with it (P16)', async ({ page }) => {
  // Start from a known state: password "testpass1" (>= 8 Zeichen, Issue #44/ADR 0018 - der
  // Ändern-Dialog erzwingt die Mindestlänge). Per docs/kb/05-migration-plan.md, this is the ONE
  // test-plan case where the legacy-portal half no longer applies (the new backend stores
  // Argon2id, not SHA1) - only the "log in again with the new password" assertion carries
  // over, now against the new portal exclusively.
  await login(page, 'e2e_pwchange_user', 'testpass1');
  await expect(page.getByText('Guthaben', { exact: true })).toBeVisible();

  await openUserMenu(page, 'Passwort ändern');
  let win = dialog(page);
  await expect(win.locator('h2[slot="title"]')).toHaveText('Passwort ändern - E2E PwChange User');

  const pw = win.locator('input[type="password"]');
  await pw.nth(0).fill('testpass1'); // old
  await pw.nth(1).fill('geheim123'); // new
  await pw.nth(2).fill('geheim123'); // repeat
  await win.getByRole('button', { name: 'OK' }).click();
  await expectNoDialog(page);

  // Log out and back in with the new password.
  await openUserMenu(page, 'Logout');
  await expect(page).toHaveURL(/\/login/);
  await login(page, 'e2e_pwchange_user', 'geheim123');
  await expect(page.getByText('Guthaben', { exact: true })).toBeVisible();

  // Restore the original password so the test stays repeatable (ebenfalls >= 8 Zeichen).
  await openUserMenu(page, 'Passwort ändern');
  win = dialog(page);
  const pw2 = win.locator('input[type="password"]');
  await pw2.nth(0).fill('geheim123');
  await pw2.nth(1).fill('testpass1');
  await pw2.nth(2).fill('testpass1');
  await win.getByRole('button', { name: 'OK' }).click();
  await expectNoDialog(page);
});

test('a user can edit their settings and they persist (P17)', async ({ page }) => {
  await login(page, 'e2e_portal_user', 'testpass1');
  await expect(page.getByText('Guthaben', { exact: true })).toBeVisible();

  const email = `e2e_portal_user_${Date.now()}@example.com`;

  await openUserMenu(page, 'Einstellungen');
  let win = dialog(page);
  await expect(win.locator('h2[slot="title"]')).toHaveText('Benutzer ändern - E2E Portal User');

  await win.getByLabel('Email', { exact: true }).fill(email);
  await win.getByLabel('Email-Benachrichtigung').check({ force: true });
  await win.getByRole('button', { name: 'OK' }).click();
  await expectNoDialog(page);

  // Re-open the settings and verify the values were persisted.
  await openUserMenu(page, 'Einstellungen');
  win = dialog(page);
  await expect(win.getByLabel('Email', { exact: true })).toHaveValue(email);
  await expect(win.getByLabel('Email-Benachrichtigung')).toBeChecked();
  await win.getByRole('button', { name: 'Abbrechen' }).click();
});
