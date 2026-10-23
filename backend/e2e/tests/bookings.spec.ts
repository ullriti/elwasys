import { test, expect } from '@playwright/test';
import { loginAsAdmin, openAdminSection, runSql } from './helpers';

/**
 * Buchungsübersicht (test plan P34) - AdminBookingsView: alle Buchungen aller Benutzer, filterbar
 * nach Art (Einzahlungen/Auszahlungen) und Zeitraum. Die Buchungen werden direkt in die
 * E2E-Datenbank geseedet (Muster wie offline-incidents.spec.ts) und tragen einen eindeutigen
 * Buchungstext, damit die Prüfungen unabhängig von anderen Buchungen der Datenbank bleiben.
 */

const DB_NAME = process.env.E2E_DB_NAME || 'elwasys_backend_e2e';
const STAMP = Date.now();
const USER = 'e2e_bookings_user';
const IN_TEXT = `E2E-Einzahlung-${STAMP}`;
const OUT_TEXT = `E2E-Auszahlung-${STAMP}`;

const CLEANUP_SQL = `
    DELETE FROM credit_accounting WHERE user_id IN (SELECT id FROM users WHERE username = '${USER}');
    DELETE FROM users WHERE username = '${USER}';
  `;

function seed() {
  runSql(
    DB_NAME,
    `
    ${CLEANUP_SQL}
    INSERT INTO users (name, username, is_admin, blocked, deleted, group_id)
      VALUES ('E2E Bookings User', '${USER}', FALSE, FALSE, FALSE,
        (SELECT id FROM user_groups ORDER BY id LIMIT 1));
    INSERT INTO credit_accounting (user_id, amount, date, description)
      VALUES ((SELECT id FROM users WHERE username='${USER}'), 12.00, '2019-03-01 10:00', '${IN_TEXT}'),
             ((SELECT id FROM users WHERE username='${USER}'), -7.00, '2019-04-01 10:00', '${OUT_TEXT}');
  `,
  );
}

test.afterAll(() => runSql(DB_NAME, CLEANUP_SQL));

test.beforeEach(async ({ page }) => {
  seed();
  await loginAsAdmin(page);
  await openAdminSection(page, 'admin/bookings');
});

test('P34: Buchungsübersicht filtert nach Ein-/Auszahlung', async ({ page }) => {
  await page.getByRole('textbox', { name: 'Buchungen filtern' }).fill(String(STAMP));
  await expect(page.getByText(IN_TEXT)).toBeVisible();
  await expect(page.getByText(OUT_TEXT)).toBeVisible();

  await page.getByLabel('Art').click();
  await page.locator('vaadin-select-item', { hasText: 'Einzahlungen' }).click();
  await expect(page.getByText(IN_TEXT)).toBeVisible();
  await expect(page.getByText(OUT_TEXT)).toBeHidden();

  await page.getByLabel('Art').click();
  await page.locator('vaadin-select-item', { hasText: 'Auszahlungen' }).click();
  await expect(page.getByText(OUT_TEXT)).toBeVisible();
  await expect(page.getByText(IN_TEXT)).toBeHidden();
});

test('P34: Buchungsübersicht grenzt nach Zeitraum ein', async ({ page }) => {
  await page.getByRole('textbox', { name: 'Buchungen filtern' }).fill(String(STAMP));
  await page.getByLabel('Von').fill('01.04.2019');
  await page.keyboard.press('Enter');
  await expect(page.getByText(OUT_TEXT)).toBeVisible();
  await expect(page.getByText(IN_TEXT)).toBeHidden();
});
