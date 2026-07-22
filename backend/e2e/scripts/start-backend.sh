#!/bin/bash
# Idempotently bring up the elwasys backend Portal (Vaadin Flow, Phase 3 AP6) for E2E tests -
# fachlicher Nachfolger von Portal/e2e/scripts/start-portal.sh (Vaadin 7), siehe
# kb/06-ui-tests.md, kb/05-migration-plan.md.
#
#   1. start PostgreSQL
#   2. create a FRESH, dedicated E2E database (dropped+recreated every run, same pattern as
#      backend/run-backend-tests.sh - guarantees a clean, reproducible starting point instead
#      of accumulating E2E fixtures across runs)
#   3. build Common (+ the aggregator parent POM) and the backend jar in production mode
#   4. seed the admin/admin login via the admin-cli profile (see below - since Phase 5 AP2 the
#      Flyway baseline no longer ships a known admin password, see
#      V7__remove_default_admin_password.sql)
#   5. run the jar in the foreground on E2E_BACKEND_PORT (default 8081 - deliberately NOT 8080,
#      so this suite cannot collide with a manually-started legacy Portal/e2e run on the same
#      host)
#
# Playwright's webServer waits for http://localhost:<port>/login before running tests; the
# extra non-admin fixtures (P15-P19) are seeded separately by ../global-setup.ts, which is
# guaranteed by Playwright to run strictly after that readiness check succeeds - see that
# file's comment for why the ordering matters and isn't handled here.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
PG_VER=16
DB_NAME="${E2E_DB_NAME:-elwasys_backend_e2e}"
PORT="${E2E_BACKEND_PORT:-8081}"

echo "[start-backend] repo root: $REPO_ROOT"

# 1. Start PostgreSQL cluster (ignore if already running)
sudo pg_ctlcluster "$PG_VER" main start 2>/dev/null || true
for i in $(seq 1 30); do pg_isready -q && break; sleep 1; done
pg_isready || { echo "[start-backend] PostgreSQL not ready"; exit 1; }

# Tests/fixtures connect over TCP as the postgres superuser (same pattern as
# backend/run-backend-tests.sh and Portal/e2e/scripts/start-portal.sh).
sudo -u postgres psql -q -c "ALTER USER postgres WITH PASSWORD 'postgres';"

# 2. Fresh database every run. Flyway migrates the baseline schema from scratch on first
#    application start (see backend/src/main/resources/db/migration/V1__baseline_schema_0_4_0.sql),
#    which already seeds the admin user (admin/admin), the "Default" user group and the
#    "Default" location - the same starting point database-init.sql gives the legacy suite.
echo "[start-backend] (re-)creating database $DB_NAME"
sudo -u postgres psql -q -c "DROP DATABASE IF EXISTS ${DB_NAME};"
sudo -u postgres psql -q -c "CREATE DATABASE ${DB_NAME};"

# 3. Build the backend jar in production mode (mvn package -Pproduction) - the only build
#    mode verified to run without a Vaadin dev-mode license check in this sandbox, see
#    kb/05-migration-plan.md "Phase 3 AP2"/"AP6". Building via the root reactor (-pl backend)
#    resolves the aggregator parent POM as part of the same build, so no separate install is
#    needed. Tests are skipped here (backend/run-backend-tests.sh is the dedicated, faster
#    path for the backend's own JUnit suite).
mvn -q -B -f "$REPO_ROOT/pom.xml" package -pl backend -Pproduction -DskipTests

# Env vars shared by steps 4 and 5 below (foreground run; Playwright tears this down after
# the run).
export ELWASYS_DB_URL="jdbc:postgresql://localhost:5432/${DB_NAME}"
export ELWASYS_DB_USER="postgres"
export ELWASYS_DB_PASSWORD="postgres"
export SERVER_PORT="$PORT"
# Password-Reset bleibt beim Default AN (siehe application.yml/kb/05-migration-plan.md
# "Entscheidungen") - P19 testet genau dieses Verhalten (Dialog öffnet, Fehlermeldung bei
# unbekannter Email-Adresse, kein Crash) ohne konfiguriertes SMTP.
export ELWASYS_PASSWORD_RESET_ENABLED="${ELWASYS_PASSWORD_RESET_ENABLED:-true}"

# 4. Seed the admin/admin login (helpers.ts ADMIN_PASSWORD, P1/P2 login.spec.ts and every
#    other spec that logs in as admin) BEFORE starting the server: the admin-cli profile
#    (AdminPasswordCliRunner) migrates the DB itself (same Flyway config as the real server,
#    see application.yml/application-admin-cli.yml) and exits, then step 5 starts the actual
#    server against the now-migrated, admin-seeded database - same two-step pattern as
#    Client-Raspi/ci-support/start-test-backend.sh's token-cli seeding.
echo "[start-backend] seeding admin/admin login (admin-cli)"
java -jar "$REPO_ROOT/backend/target/elwasys-backend.jar" \
    --spring.profiles.active=admin-cli --username=admin --password=admin

# 5. Run the backend (foreground; Playwright tears this down after the run).
echo "[start-backend] starting backend on :${PORT}"
exec java -jar "$REPO_ROOT/backend/target/elwasys-backend.jar"
