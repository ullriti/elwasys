import { test, expect } from '@playwright/test';
import { ADMIN_USERNAME, ADMIN_PASSWORD, login } from './helpers';

/**
 * Login smoke test for the elwasys backend Portal (Vaadin Flow) - test plan P1/P2, fachlicher
 * Nachfolger von Portal/e2e/tests/login.spec.ts (Vaadin 7). Uses the administrator account
 * (username "admin", password helpers.ADMIN_PASSWORD): the account itself is created by the
 * Flyway baseline migration (V1__baseline_schema_0_4_0.sql, die eingefrorene 0.4.0-Baseline
 * übernommen), but since Phase 5 AP2 (siehe docs/kb/05-migration-plan.md) the baseline's default
 * password is cleared for fresh installations by V7__remove_default_admin_password.sql - the
 * admin password used here is instead set explicitly by the E2E setup via the admin-cli
 * profile (../scripts/start-backend.sh, AdminPasswordCliRunner) and must be >= 8 characters
 * (Issue #44, ADR 0018), before the server starts.
 */

test('login page renders (P1)', async ({ page }) => {
  await page.goto('/login');
  await expect(page).toHaveTitle(/Waschportal/i);
  await expect(page.getByText('Waschportal').first()).toBeVisible();
  await expect(page.locator('input[name="username"]')).toBeVisible();
  await expect(page.locator('input[name="password"]')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Login', exact: true })).toBeVisible();
});

test('admin can log in and reach the dashboard (P2)', async ({ page }) => {
  await login(page, ADMIN_USERNAME, ADMIN_PASSWORD);

  // After a successful admin login, RootView forwards to /admin (AdminDashboardView) and the
  // admin side-nav is shown. These entries do not exist on the login page, so their presence
  // proves we are logged in as an administrator (not just logged in).
  await expect(page).toHaveURL(/\/admin(\?|$)/);
  await expect(page.locator('vaadin-side-nav-item[path="admin/user-groups"]')).toBeVisible();
  await expect(page.locator('vaadin-side-nav-item[path="admin/devices"]')).toBeVisible();

  // ...and the login form is gone.
  await expect(page.locator('vaadin-login-form')).toHaveCount(0);
});
