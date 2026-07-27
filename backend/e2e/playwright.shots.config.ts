import { defineConfig } from '@playwright/test';
import baseConfig from './playwright.config';

/**
 * Konfiguration für den Screenshot-Durchlauf (`tests-shots/portal-shots.spec.ts`) - kein
 * Regressionstest, sondern das Werkzeug für die optische Abnahme des Portals.
 *
 * Warum eine eigene Konfiguration statt eines Pfad-Arguments: Playwright behandelt Pfade auf
 * der Kommandozeile als Filter INNERHALB von `testDir`. Ein
 * `npx playwright test tests-shots/portal-shots.spec.ts` liefert deshalb "No tests found",
 * solange `testDir` auf `./tests` steht. Und `-c tests-shots` würde zwar laufen, aber ohne die
 * Projekt-Konfiguration (Browser-Pfad, baseURL, webServer) - der Durchlauf hätte dann weder
 * einen Browser noch einen Server. Also: die Basis-Konfiguration erben und nur `testDir`
 * umbiegen.
 *
 *   cd backend/e2e && npx playwright test -c playwright.shots.config.ts
 *
 * Zielverzeichnis der Bilder über SHOT_DIR (Voreinstellung /tmp/portal-shots).
 */
export default defineConfig({
  ...baseConfig,
  testDir: './tests-shots',
  // Der Durchlauf fotografiert auch Fehlerzustände (Login mit falschem Passwort) und wartet
  // an mehreren Stellen auf gerenderte Dialoge - großzügiger als die Regressionssuite.
  timeout: 180_000,
  reporter: [['list']],
});
