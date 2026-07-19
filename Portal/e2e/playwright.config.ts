import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright configuration for the elwasys Portal E2E tests.
 *
 * The browser is the Chromium pre-installed in the environment
 * (/opt/pw-browsers/chromium is a symlink to the chrome binary), referenced
 * via `executablePath` so no browser download is required.
 *
 * By default a Portal instance is started via scripts/start-portal.sh (which
 * boots PostgreSQL, seeds the DB, and runs `mvn jetty:run`). Set
 * E2E_NO_WEBSERVER=1 to test against an already-running server on :8080.
 */
const CHROME = process.env.PLAYWRIGHT_CHROMIUM_PATH || '/opt/pw-browsers/chromium';

export default defineConfig({
  testDir: './tests',
  timeout: 60_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: 'http://localhost:8080',
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
        command: 'bash scripts/start-portal.sh',
        url: 'http://localhost:8080/',
        reuseExistingServer: true,
        timeout: 240_000,
        stdout: 'pipe',
        stderr: 'pipe',
      },
});
