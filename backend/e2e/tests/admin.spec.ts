import { test, expect } from '@playwright/test';
import { loginAsAdmin, login, openUserMenu, ADMIN_USERNAME } from './helpers';

/**
 * Admin auth & navigation E2E (test plan P3-P5) - fachlicher Nachfolger von
 * Portal/e2e/tests/admin.spec.ts (Vaadin 7): wrong-password handling, logout, and navigating
 * every admin section.
 */

test('wrong password shows an error and stays on the login page (P3)', async ({ page }) => {
  await login(page, ADMIN_USERNAME, 'definitely-wrong');

  // Spring Security's default form-login failure handler redirects back to the login view with
  // "?error" (see LoginView#beforeEnter), which drives vaadin-login-form's built-in German
  // error banner (see LoginView#buildGermanI18n's errorMessage.title).
  await expect(page).toHaveURL(/\/login\?error/);
  await expect(page.getByText('Login fehlgeschlagen')).toBeVisible();
  // Still on the login page.
  await expect(page.getByRole('button', { name: 'Login', exact: true })).toBeVisible();
});

test('admin can log out again (P4)', async ({ page }) => {
  await loginAsAdmin(page);

  await openUserMenu(page, 'Logout');

  // Back on the login screen.
  await expect(page).toHaveURL(/\/login/);
  await expect(page.locator('input[name="password"]')).toBeVisible();
});

test('admin can navigate to every section (P5)', async ({ page }) => {
  await loginAsAdmin(page);

  // "Standorte" is a NEW, dedicated menu entry compared to the legacy portal (there it was only
  // reachable via a dialog on the dashboard) - a deliberate, auftraggeber-gewünschte
  // UX-improvement documented in kb/05-migration-plan.md ("Entscheidungen", AdminLayout
  // Javadoc), not a functional change; the section itself (LocationFormDialog) is exercised by
  // the P14 test in admin-crud.spec.ts.
  const sections = [
    { caption: 'Benutzer', path: 'admin/users' },
    { caption: 'Benutzergruppen', path: 'admin/user-groups' },
    { caption: 'Programme', path: 'admin/programs' },
    { caption: 'Geräte', path: 'admin/devices' },
    { caption: 'Standorte', path: 'admin/locations' },
    { caption: 'Dashboard', path: 'admin' },
  ];

  for (const section of sections) {
    const navItem = page.locator(`vaadin-side-nav-item[path="${section.path}"]`);
    // toContainText (not toHaveText): the item's accessible/full text also includes a hidden
    // "Toggle child items" label vaadin-side-nav ships for its (unused, no items here have
    // children) expand/collapse affordance.
    await expect(navItem).toContainText(section.caption);
    await navItem.click();
    await expect(page).toHaveURL(new RegExp(`/${section.path}(\\?|$)`));
    // SideNav marks the active entry with a "current" attribute (see AdminLayout).
    await expect(navItem).toHaveAttribute('current', '');
  }
});
