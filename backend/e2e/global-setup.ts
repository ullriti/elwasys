import { execFileSync } from 'child_process';
import { request } from '@playwright/test';

/**
 * Runs once before any test file, AFTER Playwright's `webServer` has confirmed the backend
 * answers on PORT/login (see playwright.config.ts) - seeds the two non-admin fixtures used by
 * tests/user-portal.spec.ts (P15-P19), fachlicher Nachfolger der entsprechenden Inserts in
 * Portal/e2e/scripts/start-portal.sh.
 *
 * Why this lives in globalSetup and not in scripts/start-backend.sh: the schema (and the
 * Flyway-baseline-seeded admin user/"Default" group/location, see
 * backend/src/main/resources/db/migration/V1__baseline_schema_0_4_0.sql) only exists once the
 * Spring Boot application has started and Flyway has migrated the fresh, empty E2E database -
 * i.e. not before the same moment Playwright's own `webServer.url` poll can start succeeding
 * against the very same port. Seeding from inside start-backend.sh (in a background subshell
 * racing its own health poll) would race Playwright's poll of the identical endpoint with no
 * guaranteed ordering. globalSetup has a documented, guaranteed ordering instead (Playwright
 * runs it strictly after `webServer` is ready and strictly before any test), so this is the
 * only place that can deterministically depend on the schema already existing.
 *
 * Idempotent (safe against E2E_NO_WEBSERVER=1 re-runs against an already-seeded database):
 * both inserts are guarded by "WHERE NOT EXISTS".
 *
 * Password for both users is "test" (SHA1 a94a8fe5ccb19ba61c4c0873d391e987982fbbd3, same
 * legacy hash the seed scripts have always used) - the backend's auth layer verifies legacy
 * SHA1 hashes unchanged (Phase 2 AP3 parallel-operation behaviour, rehash-on-login stays off
 * by default, see kb/05-migration-plan.md), so this is a faithful stand-in for "a user who has
 * never logged into the new portal before".
 */
async function globalSetup() {
  const port = process.env.E2E_BACKEND_PORT || '8081';
  const dbName = process.env.E2E_DB_NAME || 'elwasys_backend_e2e';
  const baseURL = `http://localhost:${port}`;

  // Belt-and-suspenders: webServer already guarantees this, but E2E_NO_WEBSERVER=1 runs skip
  // that guarantee, so wait here too before touching the database.
  const ctx = await request.newContext();
  let healthy = false;
  for (let i = 0; i < 120; i++) {
    try {
      const res = await ctx.get(`${baseURL}/actuator/health`);
      if (res.ok()) {
        healthy = true;
        break;
      }
    } catch {
      // not up yet
    }
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }
  await ctx.dispose();
  if (!healthy) {
    throw new Error(`[global-setup] backend at ${baseURL} did not become healthy in time`);
  }

  const sql = `
    INSERT INTO users (name, username, password, is_admin, blocked, deleted, group_id)
      SELECT 'E2E Portal User', 'e2e_portal_user', 'a94a8fe5ccb19ba61c4c0873d391e987982fbbd3',
        FALSE, FALSE, FALSE, (SELECT id FROM user_groups WHERE name = 'Default')
      WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'e2e_portal_user');
    INSERT INTO users (name, username, password, is_admin, blocked, deleted, group_id)
      SELECT 'E2E PwChange User', 'e2e_pwchange_user', 'a94a8fe5ccb19ba61c4c0873d391e987982fbbd3',
        FALSE, FALSE, FALSE, (SELECT id FROM user_groups WHERE name = 'Default')
      WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'e2e_pwchange_user');
  `;
  execFileSync('sudo', ['-u', 'postgres', 'psql', '-q', '-v', 'ON_ERROR_STOP=1', '-d', dbName], {
    input: sql,
    stdio: ['pipe', 'inherit', 'inherit'],
  });
  console.log('[global-setup] seeded e2e_portal_user / e2e_pwchange_user');
}

export default globalSetup;
