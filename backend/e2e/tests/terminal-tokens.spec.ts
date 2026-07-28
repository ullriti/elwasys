import { test, expect, Page, Locator } from '@playwright/test';
import {
  loginAsAdmin,
  openAdminSection,
  dialog,
  expectNoDialog,
  gridRowCells,
  gridRowActions,
  rowActionButton,
  confirmDeletion,
  runSql,
} from './helpers';

/**
 * Standort-Token-Verwaltung im Admin-Portal (test plan P32/P33) - TerminalTokenDialog, erreichbar
 * über die Zeilenaktion "Terminal-Tokens verwalten" der Standortliste (AdminLocationsView).
 *
 * Ein Standort-Token ist das Credential, mit dem sich ein Terminal am Backend anmeldet. Bis
 * hierher gab es dafür nur den CLI-Runner (Profil `token-cli`); das Portal ist der Weg, ein
 * neues Terminal ohne Server-Zugriff in Betrieb zu nehmen. Die beiden Kern-Zusicherungen dieser
 * Suite sind deshalb: das Klartext-Token ist GENAU EINMAL sichtbar (nach dem Schließen des
 * Dialogs nicht wieder), und ein Widerruf entfernt das Token nicht, sondern schaltet es sichtbar
 * ab.
 *
 * Der Standort wird - wie die Fixtures in dashboard.spec.ts/offline-incidents.spec.ts - direkt in
 * die E2E-Datenbank geseedet: ein eigener Standort hält die Tests von der überall sonst genutzten
 * "Default"-Zeile fern (dort hängen Geräte, Programme und die Client-Suiten).
 */

const DB_NAME = process.env.E2E_DB_NAME || 'elwasys_backend_e2e';
const STAMP = Date.now();
const LOCATION = `E2E-Token-Standort-${STAMP}`;
const REVEAL_LABEL = `e2e-terminal-neu-${STAMP}`;
const REVOKE_LABEL = `e2e-terminal-alt-${STAMP}`;
const KEEP_LABEL = `e2e-terminal-bleibt-${STAMP}`;

/** Run a SQL script against the E2E database (see helpers.runSql). */
function sql(script: string) {
  runSql(DB_NAME, script);
}

/**
 * Räumt Tokens und Standort ab. Die Tokens ausdrücklich mitzunehmen ist nicht nötig
 * (terminal_tokens.location_id ist ON DELETE CASCADE, siehe V3), aber es hält das Aufräumen
 * unabhängig davon, was eine spätere Migration an der Fremdschlüssel-Regel ändert.
 */
function cleanup() {
  sql(`
    DELETE FROM terminal_tokens
      WHERE location_id IN (SELECT id FROM locations WHERE name LIKE 'E2E-Token-Standort-%');
    DELETE FROM locations WHERE name LIKE 'E2E-Token-Standort-%';
  `);
}

/**
 * Frischer Standort ohne Tokens vor JEDEM Test: der erste Test erzeugt Tokens, der zweite
 * widerruft eines - ein einmaliger Seed machte die Tests voneinander abhängig.
 */
test.beforeEach(async ({ page }) => {
  cleanup();
  sql(`INSERT INTO locations (name) VALUES ('${LOCATION}');`);
  await loginAsAdmin(page);
});

test.afterAll(() => cleanup());

/**
 * Öffnet die Token-Verwaltung des Test-Standorts. AdminLocationsView#actionButtons order:
 * Bearbeiten(0), Terminal-Tokens(1), Löschen(2) - die Icon-Knöpfe tragen ihren Hinweistext nur
 * als vaadin-tooltip und sind deshalb nur über ihre Position adressierbar (siehe helpers.ts).
 */
async function openTokenDialog(page: Page): Promise<Locator> {
  await openAdminSection(page, 'admin/locations');
  const tokensButton = await rowActionButton(page, LOCATION, 1);
  await tokensButton.click();
  const win = dialog(page);
  await expect(win.locator('h2[slot="title"]')).toHaveText(`Terminal-Tokens von ${LOCATION}`);
  return win;
}

/** Erzeugt ein Token über den Dialog und liefert den einmalig angezeigten Klartext zurück. */
async function issueToken(win: Locator, label: string): Promise<string> {
  await win.getByLabel('Beschriftung (optional)').fill(label);
  await win.getByRole('button', { name: 'Token erzeugen' }).click();
  // Synchronisationspunkt ist die NEUE ZEILE, nicht die Klartext-Anzeige: die ist ab dem zweiten
  // Token bereits sichtbar und damit sofort grün - danach eingesammelte Zellen-Lokatoren zeigten
  // dann auf gepoolte <vaadin-grid-cell-content>-Knoten der noch nicht aktualisierten Liste, die
  // das Grid gleich einer ANDEREN Zeile zuweist (in der Entwicklung dieses Tests wurde deshalb
  // das falsche Token widerrufen). Der Zeilenname folgt dem Accessibility-Baum und ist aktuell.
  await expect(win.getByRole('row', { name: new RegExp(label) })).toHaveCount(1);
  await expect(win.locator('.token-reveal')).toBeVisible();
  return win.getByLabel('Neues Token').inputValue();
}

test('admin can issue a terminal token and its plaintext is shown exactly once (P32)', async ({ page }) => {
  const win = await openTokenDialog(page);

  // Vor dem Erzeugen gibt es nichts anzuzeigen.
  await expect(win.locator('.token-reveal')).toBeHidden();

  const rawToken = await issueToken(win, REVEAL_LABEL);

  // Das Klartext-Token trägt das Präfix des TerminalTokenService (elwt_) - es ist der Wert, den
  // der Bediener in die Terminal-Konfiguration übernimmt.
  expect(rawToken).toMatch(/^elwt_/);
  await expect(win.locator('.token-reveal')).toContainText('nur EINMAL angezeigt');

  // Die Liste führt das neue Token: Id(0), Beschriftung(1), Erstellt(2), Zuletzt benutzt(3),
  // Status(4), Aktionen(5) - der gespeicherte Hash steht bewusst in KEINER Spalte.
  // Das Grid im Dialog liegt über dem Standort-Grid dahinter - beide vergeben dieselben
  // Slot-Namen, deshalb MUSS die Suche auf den Dialog eingegrenzt werden (siehe helpers.ts).
  // Nirgends im Dialog steht der gespeicherte Hash (SHA-256, also 64 Hex-Zeichen). Bisher stand
  // diese Zusicherung nur als Kommentar im Dialog-Quelltext ("bewusst OHNE token_hash") - eine
  // versehentlich hinzugefügte Spalte fiele sonst niemandem auf.
  await expect(win).not.toContainText(/[0-9a-f]{64}/);

  const cells = await gridRowCells(page, REVEAL_LABEL, win);
  expect(cells).toHaveLength(6);
  await expect(cells[1]).toHaveText(REVEAL_LABEL);
  // Noch nie benutzt: PortalFormats bildet den fehlenden Zeitstempel auf "-" ab.
  await expect(cells[3]).toHaveText('-');
  await expect(cells[4]).toHaveText('Aktiv');

  // Dialog schließen (Kreuz in der Kopfzeile, siehe AbstractFormDialog#addCloseButton) ...
  await win.getByRole('button', { name: 'Dialog schließen' }).click();
  await expectNoDialog(page);

  // ... und erneut öffnen: das Klartext-Token ist weg (es existiert nur im Rückgabewert von
  // TerminalTokenService#createToken, in der DB steht nur sein Hash).
  //
  // toHaveCount(0), nicht toBeHidden(): Vaadin Flows setVisible(false) rendert die Komponente
  // gar nicht erst zum Client (kein verstecktes Element im DOM). toBeHidden() wäre hier zwar
  // ebenfalls grün, aber auch dann, wenn das Feld mit dem Klartext bloß unsichtbar im DOM stünde -
  // die Zählung sagt aus, dass der Wert den Browser nicht erreicht.
  const reopened = await openTokenDialog(page);
  await expect(reopened.locator('.token-reveal')).toHaveCount(0);
  await expect(reopened.locator('.token-reveal-value')).toHaveCount(0);

  // Das Token selbst existiert weiter - nur eben ohne seinen Klartext.
  const cellsAfter = await gridRowCells(page, REVEAL_LABEL, reopened);
  await expect(cellsAfter[4]).toHaveText('Aktiv');
});

test('admin can revoke a terminal token without deleting it (P33)', async ({ page }) => {
  const win = await openTokenDialog(page);

  // Zwei Tokens: der Widerruf des einen darf den anderen nicht berühren (Rotation ohne Ausfall -
  // genau dafür sind mehrere aktive Tokens je Standort zulässig, siehe TerminalTokenService).
  await issueToken(win, REVOKE_LABEL);
  await issueToken(win, KEEP_LABEL);

  // Widerruf läuft - wie die Löschpfade des Portals - über eine ausdrückliche Ja/Nein-Rückfrage.
  const actions = await gridRowActions(page, REVOKE_LABEL, win);
  await actions.getByRole('button', { name: 'Widerrufen' }).click();
  await confirmDeletion(page);

  // ERST auf die aktualisierte ZEILE warten, dann die Zellen einsammeln: vaadin-grid poolt seine
  // <vaadin-grid-cell-content>-Knoten und vergibt beim Neuladen neue Slot-Namen. Ein vor dem
  // Server-Roundtrip eingesammelter Zellen-Lokator zeigt danach auf einen zurückgelassenen Knoten
  // mit dem ALTEN Text ("Aktiv") und läuft in einen Timeout, den es fachlich nicht gibt (siehe
  // helpers.ts/06-ui-tests.md). Der Zeilenname folgt dagegen dem Accessibility-Baum, ist also
  // immer der aktuelle - und expect() pollt, das bleibt ohne waitForTimeout deterministisch.
  const revokedRow = win.getByRole('row', { name: new RegExp(REVOKE_LABEL) });
  await expect(revokedRow).toHaveAccessibleName(/Widerrufen am/);

  // Der Status zeigt den Widerruf samt Zeitpunkt, die Zeile bleibt als Nachweis bestehen ...
  const revokedCells = await gridRowCells(page, REVOKE_LABEL, win);
  await expect(revokedCells[4]).toContainText('Widerrufen am');
  await expect(revokedCells[1]).toHaveText(REVOKE_LABEL);
  // ... und trägt keine Aktion mehr (ein widerrufenes Token lässt sich nicht reaktivieren).
  const revokedActions = await gridRowActions(page, REVOKE_LABEL, win);
  await expect(revokedActions.getByRole('button', { name: 'Widerrufen' })).toHaveCount(0);

  // Das zweite Token ist unverändert gültig.
  const keptCells = await gridRowCells(page, KEEP_LABEL, win);
  await expect(keptCells[4]).toHaveText('Aktiv');
  const keptActions = await gridRowActions(page, KEEP_LABEL, win);
  await expect(keptActions.getByRole('button', { name: 'Widerrufen' })).toHaveCount(1);
});
