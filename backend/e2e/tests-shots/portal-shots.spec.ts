import { test, expect, Page } from '@playwright/test';
import {
  loginAsAdmin,
  login,
  openAdminSection,
  rowActionButton,
  dialog,
  runSql,
} from '../tests/helpers';

/**
 * Screenshot-Durchlauf über ALLE Seiten und Dialoge des Portals - kein Regressionstest, sondern
 * ein Werkzeug zur optischen Abnahme des UI-Redesigns v2 durch den Auftraggeber.
 *
 * Bewusst NICHT unter tests/: die Playwright-Konfiguration zieht nur tests/ als testDir ein, das
 * hier läuft also nur, wenn man es ausdrücklich anstößt (siehe README-Hinweis im Worklog). Der
 * Durchlauf seedet seine eigenen Daten und räumt sie wieder ab.
 */

const DIR = process.env.SHOT_DIR || '/tmp/portal-shots';
const DB_NAME = process.env.E2E_DB_NAME || 'elwasys_backend_e2e';

const PREFIX = 'ZZS';
const DEV_RUNNING = `${PREFIX}-Waschmaschine 1`;
const DEV_FREE = `${PREFIX}-Waschmaschine 2`;
const DEV_OFF = `${PREFIX}-Trockner`;
const PROGRAM = `${PREFIX}-Buntwäsche 40°`;
const PROGRAM_DYN = `${PREFIX}-Trocknen nach Zeit`;
const GROUP = `${PREFIX}-Mieter`;
const LOCATION = `${PREFIX}-Waschküche Nord`;
// Der Benutzerbereich braucht einen Account MIT Passwort - global-setup.ts legt
// e2e_portal_user/testpass1 an, deshalb haengen Buchungen und Ausfuehrungen an ihm.
const USER = 'E2E Portal User';
const USERNAME = 'e2e_portal_user';

let shotIndex = 0;

/** Nimmt einen Screenshot auf; die laufende Nummer hält die Dateien in Ablaufreihenfolge. */
async function shot(page: Page, name: string) {
  shotIndex += 1;
  const num = String(shotIndex).padStart(2, '0');
  await page.waitForTimeout(500);
  await page.screenshot({ path: `${DIR}/${num}-${name}.png` });
}

/** Wartet, bis der Kopfbalken markenblau gerendert ist - sonst entstehen halbfertige Bilder. */
async function settled(page: Page) {
  await expect
    .poll(
      async () =>
        page.evaluate(() => {
          const nav = document
            .querySelector('vaadin-app-layout')
            ?.shadowRoot?.querySelector('[part~="navbar"]') as HTMLElement | null;
          return nav ? getComputedStyle(nav).backgroundColor : '';
        }),
      { timeout: 15000 },
    )
    .toBe('rgb(68, 136, 221)');
}

/** Öffnet einen Dialog, fotografiert ihn und schließt ihn wieder. */
async function captureDialog(page: Page, name: string, open: () => Promise<void>) {
  await open();
  const win = dialog(page);
  await expect(win).toBeVisible({ timeout: 10000 });
  await shot(page, name);
  await page.keyboard.press('Escape');
  await expect(page.locator('vaadin-dialog-overlay')).toHaveCount(0, { timeout: 10000 });
}

function seed() {
  runSql(
    DB_NAME,
    `
    DELETE FROM credit_accounting WHERE user_id IN (SELECT id FROM users WHERE username='${USERNAME}');
    DELETE FROM executions WHERE device_id IN (SELECT id FROM devices WHERE name LIKE '${PREFIX}-%');
    DELETE FROM terminal_offline_incidents WHERE incident_key LIKE '${PREFIX}-%';
    DELETE FROM devices WHERE name LIKE '${PREFIX}-%';
    DELETE FROM programs WHERE name LIKE '${PREFIX}-%';
    DELETE FROM locations WHERE name='${LOCATION}';
    DELETE FROM user_groups WHERE name='${GROUP}';

    INSERT INTO user_groups (name) VALUES ('${GROUP}');
    INSERT INTO locations (name, offline_max_duration) VALUES ('${LOCATION}', 120);

    INSERT INTO programs (name, type, max_duration, free_duration, flagfall, rate, time_unit,
        auto_end, earliest_auto_end, enabled)
      VALUES ('${PROGRAM}', 'FIXED', 5400, 0, 2.50, NULL, NULL, TRUE, 1200, TRUE);
    INSERT INTO programs (name, type, max_duration, free_duration, flagfall, rate, time_unit,
        auto_end, earliest_auto_end, enabled)
      VALUES ('${PROGRAM_DYN}', 'DYNAMIC', 3600, 300, 0.50, 0.10, 600, FALSE, 0, TRUE);

    -- Kartennummer am bestehenden Benutzer, damit die Spalte in der Liste nicht leer bleibt.
    UPDATE users SET card_ids = '{04A1B2C3}' WHERE username='${USERNAME}';

    INSERT INTO devices (name, position, location_id, fhem_name, fhem_switch_name, fhem_power_name,
        deconz_uuid, auto_end_power_threshold, auto_end_wait_time, enabled) VALUES
      ('${DEV_RUNNING}', 91, (SELECT id FROM locations WHERE name='Default'),
        'zzs1', 'zzs1sw', 'zzs1pw', '', 0.5, 20, TRUE),
      ('${DEV_FREE}', 92, (SELECT id FROM locations WHERE name='Default'),
        'zzs2', 'zzs2sw', 'zzs2pw', '', 0.5, 20, TRUE),
      ('${DEV_OFF}', 93, (SELECT id FROM locations WHERE name='Default'),
        'zzs3', 'zzs3sw', 'zzs3pw', '', 0.5, 20, FALSE);

    -- Laufende Ausfuehrung (vor 25 Minuten gestartet, Programm laeuft 90 Minuten).
    INSERT INTO executions (device_id, program_id, user_id, start, finished)
      VALUES ((SELECT id FROM devices WHERE name='${DEV_RUNNING}'),
              (SELECT id FROM programs WHERE name='${PROGRAM}'),
              (SELECT id FROM users WHERE username='${USERNAME}'),
              NOW() - INTERVAL '25 minutes', FALSE);

    -- Abgeschlossene Ausfuehrungen fuer den Verlauf.
    INSERT INTO executions (device_id, program_id, user_id, start, stop, finished)
      SELECT (SELECT id FROM devices WHERE name='${DEV_RUNNING}'),
             (SELECT id FROM programs WHERE name='${PROGRAM}'),
             (SELECT id FROM users WHERE username='${USERNAME}'),
             NOW() - (n || ' hours')::interval,
             NOW() - (n || ' hours')::interval + INTERVAL '82 minutes', TRUE
      FROM generate_series(2, 9) AS n;

    -- Buchungen fuer Guthaben-Kachel, Umsatz-Dialog und die Buchungstabelle des Benutzers.
    INSERT INTO credit_accounting (user_id, date, amount, description)
      VALUES ((SELECT id FROM users WHERE username='${USERNAME}'),
              NOW() - INTERVAL '20 days', 30.00, 'Einzahlung');
    INSERT INTO credit_accounting (user_id, date, amount, description)
      SELECT (SELECT id FROM users WHERE username='${USERNAME}'),
             NOW() - (n || ' days')::interval, -2.50, 'Waschgang ${PROGRAM}'
      FROM generate_series(1, 6) AS n;

    -- Offene und quittierte Offline-Vorfaelle.
    INSERT INTO terminal_offline_incidents (incident_key, location_id, kind, entry_type,
        idempotency_key, user_id, charged_price, reason, occurred_at, reported_at)
      VALUES ('${PREFIX}-open', (SELECT id FROM locations WHERE name='Default'),
        'DEAD_LETTER', 'FINISH', '${PREFIX}-idem-open',
        (SELECT id FROM users WHERE username='${USERNAME}'), 3.50,
        'Buchung nach Netzausfall nicht zustellbar', NOW() - INTERVAL '2 hours', NOW() - INTERVAL '1 hour');
    INSERT INTO terminal_offline_incidents (incident_key, location_id, kind, entry_type,
        idempotency_key, user_id, charged_price, reason, occurred_at, reported_at,
        acknowledged_at, acknowledged_by)
      VALUES ('${PREFIX}-ack', (SELECT id FROM locations WHERE name='Default'),
        'GHOST_EXECUTION', 'ABORT', '${PREFIX}-idem-ack', NULL, 1.20,
        'Ausfuehrung ohne Gegenstueck im Backend', NOW() - INTERVAL '3 hours',
        NOW() - INTERVAL '2 hours', NOW() - INTERVAL '1 hour', 'Administrator');
  `,
  );
}

function cleanup() {
  runSql(
    DB_NAME,
    `
    DELETE FROM credit_accounting WHERE user_id IN (SELECT id FROM users WHERE username='${USERNAME}');
    DELETE FROM executions WHERE device_id IN (SELECT id FROM devices WHERE name LIKE '${PREFIX}-%');
    DELETE FROM terminal_offline_incidents WHERE incident_key LIKE '${PREFIX}-%';
    DELETE FROM devices WHERE name LIKE '${PREFIX}-%';
    DELETE FROM programs WHERE name LIKE '${PREFIX}-%';
    UPDATE users SET card_ids = '{}' WHERE username='${USERNAME}';
    DELETE FROM locations WHERE name='${LOCATION}';
    DELETE FROM user_groups WHERE name='${GROUP}';
  `,
  );
}

test.describe.configure({ mode: 'serial' });

test.beforeAll(() => seed());
test.afterAll(() => cleanup());

test('oeffentliche Seiten', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });

  await page.goto('/login');
  await expect(page.locator('input[name="username"]')).toBeVisible();
  await shot(page, 'login');

  // Fehlerzustand des Logins.
  await login(page, 'admin', 'falsches-passwort');
  await expect(page.getByText('Login fehlgeschlagen')).toBeVisible();
  await shot(page, 'login-fehler');

  await page.goto('/login');
  await captureDialog(page, 'dialog-passwort-vergessen', async () => {
    await page.getByRole('button', { name: 'Passwort vergessen?' }).click();
  });

  await page.goto('/reset-password?key=abgelaufener-schluessel');
  await expect(page.getByText('Dieser Link zum Zurücksetzen des Passworts ist ungültig oder abgelaufen.'))
    .toBeVisible();
  await shot(page, 'passwort-zuruecksetzen-ungueltig');
});

test('Admin-Bereich', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1100 });
  await loginAsAdmin(page);

  // --- Dashboard -----------------------------------------------------------
  await openAdminSection(page, 'admin');
  await settled(page);
  await page.waitForTimeout(1200);
  await shot(page, 'dashboard');

  await captureDialog(page, 'dialog-log-viewer', async () => {
    await page.locator('.dashboard-location-header').first()
      .getByRole('button', { name: 'Log anzeigen' }).click();
  });

  // --- Benutzer ------------------------------------------------------------
  await openAdminSection(page, 'admin/users');
  await settled(page);
  await shot(page, 'liste-benutzer');

  // Filter in Aktion.
  const filter = page.locator('.list-filter').first();
  await filter.click();
  await filter.fill('Maria');
  await page.waitForTimeout(900);
  await shot(page, 'liste-benutzer-gefiltert');
  await filter.fill('');
  await page.waitForTimeout(900);

  await captureDialog(page, 'dialog-benutzer-erstellen', async () => {
    await page.getByRole('button', { name: 'Neu' }).click();
  });
  await captureDialog(page, 'dialog-benutzer-bearbeiten', async () => {
    (await rowActionButton(page, new RegExp(USER), 0)).click();
  });
  await captureDialog(page, 'dialog-guthaben', async () => {
    (await rowActionButton(page, new RegExp(USER), 1)).click();
  });
  await captureDialog(page, 'dialog-umsaetze', async () => {
    (await rowActionButton(page, new RegExp(USER), 2)).click();
  });
  await captureDialog(page, 'dialog-loeschen-bestaetigen', async () => {
    (await rowActionButton(page, new RegExp(USER), 3)).click();
  });

  // --- Benutzergruppen -----------------------------------------------------
  await openAdminSection(page, 'admin/user-groups');
  await settled(page);
  await shot(page, 'liste-benutzergruppen');
  await captureDialog(page, 'dialog-benutzergruppe', async () => {
    await page.getByRole('button', { name: 'Neu' }).click();
  });

  // --- Programme -----------------------------------------------------------
  await openAdminSection(page, 'admin/programs');
  await settled(page);
  await shot(page, 'liste-programme');
  await captureDialog(page, 'dialog-programm-statisch', async () => {
    await page.getByRole('button', { name: 'Neu' }).click();
  });
  // Dynamischer Typ: der Segmented Control schaltet die preisabhaengigen Felder um.
  await page.getByRole('button', { name: 'Neu' }).click();
  await expect(dialog(page)).toBeVisible();
  await dialog(page).getByRole('radio', { name: 'Dynamisch' }).click({ force: true });
  await page.waitForTimeout(400);
  await shot(page, 'dialog-programm-dynamisch');
  await page.keyboard.press('Escape');
  await expect(page.locator('vaadin-dialog-overlay')).toHaveCount(0);

  // --- Geraete -------------------------------------------------------------
  await openAdminSection(page, 'admin/devices');
  await settled(page);
  await shot(page, 'liste-geraete');
  await captureDialog(page, 'dialog-geraet', async () => {
    await page.getByRole('button', { name: 'Neu' }).click();
  });

  // --- Standorte -----------------------------------------------------------
  await openAdminSection(page, 'admin/locations');
  await settled(page);
  await shot(page, 'liste-standorte');
  await captureDialog(page, 'dialog-standort', async () => {
    await page.getByRole('button', { name: 'Neu' }).click();
  });

  // --- Offline-Vorfaelle ---------------------------------------------------
  await openAdminSection(page, 'admin/offline-incidents');
  await settled(page);
  await shot(page, 'liste-offline-vorfaelle');

  // --- Benutzermenue -------------------------------------------------------
  await page.locator('vaadin-menu-bar vaadin-menu-bar-button').first().click();
  await page.waitForTimeout(500);
  await shot(page, 'benutzermenue');
  await page.getByRole('menuitem', { name: 'Einstellungen', exact: true }).click();
  await expect(dialog(page)).toBeVisible();
  await shot(page, 'dialog-einstellungen');
  await page.keyboard.press('Escape');
  await expect(page.locator('vaadin-dialog-overlay')).toHaveCount(0);

  await page.locator('vaadin-menu-bar vaadin-menu-bar-button').first().click();
  await page.getByRole('menuitem', { name: 'Passwort ändern', exact: true }).click();
  await expect(dialog(page)).toBeVisible();
  await shot(page, 'dialog-passwort-aendern');
  await page.keyboard.press('Escape');
});

test('Benutzerbereich', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1000 });
  await login(page, USERNAME, 'testpass1');
  await expect(page).toHaveURL(/\/user(\?|$)/);
  await settled(page);
  await shot(page, 'benutzer-uebersicht');
});
