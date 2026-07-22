import { test, expect, Page } from '@playwright/test';
import {
  loginAsAdmin,
  openAdminSection,
  dialog,
  expectNoDialog,
  pickCombo,
  gridRowCells,
  rowActionButton,
  confirmDeletion,
} from './helpers';

/**
 * Admin CRUD E2E (test plan P6-P14) - fachlicher Nachfolger von
 * Portal/e2e/tests/admin-crud.spec.ts (Vaadin 7): create/edit/delete flows through the
 * Vaadin-Flow admin dialogs (backend/.../ui/admin/dialog/*), asserted against the resulting
 * vaadin-grid rows.
 */

/** Create a user (group "Default", no password mail) via the Vaadin UI - mirrors
 * Portal/e2e/tests/admin-crud.spec.ts's createUser(). */
async function createUser(page: Page, name: string, username: string): Promise<void> {
  await openAdminSection(page, 'admin/users');
  await page.getByRole('button', { name: 'Neu' }).click();
  const win = dialog(page);
  await expect(win.locator('h2[slot="title"]')).toHaveText('Benutzer erstellen');

  await win.getByLabel('Name', { exact: true }).fill(name);
  await win.getByLabel('Username', { exact: true }).fill(username);

  // Uncheck "send a new password by email" so email is not required and no mail is sent (SMTP
  // is not configured in the test environment) - 1:1 wie im Alt-Test.
  const sendPw = win.getByLabel('Sende dem Benutzer per Email ein neues Passwort');
  await sendPw.uncheck({ force: true });

  await pickCombo(page, win, 'Benutzergruppe', 'Default');

  await win.getByRole('button', { name: 'Erstellen' }).click();
  await expectNoDialog(page);
}

/** Create a user group with no discount via the Vaadin UI. */
async function createGroup(page: Page, groupName: string): Promise<void> {
  await openAdminSection(page, 'admin/user-groups');
  await page.getByRole('button', { name: 'Neu' }).click();
  const win = dialog(page);
  await expect(win.locator('h2[slot="title"]')).toHaveText('Gruppe erstellen');

  await win.getByLabel('Name', { exact: true }).fill(groupName);
  await win.getByRole('radio', { name: 'Keiner' }).click({ force: true });

  await win.getByRole('button', { name: 'Erstellen' }).click();
  await expectNoDialog(page);
}

test.beforeEach(async ({ page }) => {
  await loginAsAdmin(page);
});

test('admin can create a user (P6)', async ({ page }) => {
  const stamp = Date.now();
  const name = `E2E UI User ${stamp}`;

  await createUser(page, name, `e2e_ui_${stamp}`);

  await expect(page.getByText(name, { exact: true })).toBeVisible();
});

test('admin can block a user (P7)', async ({ page }) => {
  const stamp = Date.now();
  const name = `E2E Sperre ${stamp}`;
  await createUser(page, name, `e2e_block_${stamp}`);

  // AdminUsersView#actionButtons order: Bearbeiten(0), Guthaben aufladen(1),
  // Umsätze ansehen(2), Löschen(3).
  const openEdit = async () => {
    const editBtn = await rowActionButton(page, name, 0);
    await editBtn.click();
    const win = dialog(page);
    await expect(win.locator('h2[slot="title"]')).toHaveText('Benutzer bearbeiten');
    return win;
  };

  // Block the user.
  let win = await openEdit();
  await win.getByLabel('Gesperrt').check({ force: true });
  await win.getByRole('button', { name: 'Speichern' }).click();
  await expectNoDialog(page);

  // Re-open the editor and confirm the "blocked" flag persisted.
  win = await openEdit();
  await expect(win.getByLabel('Gesperrt')).toBeChecked();
  await win.getByRole('button', { name: 'Abbrechen' }).click();

  // The grid's status badge reflects the block too (statusBadge in AdminUsersView).
  const cells = await gridRowCells(page, name);
  await expect(cells[5]).toHaveText('Gesperrt');
});

test('admin can top up a user credit (P8)', async ({ page }) => {
  const stamp = Date.now();
  const name = `E2E Guthaben ${stamp}`;
  await createUser(page, name, `e2e_credit_${stamp}`);

  const creditBtn = await rowActionButton(page, name, 1); // "Guthaben aufladen"
  await creditBtn.click();

  const win = dialog(page);
  await expect(win.locator('h2[slot="title"]')).toHaveText(`Guthaben von ${name}`);

  // Default action is a deposit ("Einzahlung"); just enter the amount and book.
  await win.getByLabel('Betrag').fill('5');
  await win.getByRole('button', { name: 'Buchen' }).click();
  await expectNoDialog(page);

  // The user's row now shows the credited amount (Locale.GERMANY currency formatting, see
  // AdminUsersView#formatCredit - comma decimal separator, non-breaking space before "€").
  const cells = await gridRowCells(page, name);
  await expect(cells[4]).toContainText('5,00');
});

test('admin can create a user group (P9)', async ({ page }) => {
  const groupName = `E2E-Gruppe-${Date.now()}`;

  await createGroup(page, groupName);

  await expect(page.getByText(groupName, { exact: true })).toBeVisible();
});

test('admin can delete a user group (P13)', async ({ page }) => {
  const groupName = `E2E-DelGruppe-${Date.now()}`;
  await createGroup(page, groupName);
  await expect(page.getByText(groupName, { exact: true })).toBeVisible();

  // AdminUserGroupsView#actionButtons order: Bearbeiten(0), Löschen(1).
  const deleteBtn = await rowActionButton(page, groupName, 1);
  await deleteBtn.click();
  await confirmDeletion(page);

  await expect(page.getByText(groupName, { exact: true })).toHaveCount(0);
});

test('admin can create a device (P10)', async ({ page }) => {
  const deviceName = `E2E-Geraet-${Date.now()}`;

  await openAdminSection(page, 'admin/devices');
  await page.getByRole('button', { name: 'Neu' }).click();
  const win = dialog(page);
  await expect(win.locator('h2[slot="title"]')).toHaveText('Gerät erstellen');

  await win.getByLabel('Name', { exact: true }).fill(deviceName);
  await pickCombo(page, win, 'Position', '1');
  await pickCombo(page, win, 'Standort', 'Default');
  // fhem gateway fields (required) - both fhem and deCONZ gateways stay supported (see
  // docs/kb/05-migration-plan.md "Entscheidungen"), the legacy test only ever exercised fhem.
  await win.getByLabel('Fhem Name').fill('e2e-wm');
  await win.getByLabel('Fhem Switch Name').fill('e2e-wm-sw');
  await win.getByLabel('Fhem Power Name').fill('e2e-wm-pw');
  await win.getByLabel('Auto-Ende Schwellwert (W)').fill('0.5');
  await win.getByLabel('Auto-Ende Wartezeit (s)').fill('20');

  await win.getByRole('button', { name: 'Erstellen' }).click();
  await expectNoDialog(page);

  await expect(page.getByText(deviceName, { exact: true })).toBeVisible();
});

test('admin can activate/deactivate a device and it persists (P11)', async ({ page }) => {
  // P11 was never implemented against the legacy Vaadin 7 portal (see docs/kb/08-test-plan.md) -
  // newly added here to reach full P1-P20 coverage, per the Phase 3 AP6 acceptance criterion.
  const deviceName = `E2E-Geraet-Toggle-${Date.now()}`;

  await openAdminSection(page, 'admin/devices');
  await page.getByRole('button', { name: 'Neu' }).click();
  let win = dialog(page);
  await win.getByLabel('Name', { exact: true }).fill(deviceName);
  await pickCombo(page, win, 'Position', '2');
  await pickCombo(page, win, 'Standort', 'Default');
  await win.getByLabel('Fhem Name').fill('e2e-toggle');
  await win.getByLabel('Fhem Switch Name').fill('e2e-toggle-sw');
  await win.getByLabel('Fhem Power Name').fill('e2e-toggle-pw');
  await win.getByLabel('Auto-Ende Schwellwert (W)').fill('0.5');
  await win.getByLabel('Auto-Ende Wartezeit (s)').fill('20');
  // New devices default to "Aktiviert" (see DeviceFormDialog).
  await win.getByRole('button', { name: 'Erstellen' }).click();
  await expectNoDialog(page);

  // AdminDevicesView#actionButtons order: Bearbeiten(0), Löschen(1).
  const openEdit = async () => {
    const editBtn = await rowActionButton(page, deviceName, 0);
    await editBtn.click();
    const dlg = dialog(page);
    await expect(dlg.locator('h2[slot="title"]')).toHaveText('Gerät bearbeiten');
    return dlg;
  };

  win = await openEdit();
  await expect(win.getByLabel('Aktiviert')).toBeChecked();
  await win.getByLabel('Aktiviert').uncheck({ force: true });
  await win.getByRole('button', { name: 'Speichern' }).click();
  await expectNoDialog(page);

  // The grid's status badge flips to "Deaktiviert" (see AdminDevicesView#statusBadge).
  const cells = await gridRowCells(page, deviceName);
  await expect(cells[3]).toHaveText('Deaktiviert');

  // Re-open and confirm the unchecked state persisted.
  win = await openEdit();
  await expect(win.getByLabel('Aktiviert')).not.toBeChecked();
  await win.getByRole('button', { name: 'Abbrechen' }).click();
});

test('admin can create a fixed-price program (P12)', async ({ page }) => {
  const programName = `E2E-Programm-${Date.now()}`;

  await openAdminSection(page, 'admin/programs');
  await page.getByRole('button', { name: 'Neu' }).click();
  const win = dialog(page);
  await expect(win.locator('h2[slot="title"]')).toHaveText('Programm erstellen');

  await win.getByLabel('Name', { exact: true }).fill(programName);
  // "Statisch" (fixed price) is already the dialog's default (see ProgramFormDialog) - click it
  // explicitly anyway so the test doesn't silently depend on that default.
  await win.getByRole('radio', { name: 'Statisch' }).click({ force: true });
  await win.getByLabel('Preis').fill('1.50');
  // Maximaldauer: composite amount+unit field (see ProgramFormDialog.DurationField); the unit
  // combo and the other two duration groups (Freie Zeit/Frühester Abbruch) keep their valid
  // defaults (0 s, non-negative).
  await win.getByRole('spinbutton', { name: 'Maximaldauer' }).fill('60');

  await win.getByRole('button', { name: 'Erstellen' }).click();
  await expectNoDialog(page);

  await expect(page.getByText(programName, { exact: true })).toBeVisible();
});

test('admin can open and save a location from the Standorte section (P14)', async ({ page }) => {
  // The legacy portal only exposed location editing via a dialog on the dashboard
  // (LocationWindow); the backend Portal gives locations their own "Standorte" section instead
  // (AdminLocationsView) - a deliberate, auftraggeber-gewünschte UX-improvement, not a
  // functional change (see docs/kb/05-migration-plan.md "Entscheidungen", AdminLayout Javadoc). The
  // underlying assertion (P14's "name prefilled, unchanged save round-trip") is unchanged.
  await openAdminSection(page, 'admin/locations');

  // AdminLocationsView#actionButtons order: Bearbeiten(0), Löschen(1).
  const openEdit = async () => {
    const editBtn = await rowActionButton(page, 'Default', 0);
    await editBtn.click();
    const win = dialog(page);
    await expect(win.locator('h2[slot="title"]')).toHaveText('Standort bearbeiten');
    return win;
  };

  let win = await openEdit();
  const nameField = win.getByLabel('Name', { exact: true });
  await expect(nameField).toHaveValue('Default');

  // Save unchanged (round-trip through LocationService#update) - keeps global state stable for
  // the other tests that rely on the "Default" location.
  await win.getByRole('button', { name: 'Speichern' }).click();
  await expectNoDialog(page);

  // Re-open to confirm the location still edits and persists correctly.
  win = await openEdit();
  await expect(win.getByLabel('Name', { exact: true })).toHaveValue('Default');
  await win.getByRole('button', { name: 'Abbrechen' }).click();
});
