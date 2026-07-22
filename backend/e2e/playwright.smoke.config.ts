import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright configuration for the POST-DEPLOY SMOKE subset (Phase 6 AP6, siehe
 * kb/05-migration-plan.md "Phase 6" und kb/06-ui-tests.md) - fährt die schlanke, strikt
 * READ-ONLY Liveness-Teilmenge (tests-smoke/) gegen eine BEREITS LAUFENDE, extern deployte
 * Umgebung. Das ist das Rollout-Gate: Ein Rollout (docker-compose-Redeploy bzw. Helm-Upgrade)
 * gilt erst nach GRÜNEM Smoke-Test als erfolgreich (aufgerufen über
 * deploy/smoke/post-deploy-smoke.sh).
 *
 * Bewusste Unterschiede zur Haupt-Config (playwright.config.ts):
 *   - KEIN `webServer`: Die Umgebung ist bereits deployt und läuft; wir starten hier nichts.
 *   - KEIN `globalSetup`: KEIN direkter DB-Seed. Eine entfernte Deployment-Umgebung ist "fertig
 *     deployt"; die Smoke-Tests dürfen NICHTS über konkrete Seed-Daten annehmen und nichts
 *     mutieren.
 *   - `baseURL` aus E2E_BASE_URL (Default http://localhost:8080 - der Produktions-Port aus
 *     deploy/compose/docker-compose.yml), nicht der 8081-Default der Haupt-Suite.
 *   - `testDir: './tests-smoke'` (eigene, additive Teilmenge; die Haupt-Suite tests/ bleibt
 *     unberührt).
 *
 * Der Browser ist das in der Umgebung vorinstallierte Chromium (/opt/pw-browsers/chromium),
 * referenziert über `executablePath` - identisch zur Haupt-Config, kein Browser-Download nötig.
 */
const CHROME = process.env.PLAYWRIGHT_CHROMIUM_PATH || '/opt/pw-browsers/chromium';
const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:8080';

export default defineConfig({
  testDir: './tests-smoke',
  timeout: 60_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  // Default 0 (ein Flake soll als Fehler sichtbar werden, nicht still durchgehen). In einer
  // frisch hochgefahrenen Umgebung kann ein erster Retry sinnvoll sein - per SMOKE_RETRIES
  // überschreibbar.
  retries: process.env.SMOKE_RETRIES ? Number(process.env.SMOKE_RETRIES) : 0,
  reporter: [['list']],
  use: {
    baseURL: BASE_URL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    launchOptions: { executablePath: CHROME },
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'], launchOptions: { executablePath: CHROME } },
    },
  ],
  // Kein webServer: gegen eine bereits laufende, extern deployte Umgebung.
});
