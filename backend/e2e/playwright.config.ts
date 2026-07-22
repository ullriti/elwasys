import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright configuration for the elwasys backend Portal E2E tests (Vaadin Flow, Phase 3
 * AP6) - fachlicher Nachfolger von Portal/e2e/playwright.config.ts (Vaadin 7), siehe
 * docs/kb/06-ui-tests.md und docs/kb/05-migration-plan.md.
 *
 * The browser is the Chromium pre-installed in the environment
 * (/opt/pw-browsers/chromium is a symlink to the chrome binary), referenced via
 * `executablePath` so no browser download is required.
 *
 * By default a backend instance is started via scripts/start-backend.sh (which boots
 * PostgreSQL, creates a fresh dedicated E2E database, builds the backend jar in production
 * mode - the only build mode verified to run without a Vaadin dev-mode license check in this
 * sandbox, see docs/kb/05-migration-plan.md "Phase 3 AP2" - and runs it). Set E2E_NO_WEBSERVER=1
 * to test against an already-running backend instead.
 *
 * globalSetup (./global-setup.ts) seeds the two non-admin test fixtures (P15-P19) AFTER
 * Playwright's webServer has confirmed the app is reachable - see that file's comment for why
 * this is not done by scripts/start-backend.sh itself (race with Playwright's own readiness
 * poll against the very same port).
 */
const CHROME = process.env.PLAYWRIGHT_CHROMIUM_PATH || '/opt/pw-browsers/chromium';
const PORT = process.env.E2E_BACKEND_PORT || '8081';
const BASE_URL = `http://localhost:${PORT}`;

export default defineConfig({
  testDir: './tests',
  globalSetup: require.resolve('./global-setup.ts'),
  timeout: 60_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  // The suite must be provably stable (see docs/kb/05-migration-plan.md, Phase 3 AP6 acceptance
  // criterion) - retries are disabled by default so a flake shows up as a failure, not a
  // silently-passing report. Set E2E_RETRIES to opt into CI-style retries when investigating.
  retries: process.env.E2E_RETRIES ? Number(process.env.E2E_RETRIES) : 0,
  reporter: [['list'], ['html', { open: 'never' }]],
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
  webServer: process.env.E2E_NO_WEBSERVER
    ? undefined
    : {
        command: 'bash scripts/start-backend.sh',
        url: `${BASE_URL}/login`,
        reuseExistingServer: true,
        // Generous timeout: covers the production Maven build (mvn package -Pproduction,
        // ~20s warm / longer cold) plus Spring Boot + Flyway startup.
        timeout: 300_000,
        stdout: 'pipe',
        stderr: 'pipe',
        env: { E2E_BACKEND_PORT: PORT },
      },
});
