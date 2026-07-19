# elwasys Portal – E2E Tests (Playwright)

End-to-end UI tests for the Vaadin 7 web portal, driven through a real browser.

## Prerequisites

- Node.js + npm
- Java + Maven (to build/run the portal)
- PostgreSQL (the start script uses the local cluster `16/main`)
- A Chromium browser. In the Claude Code remote environment it is pre-installed
  at `/opt/pw-browsers/chromium`; the Playwright config points `executablePath`
  there, so **no `playwright install` is needed**. Elsewhere, run
  `npx playwright install chromium` and unset `PLAYWRIGHT_CHROMIUM_PATH`.

## Install

```bash
cd Portal/e2e
PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 npm install
```

## Run

```bash
# Boots PostgreSQL + seeds the DB + runs `mvn jetty:run`, then tests:
npx playwright test

# Against an already-running portal on :8080 (skip the built-in web server):
E2E_NO_WEBSERVER=1 npx playwright test
```

`scripts/start-portal.sh` is idempotent: it starts PostgreSQL, seeds the
`elwasys` database from `Common/resources/database-init.sql` (once), writes
`/etc/elwaportal/elwaportal.properties`, installs `Common`, and runs Jetty on
port 8080. Playwright waits for the server, runs the tests, and tears it down.

## Tests

- `tests/login.spec.ts`
  - the public login page renders (title, username/password fields, Login button)
  - the seeded admin (`admin` / `admin`) can log in and reach the admin dashboard

## Notes on Vaadin 7 selectors

Vaadin 7 does not emit stable element ids, so locators use Vaadin CSS classes
(`input.v-textfield`, `.v-button`) and visible captions. When adding tests,
prefer visible text (menu captions, button labels) over generated ids.
