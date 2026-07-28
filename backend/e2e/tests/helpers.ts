import { Page, Locator, expect } from '@playwright/test';
import { execFileSync } from 'child_process';

/**
 * Shared helpers for the elwasys backend Portal E2E suite (Vaadin Flow, Phase 3 AP6) -
 * fachlicher Nachfolger der Vaadin-7-Lokatoren-Helfer in Portal/e2e/tests/*.spec.ts, siehe
 * docs/kb/06-ui-tests.md für die Selektor-Strategie.
 */

export const ADMIN_USERNAME = 'admin';
// Mindestens 8 Zeichen (Issue #44, ADR 0018): der admin-cli-Seed setzt das Passwort über
// PasswordService#setNewPassword, das die serverseitige Mindestlänge erzwingt. Muss mit
// scripts/start-backend.sh (--password) übereinstimmen.
export const ADMIN_PASSWORD = 'admin-e2e';

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
  // Issue #40 (Pre-Launch AP5): deterministisch statt eines festen waitForTimeout(200) - auf den
  // gefilterten Overlay-Eintrag warten, bevor Enter ihn bestätigt (kein Flake auf langsamer CI).
  await expect(
    page.locator('vaadin-combo-box-item', { hasText: item }).first(),
  ).toBeVisible();
  await page.keyboard.press('Enter');
  await expect(combo).toHaveValue(item);
}

/**
 * Returns locators for every column cell of the `vaadin-grid` row whose accessible name
 * matches `rowName`, in column order.
 *
 * IMPORTANT (see docs/kb/06-ui-tests.md): `vaadin-grid` renders cell content as LIGHT-DOM
 * `<vaadin-grid-cell-content slot="...">` elements that are children of `<vaadin-grid>` itself,
 * NOT of the row's `<tr>` - they are merely *slotted* into a `<td><slot></td>` inside the row's
 * shadow tree. `row.locator(...)` therefore silently finds nothing (Playwright's locators
 * follow the real DOM tree, not the flattened slot-assignment rendering tree), even though
 * `getByRole('row', { name })` DOES work (the accessible name computation follows the
 * flattened tree). The fix: read the slot NAMES off the row's own `<td><slot>` elements (real
 * shadow-DOM descendants of `<tr>`, so `row.locator('td slot')` works), then re-locate the
 * matching `vaadin-grid-cell-content` elements by that slot name.
 *
 * SECOND PITFALL (found with the TerminalTokenDialog, P32): the generated slot names are unique
 * per GRID, not per document - `vaadin-grid-cell-content-19` exists once in every grid on the
 * page. As long as a page shows a single grid that is harmless, but a grid inside a dialog sits
 * on top of the list grid behind it, and the global re-location then resolves to two elements
 * ("strict mode violation"). Pass `scope` (e.g. `dialog(page)`) whenever more than one grid can
 * be present; the row lookup AND the cell re-location are both confined to it.
 */
export async function gridRowCells(page: Page, rowName: string | RegExp, scope?: Locator): Promise<Locator[]> {
  const root = scope ?? page;
  const row = root.getByRole('row', { name: rowName });
  await expect(row).toBeVisible();
  const slotNames = await row.locator('td slot').evaluateAll((slots) => slots.map((s) => s.getAttribute('name')));
  return slotNames.map((name) => root.locator(`vaadin-grid-cell-content[slot="${name}"]`));
}

/** The last column of a row - by convention of every admin grid in this portal
 * (AdminUsersView/-DevicesView/-UserGroupsView/-ProgramsView/-LocationsView) and of the token
 * grid in TerminalTokenDialog, the row-action buttons (Bearbeiten/Löschen/...) are always added
 * last. `scope` as in `gridRowCells`. */
export async function gridRowActions(page: Page, rowName: string | RegExp, scope?: Locator): Promise<Locator> {
  const cells = await gridRowCells(page, rowName, scope);
  return cells[cells.length - 1];
}

/**
 * The Nth icon-only action button in a row's actions column, addressed BY POSITION (icon
 * buttons here carry only a `vaadin-tooltip` for a sighted/screen-reader hint via
 * `aria-describedby`, which does NOT contribute to the accessible NAME Playwright's
 * `getByRole('button', { name })` matches against - confirmed empirically, see
 * docs/kb/06-ui-tests.md - so name-based lookup silently finds nothing for these buttons). The order
 * matches each view's `actionButtons()` method 1:1:
 *   - AdminUsersView: Bearbeiten(0), Guthaben aufladen(1), Umsätze ansehen(2), Löschen(3)
 *   - AdminLocationsView: Bearbeiten(0), Terminal-Tokens verwalten(1), Löschen(2)
 *   - AdminDevicesView / AdminUserGroupsView / AdminProgramsView: Bearbeiten(0), Löschen(1)
 */
export async function rowActionButton(
  page: Page,
  rowName: string | RegExp,
  index: number,
  scope?: Locator,
): Promise<Locator> {
  const actions = await gridRowActions(page, rowName, scope);
  return actions.locator('vaadin-button').nth(index);
}

/** Confirms a ConfirmDeleteDialog (Vaadin `ConfirmDialog`, German "Ja"/"Nein" buttons, see
 * ConfirmDeleteDialog). */
export async function confirmDeletion(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Ja', exact: true }).click();
}

/**
 * Opens the dialog behind the Nth row-action button (see `rowActionButton` for the index order)
 * and asserts its title before handing the dialog back - the shape every "open the editor of row
 * X" step in this suite had spelled out for itself (finale Review R3c, Issue #92).
 */
export async function openEditDialog(
  page: Page,
  rowName: string | RegExp,
  buttonIndex: number,
  expectedTitle: string,
): Promise<Locator> {
  const button = await rowActionButton(page, rowName, buttonIndex);
  await button.click();
  const win = dialog(page);
  await expect(win.locator('h2[slot="title"]')).toHaveText(expectedTitle);
  return win;
}

/**
 * Runs a SQL script against the E2E database as the postgres superuser - the way the specs seed
 * and clean up their own fixtures (the backend owns the schema, so the fixtures are inserted
 * directly instead of through an API the portal does not offer).
 *
 * `ON_ERROR_STOP=1` makes a broken script fail the test instead of silently seeding half a
 * fixture; psql's stdout stays hidden (`-q` leaves nothing useful there) while stderr is passed
 * through, so a failure is readable in the test output.
 */
export function runSql(dbName: string, script: string): void {
  execFileSync('sudo', ['-u', 'postgres', 'psql', '-q', '-v', 'ON_ERROR_STOP=1', '-d', dbName], {
    input: script,
    stdio: ['pipe', 'ignore', 'inherit'],
  });
}
