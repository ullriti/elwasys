import { test, expect } from '@playwright/test';
import { login } from '../tests/helpers';

/**
 * POST-DEPLOY-SMOKE-Teilmenge (Phase 6 AP6, siehe docs/kb/05-migration-plan.md "Phase 6" und
 * docs/kb/06-ui-tests.md) - eine SCHLANKE, strikt READ-ONLY Liveness-Prüfung, die nach einem
 * Deployment gegen die frisch deployte, laufende Umgebung läuft (baseURL aus E2E_BASE_URL,
 * siehe playwright.smoke.config.ts).
 *
 * Bewusst KEINE Mutationen, KEINE Abhängigkeit von global-setup-Fixtures und KEINE Annahmen
 * über konkrete Produktivdaten (Zeilenzahlen, bestimmte Geräte/Benutzer): Die Umgebung enthält
 * beliebige echte Daten. Geprüft wird nur, dass die tragenden Schichten leben:
 *   - der Login-Screen rendert (Web/Vaadin-Frontend erreichbar)   -> P1-artig
 *   - der Admin kann sich einloggen und erreicht das Dashboard    -> P2-artig (Auth + DB leben)
 *   - die Kern-Admin-Sektionen rendern (nur lesende Navigation)   -> P5-artig
 *   - das Dashboard rendert (Portal<->DB-Pfad lebt)
 *
 * Admin-Credentials kommen aus SMOKE_ADMIN_USER / SMOKE_ADMIN_PASSWORD (Default admin/admin) -
 * so kann das Gate gegen eine Umgebung mit produktivem Admin-Passwort laufen, ohne den Code
 * anzufassen. `login()` wird aus der Haupt-Suite (../tests/helpers.ts) wiederverwendet.
 */

const ADMIN_USER = process.env.SMOKE_ADMIN_USER || 'admin';
const ADMIN_PASSWORD = process.env.SMOKE_ADMIN_PASSWORD || 'admin';

// Kern-Admin-Sektionen, deren bloßes Rendern (Route erreichbar, aktive Navigation) die
// Funktionsfähigkeit belegt - ohne über konkrete Datenzeilen zu urteilen.
const CORE_SECTIONS = [
  { caption: 'Benutzer', path: 'admin/users' },
  { caption: 'Geräte', path: 'admin/devices' },
  { caption: 'Programme', path: 'admin/programs' },
];

test('smoke: login page renders', async ({ page }) => {
  await page.goto('/login');
  await expect(page).toHaveTitle(/Waschportal/i);
  await expect(page.locator('input[name="username"]')).toBeVisible();
  await expect(page.locator('input[name="password"]')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Login', exact: true })).toBeVisible();
});

test('smoke: admin can log in and reach the dashboard', async ({ page }) => {
  await login(page, ADMIN_USER, ADMIN_PASSWORD);

  // Erfolgreicher Admin-Login leitet auf /admin weiter und zeigt die Admin-Side-Nav (diese
  // Einträge existieren auf der Login-Seite nicht - ihr Vorhandensein beweist eine
  // authentifizierte Admin-Session, also lebt der Auth-Pfad gegen die DB).
  await expect(page).toHaveURL(/\/admin(\?|$)/);
  await expect(page.locator('vaadin-side-nav-item[path="admin/devices"]')).toBeVisible();
  await expect(page.locator('vaadin-login-form')).toHaveCount(0);
});

test('smoke: core admin sections render (read-only navigation)', async ({ page }) => {
  await login(page, ADMIN_USER, ADMIN_PASSWORD);
  await expect(page.locator('vaadin-side-nav-item[path="admin/users"]')).toBeVisible();

  for (const section of CORE_SECTIONS) {
    const navItem = page.locator(`vaadin-side-nav-item[path="${section.path}"]`);
    await expect(navItem).toContainText(section.caption);
    await navItem.click();
    await expect(page).toHaveURL(new RegExp(`/${section.path}(\\?|$)`));
    // SideNav markiert den aktiven Eintrag mit "current" (siehe AdminLayout) - beweist, dass die
    // Route tatsächlich geladen hat, ohne über den Inhalt (Anzahl Zeilen o.ä.) zu urteilen.
    await expect(navItem).toHaveAttribute('current', '');
  }
});

test('smoke: dashboard renders (portal<->db path alive)', async ({ page }) => {
  await login(page, ADMIN_USER, ADMIN_PASSWORD);

  // Zum Dashboard navigieren und prüfen, dass es rendert. KEINE Annahme über konkrete Geräte -
  // das Dashboard kann beliebig viele (oder null) Geräte-Panels zeigen; entscheidend ist, dass
  // die datengetriebene View ohne Fehler lädt (Portal<->DB-Pfad lebt).
  const dashboardNav = page.locator('vaadin-side-nav-item[path="admin"]');
  await dashboardNav.click();
  await expect(page).toHaveURL(/\/admin(\?|$)/);
  await expect(dashboardNav).toHaveAttribute('current', '');
});
