import { test, expect, Page, Locator } from '@playwright/test';
import { execFileSync } from 'child_process';

/**
 * Admin dashboard device status E2E (test plan P20): the "Frei"/"Besetzt"
 * status shown per device on the admin dashboard reflects whether the device
 * has a running execution in the database (AdminDashboardView.buildDeviceInfo
 * calls DataManager.getRunningExecution). This is DB-driven, so no live client
 * connection is required.
 *
 * We seed one occupied device (with a running execution) and one free device
 * directly in the database, then assert the dashboard renders their status.
 */

const OCCUPIED = 'E2E-Dash-Occupied';
const FREE = 'E2E-Dash-Free';

/** Run a SQL script against the elwasys DB as the postgres superuser. */
function sql(script: string) {
  execFileSync('sudo', ['-u', 'postgres', 'psql', '-q', '-v', 'ON_ERROR_STOP=1', '-d', 'elwasys'],
    { input: script, stdio: ['pipe', 'ignore', 'inherit'] });
}

function seed() {
  sql(`
    DELETE FROM credit_accounting WHERE execution_id IN
      (SELECT id FROM executions WHERE device_id IN
        (SELECT id FROM devices WHERE name IN ('${OCCUPIED}', '${FREE}')));
    DELETE FROM executions WHERE device_id IN
      (SELECT id FROM devices WHERE name IN ('${OCCUPIED}', '${FREE}'));
    DELETE FROM devices WHERE name IN ('${OCCUPIED}', '${FREE}');
    DELETE FROM programs WHERE name = 'E2E-Dash-Prog';
    DELETE FROM users WHERE username = 'e2e_dash_user';

    INSERT INTO programs (name, type, max_duration, free_duration, flagfall, rate,
        time_unit, auto_end, earliest_auto_end, enabled)
      VALUES ('E2E-Dash-Prog', 'FIXED', 3600, 0, 1.50, NULL, NULL, FALSE, 0, TRUE);
    INSERT INTO devices (name, position, location_id, fhem_name, fhem_switch_name,
        fhem_power_name, deconz_uuid, auto_end_power_threashold, auto_end_wait_time, enabled)
      VALUES ('${OCCUPIED}', 90, (SELECT id FROM locations WHERE name='Default'),
        'e2e-dash-1', 'e2e-dash-1sw', 'e2e-dash-1pw', '', 0.5, 20, TRUE);
    INSERT INTO devices (name, position, location_id, fhem_name, fhem_switch_name,
        fhem_power_name, deconz_uuid, auto_end_power_threashold, auto_end_wait_time, enabled)
      VALUES ('${FREE}', 91, (SELECT id FROM locations WHERE name='Default'),
        'e2e-dash-2', 'e2e-dash-2sw', 'e2e-dash-2pw', '', 0.5, 20, TRUE);
    INSERT INTO users (name, username, is_admin, blocked, deleted, group_id)
      VALUES ('E2E Dash User', 'e2e_dash_user', FALSE, FALSE, FALSE,
        (SELECT id FROM user_groups ORDER BY id LIMIT 1));

    -- A running execution occupies the first device.
    INSERT INTO executions (device_id, program_id, user_id, start, finished)
      VALUES ((SELECT id FROM devices WHERE name='${OCCUPIED}'),
              (SELECT id FROM programs WHERE name='E2E-Dash-Prog'),
              (SELECT id FROM users WHERE username='e2e_dash_user'), NOW(), FALSE);
  `);
}

function cleanup() {
  sql(`
    DELETE FROM credit_accounting WHERE execution_id IN
      (SELECT id FROM executions WHERE device_id IN
        (SELECT id FROM devices WHERE name IN ('${OCCUPIED}', '${FREE}')));
    DELETE FROM executions WHERE device_id IN
      (SELECT id FROM devices WHERE name IN ('${OCCUPIED}', '${FREE}'));
    DELETE FROM devices WHERE name IN ('${OCCUPIED}', '${FREE}');
    DELETE FROM programs WHERE name = 'E2E-Dash-Prog';
    DELETE FROM users WHERE username = 'e2e_dash_user';
  `);
}

async function loginAsAdmin(page: Page) {
  await page.goto('/');
  await page.locator('input.v-textfield:not([type="password"])').first().fill('admin');
  await page.locator('input[type="password"]').first().fill('admin');
  await page.locator('.v-button', { hasText: 'Login' }).first().click();
  await expect(page.getByText('Benutzergruppen', { exact: true })).toBeVisible();
}

/** The dashboard device tile (a .device block) whose name matches. */
function deviceTile(page: Page, name: string): Locator {
  return page.locator('.device').filter({ has: page.getByText(name, { exact: true }) });
}

test.beforeAll(() => seed());
test.afterAll(() => cleanup());

test('the dashboard shows device occupancy from the database (P20)', async ({ page }) => {
  await loginAsAdmin(page);
  await page.locator('.valo-menuitems').getByText('Dashboard', { exact: true }).click();
  await expect(page).toHaveURL(/#!dashboard\b/);

  // The device with a running execution is "Besetzt"; the other is "Frei".
  await expect(deviceTile(page, OCCUPIED).locator('.device-occupied')).toHaveText('Besetzt');
  await expect(deviceTile(page, FREE).locator('.device-free')).toHaveText('Frei');
});
