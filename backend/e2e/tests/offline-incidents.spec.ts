import { test, expect } from '@playwright/test';
import { execFileSync } from 'child_process';
import {
  loginAsAdmin,
  openAdminSection,
  gridRowCells,
  gridRowActions,
  confirmDeletion,
} from './helpers';

/**
 * Offline-Vorfälle der Terminals (test plan P27/P28, Issue #89) - AdminOfflineIncidentsView:
 * ein vom Terminal gemeldeter, noch nicht quittierter Vorfall ist eine verlorene Buchung (Geld)
 * und hält den Betriebsalarm (/actuator/health/operational, OfflineIncidentHealthIndicator).
 * Die Quittierung in dieser View ist der EINZIGE Weg, den Alarm zu beenden - genau das prüfen
 * diese Tests.
 *
 * Die Vorfälle entstehen im Betrieb über den Wartungs-WebSocket (TerminalWebSocketHandler
 * #handleOfflineIncident). Für die Portal-Sicht werden sie hier - wie die Gerätefixtures in
 * dashboard.spec.ts - direkt in die E2E-Datenbank geseedet; das hält den Test auf der
 * Portal-Naht, statt einen eigenen Terminal-Sonderpfad zu bauen (der WS-Meldepfad selbst ist
 * Sache der Backend-/Terminal-Suiten).
 */

const DB_NAME = process.env.E2E_DB_NAME || 'elwasys_backend_e2e';
const STAMP = Date.now();
const OPEN_REASON = `E2E-Vorfall-offen-${STAMP}`;
const ACK_REASON = `E2E-Vorfall-quittiert-${STAMP}`;
const INCIDENT_USER = 'e2e_incident_user';

/** Run a SQL script against the E2E database as the postgres superuser (same pattern as
 * dashboard.spec.ts). */
function sql(script: string) {
  execFileSync('sudo', ['-u', 'postgres', 'psql', '-q', '-v', 'ON_ERROR_STOP=1', '-d', DB_NAME], {
    input: script,
    stdio: ['pipe', 'ignore', 'inherit'],
  });
}

/**
 * Re-seeds both fixtures before EVERY test (one open, one already acknowledged incident) - the
 * P27 test acknowledges the open one, so a per-file seed would make the tests order-dependent.
 */
function seed() {
  sql(`
    DELETE FROM terminal_offline_incidents WHERE incident_key LIKE 'e2e-incident-%';
    DELETE FROM users WHERE username = '${INCIDENT_USER}';

    INSERT INTO users (name, username, is_admin, blocked, deleted, group_id)
      VALUES ('E2E Incident User', '${INCIDENT_USER}', FALSE, FALSE, FALSE,
        (SELECT id FROM user_groups ORDER BY id LIMIT 1));

    -- Offener Vorfall: eine dead-gelettert Offline-Buchung über 3,50 EUR.
    INSERT INTO terminal_offline_incidents (incident_key, location_id, kind, entry_type,
        idempotency_key, user_id, charged_price, reason, occurred_at, reported_at)
      VALUES ('e2e-incident-open-${STAMP}', (SELECT id FROM locations WHERE name='Default'),
        'DEAD_LETTER', 'FINISH', 'e2e-idem-open-${STAMP}',
        (SELECT id FROM users WHERE username='${INCIDENT_USER}'), 3.50, '${OPEN_REASON}',
        NOW() - INTERVAL '2 hours', NOW() - INTERVAL '1 hour');

    -- Bereits quittierter Vorfall: bleibt als Beleg erhalten, taucht aber nicht in der
    -- Standardansicht (offene Vorfälle) auf.
    INSERT INTO terminal_offline_incidents (incident_key, location_id, kind, entry_type,
        idempotency_key, user_id, charged_price, reason, occurred_at, reported_at,
        acknowledged_at, acknowledged_by)
      VALUES ('e2e-incident-ack-${STAMP}', (SELECT id FROM locations WHERE name='Default'),
        'GHOST_EXECUTION', 'ABORT', 'e2e-idem-ack-${STAMP}', NULL, 1.20, '${ACK_REASON}',
        NOW() - INTERVAL '3 hours', NOW() - INTERVAL '2 hours', NOW() - INTERVAL '1 hour',
        'Administrator');
  `);
}

function cleanup() {
  sql(`
    DELETE FROM terminal_offline_incidents WHERE incident_key LIKE 'e2e-incident-%';
    DELETE FROM users WHERE username = '${INCIDENT_USER}';
  `);
}

test.beforeEach(async ({ page }) => {
  seed();
  await loginAsAdmin(page);
});

// Der Health-Indicator zieht /actuator/health/operational auf OUT_OF_SERVICE, solange ein
// offener Vorfall existiert - die Fixtures danach also wieder entfernen.
test.afterAll(() => cleanup());

test('cancelling the confirmation leaves the acknowledge button usable (P27b)', async ({ page }) => {
  // Regression (Review-Gate FR-2): der Knopf trug zunaechst setDisableOnClick(true). Vaadin
  // deaktiviert damit serverseitig BEIM KLICK und reaktiviert nie von selbst - nach einem "Nein"
  // war der Knopf bis zum Neuladen tot. Da die Quittierung der EINZIGE Weg ist, den
  // Betriebsalarm zu beenden, waere das eine Sackgasse gewesen.
  await openAdminSection(page, 'admin/offline-incidents');

  const actions = await gridRowActions(page, new RegExp(OPEN_REASON));
  const acknowledge = actions.getByRole('button', { name: 'Quittieren' });
  await acknowledge.click();
  // Bestaetigung ABBRECHEN.
  await page.getByRole('button', { name: 'Nein', exact: true }).click();

  // Der Vorfall ist unveraendert offen ...
  const cells = await gridRowCells(page, new RegExp(OPEN_REASON));
  await expect(cells[7]).toHaveText('Offen');
  // ... und der Knopf laesst sich erneut betaetigen (das eigentliche Regressionskriterium).
  await expect(acknowledge).toBeEnabled();
  await acknowledge.click();
  await expect(page.getByRole('button', { name: 'Ja', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Nein', exact: true }).click();
});

test('admin sees an open offline incident and can acknowledge it (P27)', async ({ page }) => {
  await openAdminSection(page, 'admin/offline-incidents');

  // Spaltenreihenfolge (AdminOfflineIncidentsView#configureGrid): Aufgetreten(0), Gemeldet(1),
  // Standort(2), Art(3), Benutzer(4), Betrag(5), Grund(6), Status(7), Aktionen(8).
  const cells = await gridRowCells(page, new RegExp(OPEN_REASON));
  await expect(cells[2]).toHaveText('Default');
  // Verständliche Bezeichnung statt des rohen "DEAD_LETTER".
  await expect(cells[3]).toHaveText('Verlorene Buchung');
  await expect(cells[4]).toHaveText('E2E Incident User');
  // Der Schaden in deutscher Währungsschreibweise (PortalFormats#currency).
  await expect(cells[5]).toContainText('3,50');
  await expect(cells[7]).toHaveText('Offen');

  // Quittieren läuft - wie die Löschpfade - über eine ausdrückliche Ja/Nein-Bestätigung.
  const actions = await gridRowActions(page, new RegExp(OPEN_REASON));
  await actions.getByRole('button', { name: 'Quittieren' }).click();
  await confirmDeletion(page);
  const notification = page.getByText('Der Vorfall wurde quittiert.');
  await expect(notification).toBeVisible();
  // Eine Vaadin-Notification in Position MIDDLE ist modal und blockiert bis zu ihrem Ablauf
  // JEDE Eingabe (auch einen force-Klick) - deshalb auf ihr Verschwinden warten, statt zu
  // raten. Deterministisch: expect() pollt, kein waitForTimeout.
  await expect(notification).toBeHidden();

  // Der Vorfall ist aus der (standardmäßig gezeigten) Liste der OFFENEN Vorfälle verschwunden -
  // auf die Grid-ZEILE prüfen, nicht auf den Zellentext (vaadin-grid poolt seine
  // <vaadin-grid-cell-content>-Knoten im Light-DOM, siehe helpers.ts/06-ui-tests.md).
  await expect(page.getByRole('row', { name: new RegExp(OPEN_REASON) })).toHaveCount(0);

  // Als Beleg bleibt er über den Umschalter einsehbar - jetzt als "Quittiert" und ohne Aktion.
  await page.getByLabel('Auch quittierte Vorfälle anzeigen').check({ force: true });
  const ackCells = await gridRowCells(page, new RegExp(OPEN_REASON));
  await expect(ackCells[7]).toHaveText('Quittiert');
  const ackActions = await gridRowActions(page, new RegExp(OPEN_REASON));
  await expect(ackActions.getByRole('button', { name: 'Quittieren' })).toHaveCount(0);
});

test('acknowledged incidents stay visible as a record and the dashboard points to open ones (P28)',
  async ({ page }) => {
    // Das Dashboard ist die erste Seite nach dem Admin-Login: bei offenen Vorfällen weist es
    // darauf hin und verlinkt in die Vorfallsliste (AdminDashboardView#refreshIncidentBanner).
    await openAdminSection(page, 'admin');
    const banner = page.locator('.dashboard-incident-banner');
    await expect(banner).toBeVisible();
    await expect(banner).toContainText('Offline-Vorfall');
    await banner.getByRole('link', { name: 'Offline-Vorfälle' }).click();
    await expect(page).toHaveURL(/\/admin\/offline-incidents(\?|$)/);

    // Standardansicht: nur offene Vorfälle - der bereits quittierte fehlt.
    await expect(page.getByRole('row', { name: new RegExp(OPEN_REASON) })).toHaveCount(1);
    await expect(page.getByRole('row', { name: new RegExp(ACK_REASON) })).toHaveCount(0);

    // Umschalter zeigt ihn als Beleg, inkl. verständlicher Art und Betrag.
    await page.getByLabel('Auch quittierte Vorfälle anzeigen').check({ force: true });
    const cells = await gridRowCells(page, new RegExp(ACK_REASON));
    await expect(cells[3]).toHaveText('Geister-Ausführung');
    // Kein Benutzer gemeldet (bzw. zwischenzeitlich gelöscht) - die Zelle bleibt "-".
    await expect(cells[4]).toHaveText('-');
    await expect(cells[5]).toContainText('1,20');
    await expect(cells[7]).toHaveText('Quittiert');

    // Zurückschalten blendet ihn wieder aus; der offene Vorfall bleibt sichtbar.
    await page.getByLabel('Auch quittierte Vorfälle anzeigen').uncheck({ force: true });
    await expect(page.getByRole('row', { name: new RegExp(ACK_REASON) })).toHaveCount(0);
    await expect(page.getByRole('row', { name: new RegExp(OPEN_REASON) })).toHaveCount(1);
  });
