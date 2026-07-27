import { test, expect, Page, Locator } from '@playwright/test';
import {
  loginAsAdmin,
  openAdminSection,
  gridRowCells,
  dialog,
  pickCombo,
  expectNoDialog,
  runSql,
  ADMIN_USERNAME,
  ADMIN_PASSWORD,
  login,
} from './helpers';

/**
 * Freitextfilter und Spaltensortierung der Portal-Listen (test plan P29-P31) - die drei
 * nutzersichtbaren Neuerungen des UI-Redesigns v2 an den Listen (siehe
 * docs/specs/0002-ui-design/v2/MAPPING.md, Abschnitt "Listen"): ein Filterfeld je Liste
 * (ListFilterField), zusätzliche sortierbare Spalten und - als Regressionsfall - das erneute
 * Anwenden des Filters nach jedem Neuladen der Zeilen (ListFilterField#reapply, gerufen aus
 * AbstractAdminListView#setGridItems).
 *
 * Geprüft wird an der Benutzerliste (AdminUsersView), weil dort beide Neuerungen
 * zusammenkommen: das Filterfeld der gemeinsamen Basisklasse UND die Guthaben-Spalte, deren
 * Sortierung auf dem ROHEN BigDecimal vergleicht (setComparator) statt auf dem formatierten
 * Währungstext. Genau darauf sind die Fixtures ausgelegt (9,00 € vs. 10,00 €): alphabetisch
 * stünde "10,00 €" vor "9,00 €", numerisch ist es umgekehrt - ein Test mit z.B. 5 und 20 wäre
 * in beiden Fällen grün und würde den Vergleicher gar nicht prüfen.
 *
 * Die Fixtures werden - wie in dashboard.spec.ts/offline-incidents.spec.ts - direkt in die
 * E2E-Datenbank geseedet und danach wieder entfernt. Sie werden VOR JEDEM Test neu angelegt,
 * weil P31 über die Oberfläche einen weiteren Benutzer anlegt und die Tests sonst
 * reihenfolgeabhängig wären.
 */

const DB_NAME = process.env.E2E_DB_NAME || 'elwasys_backend_e2e';
const STAMP = Date.now();

/** Gemeinsamer Namensbestandteil der Filter-Fixtures - zugleich der Suchbegriff der Tests. */
const FILTER_TERM = `E2E-Filter-${STAMP}`;
const ALPHA = `${FILTER_TERM}-Alpha`;
const BETA = `${FILTER_TERM}-Beta`;
const GAMMA = `${FILTER_TERM}-Gamma`;

/** Gemeinsamer Namensbestandteil der Sortier-Fixtures. */
const SORT_TERM = `E2E-Sort-${STAMP}`;
const NINE = `${SORT_TERM}-Neun`;
const TEN = `${SORT_TERM}-Zehn`;

/** Alle Fixture-Benutzernamen dieser Datei tragen dieses Präfix (Seed wie Aufräumen). */
const USERNAME_PREFIX = `e2e_lfs_${STAMP}`;

/** Run a SQL script against the E2E database (see helpers.runSql). */
function sql(script: string) {
  runSql(DB_NAME, script);
}

function insertUser(name: string, usernameSuffix: string): string {
  return `
    INSERT INTO users (name, username, is_admin, blocked, deleted, group_id)
      VALUES ('${name}', '${USERNAME_PREFIX}_${usernameSuffix}', FALSE, FALSE, FALSE,
        (SELECT id FROM user_groups ORDER BY id LIMIT 1));
  `;
}

function insertCredit(usernameSuffix: string, amount: string): string {
  return `
    INSERT INTO credit_accounting (user_id, amount, date, description)
      VALUES ((SELECT id FROM users WHERE username = '${USERNAME_PREFIX}_${usernameSuffix}'),
        ${amount}, NOW(), 'E2E Testeinzahlung');
  `;
}

function seed() {
  // Reihenfolge der beiden Sortier-Fixtures im Seed bewusst "Zehn vor Neun": die Benutzerliste
  // ist ungeordnet, bis ein Spaltenkopf angeklickt wird (UserRepository#findByDeletedFalse hat
  // kein ORDER BY). Die Tests behaupten deshalb nichts über die Ausgangsreihenfolge, sondern
  // nur über die beiden Richtungen NACH einem Klick.
  sql(`
    ${cleanupSql()}
    ${insertUser(ALPHA, 'alpha')}
    ${insertUser(BETA, 'beta')}
    ${insertUser(TEN, 'ten')}
    ${insertUser(NINE, 'nine')}
    ${insertCredit('ten', '10.00')}
    ${insertCredit('nine', '9.00')}
  `);
}

function cleanupSql(): string {
  return `
    DELETE FROM credit_accounting WHERE user_id IN
      (SELECT id FROM users WHERE username LIKE '${USERNAME_PREFIX}%');
    DELETE FROM users WHERE username LIKE '${USERNAME_PREFIX}%';
  `;
}

/** Das Freitextfeld über einer Liste (ListFilterField setzt die zugängliche Beschriftung). */
function filterField(page: Page, ariaLabel: string): Locator {
  return page.getByLabel(ariaLabel, { exact: true });
}

/** Die Tabelle der Benutzerliste (AbstractAdminListView setzt die CSS-Klasse der Ansicht). */
function usersGrid(page: Page): Locator {
  return page.locator('.admin-users-view vaadin-grid');
}

/**
 * Alle Zeilen der Benutzertabelle samt Kopfzeile. Über die ARIA-Rolle gezählt, nicht über die
 * Zellknoten: `vaadin-grid` hält im Light-DOM einen Pool wiederverwendeter
 * `<vaadin-grid-cell-content>`-Knoten (siehe helpers.ts/docs/kb/06-ui-tests.md), im
 * Accessibility-Baum steht dagegen genau das, was tatsächlich als Zeile angezeigt wird.
 */
function allRows(page: Page): Locator {
  return usersGrid(page).getByRole('row');
}

/**
 * Die n-te Datenzeile eines Grids (1 = die oberste). `vaadin-grid` schreibt die Position als
 * `aria-rowindex` an jedes `<tr>` (Kopfzeile = 1, erste Datenzeile = 2) - der einzige
 * verlässliche Ordnungsschlüssel, denn die DOM-Reihenfolge der `<tr>` folgt dem Recycling des
 * Virtualizers, nicht der Anzeige.
 *
 * <p>Über die Position statt über die Zeile selbst zu prüfen ist zugleich das, was die Zusicherung
 * abwartbar macht: ein Klick auf einen Spaltenkopf setzt das `direction`-Attribut SOFORT im
 * Browser, sortiert wird aber serverseitig eine Serverrunde später. Eine Zusicherung auf den
 * Zeileninhalt an fester Position wiederholt Playwright bis dahin von selbst - deterministisch,
 * ohne waitForTimeout.
 */
function dataRow(page: Page, position: number): Locator {
  return usersGrid(page).locator(`tr[aria-rowindex="${position + 1}"]`);
}

test.beforeEach(async ({ page }) => {
  seed();
  await loginAsAdmin(page);
});

test.afterAll(() => sql(cleanupSql()));

test('the list filter narrows a list to the matching rows and clearing it restores them (P29)', async ({
  page,
}) => {
  await openAdminSection(page, 'admin/users');
  const filter = filterField(page, 'Benutzer filtern');

  // Ausgangszustand: beide Filter-Fixtures stehen in der ungefilterten Liste.
  await expect(page.getByRole('row', { name: ALPHA })).toHaveCount(1);
  await expect(page.getByRole('row', { name: BETA })).toHaveCount(1);
  const unfilteredRows = await allRows(page).count();

  // Ein Begriff, der nur eine Zeile trifft: die Liste schrumpft auf Kopfzeile + diese Zeile.
  await filter.fill(ALPHA);
  await expect(allRows(page)).toHaveCount(2);
  await expect(page.getByRole('row', { name: ALPHA })).toHaveCount(1);
  await expect(page.getByRole('row', { name: BETA })).toHaveCount(0);

  // Groß-/Kleinschreibung spielt keine Rolle (ListFilterField#normalize).
  await filter.fill(ALPHA.toUpperCase());
  await expect(page.getByRole('row', { name: ALPHA })).toHaveCount(1);

  // Leeren stellt die vollständige Liste wieder her.
  await filter.fill('');
  await expect(allRows(page)).toHaveCount(unfilteredRows);
  await expect(page.getByRole('row', { name: BETA })).toHaveCount(1);
});

test('clicking the Guthaben header sorts by the amount, not by its formatted text (P30)', async ({ page }) => {
  await openAdminSection(page, 'admin/users');

  // Auf die zwei Sortier-Fixtures eingrenzen: die Benutzerliste enthält je nach vorher
  // gelaufenen Specs beliebig viele weitere Zeilen, und geprüft werden soll die REIHENFOLGE
  // dieser beiden zueinander.
  await filterField(page, 'Benutzer filtern').fill(SORT_TERM);
  await expect(allRows(page)).toHaveCount(3);

  // Spaltenreihenfolge (AdminUsersView#configureColumns): Name(0), Username(1), Gruppe(2),
  // Kartennummer(3), Guthaben(4), Status(5), Warnung(6), Aktionen(7).
  await expect((await gridRowCells(page, NINE))[4]).toContainText('9,00');
  await expect((await gridRowCells(page, TEN))[4]).toContainText('10,00');

  const sorter = usersGrid(page).locator('vaadin-grid-sorter', { hasText: 'Guthaben' });

  // Erster Klick: aufsteigend. 9,00 € vor 10,00 € - würde das Grid am angezeigten Text
  // vergleichen (ohne den setComparator auf dem rohen BigDecimal), stünde "10,00 €"
  // alphabetisch vorn und diese Zusicherung schlüge fehl.
  await sorter.click();
  await expect(sorter).toHaveAttribute('direction', 'asc');
  await expect(dataRow(page, 1)).toHaveAccessibleName(new RegExp(NINE));
  await expect(dataRow(page, 2)).toHaveAccessibleName(new RegExp(TEN));

  // Zweiter Klick: absteigend - die Reihenfolge kehrt sich um.
  await sorter.click();
  await expect(sorter).toHaveAttribute('direction', 'desc');
  await expect(dataRow(page, 1)).toHaveAccessibleName(new RegExp(TEN));
  await expect(dataRow(page, 2)).toHaveAccessibleName(new RegExp(NINE));
});

test('a filtered list stays filtered when another session changes the data (P31)', async ({
  page,
  browser,
  baseURL,
}) => {
  // Regressionsfall: grid.setItems(...) legt einen NEUEN, ungefilterten ListDataProvider an.
  // Ohne das reapply() in AbstractAdminListView#setGridItems stünde nach jedem Live-Update
  // (UiBroadcaster) wieder die vollständige Liste da, während im Feld noch der Suchbegriff steht.
  await openAdminSection(page, 'admin/users');
  const filter = filterField(page, 'Benutzer filtern');
  await filter.fill(FILTER_TERM);
  await expect(allRows(page)).toHaveCount(3);
  // Die Sortier-Fixtures existieren, passen aber nicht zum Suchbegriff - ihre Abwesenheit ist
  // im Folgenden der Beleg dafür, dass der Filter noch greift.
  await expect(page.getByRole('row', { name: NINE })).toHaveCount(0);

  // Zweite Sitzung (eigener Browser-Kontext = eigene Vaadin-Session) legt einen Benutzer an,
  // der zum Suchbegriff der ersten Sitzung passt. UserService veröffentlicht dabei ein
  // UserChangedEvent, das der UiBroadcaster per Push an die erste Sitzung verteilt.
  // baseURL ausdrücklich mitgeben: ein selbst erzeugter Kontext erbt die `use`-Optionen der
  // Konfiguration nicht, page.goto('/login') liefe sonst ins Leere.
  const otherContext = await browser.newContext({ baseURL });
  try {
    const otherPage = await otherContext.newPage();
    await login(otherPage, ADMIN_USERNAME, ADMIN_PASSWORD);
    await createUser(otherPage, GAMMA, `${USERNAME_PREFIX}_gamma`);

    // Die erste Sitzung hat neu geladen - sichtbar daran, dass der neue Benutzer dort auftaucht
    // (deterministisch: expect() pollt, kein waitForTimeout).
    await expect(page.getByRole('row', { name: GAMMA })).toHaveCount(1);
  } finally {
    await otherContext.close();
  }

  // ... und der Filter gilt nach dem Neuladen unverändert: nur die drei passenden Zeilen, der
  // Suchbegriff steht weiterhin im Feld.
  await expect(allRows(page)).toHaveCount(4);
  await expect(page.getByRole('row', { name: NINE })).toHaveCount(0);
  await expect(filter).toHaveValue(FILTER_TERM);
});

/**
 * Legt einen Benutzer über die Oberfläche an (Gruppe "Default", ohne Passwort-Mail) - dieselben
 * Schritte wie in admin-crud.spec.ts (P6), hier nötig, weil P31 die Änderung ausdrücklich aus
 * einer ZWEITEN Sitzung heraus auslösen muss, damit der UiBroadcaster überhaupt ins Spiel kommt.
 */
async function createUser(page: Page, name: string, username: string): Promise<void> {
  await openAdminSection(page, 'admin/users');
  await page.getByRole('button', { name: 'Neu' }).click();
  const win = dialog(page);
  await expect(win.locator('h2[slot="title"]')).toHaveText('Benutzer erstellen');

  await win.getByLabel('Name', { exact: true }).fill(name);
  await win.getByLabel('Username', { exact: true }).fill(username);
  // Ohne Passwort-Mail: im Testumfeld ist kein SMTP konfiguriert.
  await win.getByLabel('Sende dem Benutzer per Email ein neues Passwort').uncheck({ force: true });
  await pickCombo(page, win, 'Benutzergruppe', 'Default');

  await win.getByRole('button', { name: 'Erstellen' }).click();
  await expectNoDialog(page);
}
