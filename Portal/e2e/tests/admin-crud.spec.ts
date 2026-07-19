import { test, expect, Page, Locator } from '@playwright/test';

/**
 * Admin CRUD E2E (test plan P6): create a user through the Vaadin UI and assert
 * it shows up in the users table.
 *
 * Vaadin 7 FormLayout renders each field as a row with a caption cell and a
 * content cell; there are no stable ids, so fields are located by the row whose
 * caption matches exactly.
 */

async function loginAsAdmin(page: Page) {
  await page.goto('/');
  await page.locator('input.v-textfield:not([type="password"])').first().fill('admin');
  await page.locator('input[type="password"]').first().fill('admin');
  await page.locator('.v-button', { hasText: 'Login' }).first().click();
  await expect(page.getByText('Benutzergruppen', { exact: true })).toBeVisible();
}

async function openSection(page: Page, caption: string, fragment: string) {
  await page.locator('.valo-menuitems').getByText(caption, { exact: true }).click();
  await expect(page).toHaveURL(new RegExp('#!' + fragment + '\\b'));
}

/** The input/textarea of a Vaadin form field, located by its exact caption. */
function fieldByCaption(scope: Locator, caption: string): Locator {
  return scope
    .locator('tr')
    .filter({ has: scope.page().getByText(caption, { exact: true }) })
    .locator('input, textarea')
    .first();
}

/** Select an item in a Vaadin ComboBox located by the field's exact caption. */
async function selectInCombo(scope: Locator, caption: string, item: string) {
  const page = scope.page();
  const row = scope.locator('tr').filter({ has: page.getByText(caption, { exact: true }) });
  const input = row.locator('.v-filterselect input');
  const popup = page.locator('.v-filterselect-suggestpopup');
  // Open, filter down to the item, then CLICK it — clicking the suggestion is
  // the reliable way to commit a Vaadin 7 filterselect (Enter is racy in CI).
  await input.click();
  await input.pressSequentially(item, { delay: 20 });
  await popup.locator('td.gwt-MenuItem').filter({ hasText: new RegExp('^' + item + '$') }).click();
  // Verify the value committed before moving on.
  await expect(input).toHaveValue(item);
  // The shared suggestion popup lingers and overlays the field below it;
  // clicking the window header dismisses it without changing the selection.
  await scope.locator('.v-window-header').first().click();
}

test('admin can create a user', async ({ page }) => {
  const stamp = Date.now();
  const username = `e2e_ui_${stamp}`;
  const name = `E2E UI User ${stamp}`;

  await loginAsAdmin(page);
  await openSection(page, 'Benutzer', 'users');

  // Open the "create user" window.
  await page.locator('.v-menubar-menuitem', { hasText: 'Neu' }).click();
  const win = page.locator('.v-window', { hasText: 'Benutzer erstellen' });
  await expect(win).toBeVisible();

  // Fill the required fields.
  await fieldByCaption(win, 'Name').fill(name);
  await fieldByCaption(win, 'Username').fill(username);

  // Uncheck "send a new password by email" so email is not required and no
  // mail is sent (SMTP is not configured in the test environment).
  const sendPw = win.locator('.v-checkbox', { hasText: 'Sende dem Benutzer' }).locator('input');
  if (await sendPw.isChecked()) {
    // The associated <label> overlays the input, so click it via force.
    await sendPw.uncheck({ force: true });
  }

  // Select a user group in the Vaadin ComboBox (open via the dropdown button).
  const groupRow = win.locator('tr').filter({ has: page.getByText('Benutzergruppe', { exact: true }) });
  await groupRow.locator('.v-filterselect-button').click();
  await page.locator('.v-filterselect-suggestpopup').getByText('Default', { exact: true }).click();

  // Save.
  await win.locator('.v-button', { hasText: 'Erstellen' }).click();
  await expect(win).toBeHidden();

  // The new user appears in the users table.
  await expect(page.getByText(name, { exact: true })).toBeVisible();
});

test('admin can create a user group', async ({ page }) => {
  const groupName = `E2E-Gruppe-${Date.now()}`;

  await loginAsAdmin(page);
  await openSection(page, 'Benutzergruppen', 'groups');

  await page.locator('.v-menubar-menuitem', { hasText: 'Neu' }).click();
  const win = page.locator('.v-window', { hasText: 'Gruppe erstellen' });
  await expect(win).toBeVisible();

  await fieldByCaption(win, 'Name').fill(groupName);
  // Discount type "Keiner" (no discount) — an OptionGroup radio button.
  await win.locator('.v-select-option', { hasText: 'Keiner' }).locator('input').check({ force: true });

  await win.locator('.v-button', { hasText: 'Erstellen' }).click();
  await expect(win).toBeHidden();

  await expect(page.getByText(groupName, { exact: true })).toBeVisible();
});

test('admin can create a device', async ({ page }) => {
  const deviceName = `E2E-Geraet-${Date.now()}`;

  await loginAsAdmin(page);
  await openSection(page, 'Geräte', 'devices');

  await page.locator('.v-menubar-menuitem', { hasText: 'Neu' }).click();
  const win = page.locator('.v-window', { hasText: 'Gerät erstellen' });
  await expect(win).toBeVisible();

  await fieldByCaption(win, 'Name').fill(deviceName);
  await selectInCombo(win, 'Position', '1');
  await selectInCombo(win, 'Standort', 'Default');
  // fhem gateway fields (required)
  await fieldByCaption(win, 'Fhem Name').fill('e2e-wm');
  await fieldByCaption(win, 'Fhem Switch Name').fill('e2e-wm-sw');
  await fieldByCaption(win, 'Fhem Power Name').fill('e2e-wm-pw');
  // Auto-end threshold/wait keep their defaults.

  await win.locator('.v-button', { hasText: 'Erstellen' }).click();
  await expect(win).toBeHidden();

  await expect(page.getByText(deviceName, { exact: true })).toBeVisible();
});
