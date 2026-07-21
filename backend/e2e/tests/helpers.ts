import { Page, Locator, expect } from '@playwright/test';

/**
 * Shared helpers for the elwasys backend Portal E2E suite (Vaadin Flow, Phase 3 AP6) -
 * fachlicher Nachfolger der Vaadin-7-Lokatoren-Helfer in Portal/e2e/tests/*.spec.ts, siehe
 * kb/06-ui-tests.md für die Selektor-Strategie.
 */

export const ADMIN_USERNAME = 'admin';
export const ADMIN_PASSWORD = 'admin';

/**
 * Fills Vaadin's built-in LoginForm and submits. The username/password inputs are plain
 * `<input name="username">`/`<input name="password">` elements slotted into
 * `<vaadin-text-field>`/`<vaadin-password-field>` (see LoginView#buildGermanI18n) - Spring
 * Security's default form-login parameter names, which is exactly why Vaadin's LoginForm can
 * `action="login"` straight into it without a Vaadin-specific submit handler.
 */
export async function login(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/login');
  await page.locator('input[name="username"]').fill(username);
  await page.locator('input[name="password"]').fill(password);
  await page.getByRole('button', { name: 'Login', exact: true }).click();
}

export async function loginAsAdmin(page: Page): Promise<void> {
  await login(page, ADMIN_USERNAME, ADMIN_PASSWORD);
  await expect(page.locator('vaadin-side-nav-item[path="admin/users"]')).toBeVisible();
}

/** Opens an admin side-nav section by its route path (e.g. "admin/users", "admin/devices"). */
export async function openAdminSection(page: Page, path: string): Promise<void> {
  await page.locator(`vaadin-side-nav-item[path="${path}"]`).click();
  await expect(page).toHaveURL(new RegExp(`/${path}(\\?|$)`));
}

/** Opens the top-right user menu ("<Anzeigename>") and clicks one of its entries
 * (Einstellungen / Passwort ändern / Logout - see UserMenuBar). */
export async function openUserMenu(page: Page, entry: string): Promise<void> {
  await page.locator('vaadin-menu-bar vaadin-menu-bar-button').first().click();
  await page.getByRole('menuitem', { name: entry, exact: true }).click();
}

/** The currently open modal dialog. Every admin CRUD dialog/confirmation in this portal is a
 * Vaadin `Dialog`, which renders as a single `<vaadin-dialog-overlay>` appended to `<body>`. */
export function dialog(page: Page): Locator {
  return page.locator('vaadin-dialog-overlay').first();
}

export async function expectNoDialog(page: Page): Promise<void> {
  await expect(page.locator('vaadin-dialog-overlay')).toHaveCount(0);
}

/**
 * Selects an item in a Vaadin ComboBox located by its accessible label: click to open, type to
 * filter, confirm with Enter, then assert the value committed. Fachlicher Nachfolger von
 * Portal/e2e/tests/admin-crud.spec.ts's pickCombo() (dort für Vaadin 7's v-filterselect).
 */
export async function pickCombo(page: Page, scope: Locator, label: string, item: string): Promise<void> {
  const combo = scope.getByLabel(label, { exact: true });
  await combo.click();
  await combo.fill(item);
  await page.waitForTimeout(200); // let the filtered overlay list settle before confirming
  await page.keyboard.press('Enter');
  await expect(combo).toHaveValue(item);
}

/**
 * Returns locators for every column cell of the `vaadin-grid` row whose accessible name
 * matches `rowName`, in column order.
 *
 * IMPORTANT (see kb/06-ui-tests.md): `vaadin-grid` renders cell content as LIGHT-DOM
 * `<vaadin-grid-cell-content slot="...">` elements that are children of `<vaadin-grid>` itself,
 * NOT of the row's `<tr>` - they are merely *slotted* into a `<td><slot></td>` inside the row's
 * shadow tree. `row.locator(...)` therefore silently finds nothing (Playwright's locators
 * follow the real DOM tree, not the flattened slot-assignment rendering tree), even though
 * `getByRole('row', { name })` DOES work (the accessible name computation follows the
 * flattened tree). The fix: read the slot NAMES off the row's own `<td><slot>` elements (real
 * shadow-DOM descendants of `<tr>`, so `row.locator('td slot')` works), then re-locate the
 * matching `vaadin-grid-cell-content` elements globally by that slot name.
 */
export async function gridRowCells(page: Page, rowName: string | RegExp): Promise<Locator[]> {
  const row = page.getByRole('row', { name: rowName });
  await expect(row).toBeVisible();
  const slotNames = await row.locator('td slot').evaluateAll((slots) => slots.map((s) => s.getAttribute('name')));
  return slotNames.map((name) => page.locator(`vaadin-grid-cell-content[slot="${name}"]`));
}

/** The last column of a row - by convention of every admin grid in this portal
 * (AdminUsersView/-DevicesView/-UserGroupsView/-ProgramsView/-LocationsView), the row-action
 * buttons (Bearbeiten/Löschen/...) are always added last. */
export async function gridRowActions(page: Page, rowName: string | RegExp): Promise<Locator> {
  const cells = await gridRowCells(page, rowName);
  return cells[cells.length - 1];
}

/**
 * The Nth icon-only action button in a row's actions column, addressed BY POSITION (icon
 * buttons here carry only a `vaadin-tooltip` for a sighted/screen-reader hint via
 * `aria-describedby`, which does NOT contribute to the accessible NAME Playwright's
 * `getByRole('button', { name })` matches against - confirmed empirically, see
 * kb/06-ui-tests.md - so name-based lookup silently finds nothing for these buttons). The order
 * matches each view's `actionButtons()` method 1:1:
 *   - AdminUsersView: Bearbeiten(0), Guthaben aufladen(1), Umsätze ansehen(2), Löschen(3)
 *   - AdminDevicesView / AdminUserGroupsView / AdminProgramsView / AdminLocationsView:
 *     Bearbeiten(0), Löschen(1)
 */
export async function rowActionButton(page: Page, rowName: string | RegExp, index: number): Promise<Locator> {
  const actions = await gridRowActions(page, rowName);
  return actions.locator('vaadin-button').nth(index);
}

/** Confirms a ConfirmDeleteDialog (Vaadin `ConfirmDialog`, German "Ja"/"Nein" buttons, see
 * ConfirmDeleteDialog). */
export async function confirmDeletion(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Ja', exact: true }).click();
}
