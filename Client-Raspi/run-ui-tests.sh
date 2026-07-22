#!/bin/bash
# Run the Client-Raspi JavaFX UI tests headlessly via Xvfb.
# Usage:
#   ./run-ui-tests.sh                 # run all UI tests
#   ./run-ui-tests.sh HeadlessFxSmokeTest   # run a single test class
#
# Requires: JDK, Maven, Xvfb (installed by the SessionStart hook / cloud-init).
# See docs/kb/06-ui-tests.md and docs/kb/07-cloud-init.md.
#
# Since Phase 4 AP4 (client cutover to the REST API, see docs/kb/05-migration-plan.md)
# the full-app E2E tests (`*E2ETest`) talk to a running elwasys backend instead
# of the database directly. `mvn test` (no filter) runs those too, so this script
# now brings up the same reproducible backend as run-client-e2e.sh: start
# PostgreSQL, seed the elwasys DB once, build+start the backend jar (Flyway runs
# automatically) and seed a terminal token. A single test class run
# (./run-ui-tests.sh SomeUnitTest) still gets a backend; that is cheap and keeps
# the harness uniform.
set -euo pipefail

cd "$(dirname "$0")"
REPO_ROOT="$(cd .. && pwd)"
PG_VER=16

# 1. PostgreSQL
sudo pg_ctlcluster "$PG_VER" main start 2>/dev/null || true
for i in $(seq 1 30); do pg_isready -q && break; sleep 1; done
pg_isready || { echo "PostgreSQL not ready"; exit 1; }

# 2. Seed the database once (creates DB, schema, roles incl. elwaclient1 - a frozen
#    baseline artifact from database-init.sql that Flyway migration V6 immediately
#    drops again once the backend starts, since the Client-Raspi terminals no longer
#    access the DB directly since Phase 4 AP4/AP5. The E2E harness here connects as
#    the postgres superuser, see below; elwaportal remains the backend's productive
#    DB role, see backend/application.yml)
if ! sudo -u postgres psql -lqt | cut -d'|' -f1 | grep -qw elwasys; then
  echo "[run-ui-tests] initializing elwasys database"
  # Apply the Flyway V1 baseline (0.4.0 schema) directly via psql - it is the SINGLE
  # source of truth for the legacy/base schema (the former standalone
  # database/database-init.sql was a byte-equivalent duplicate and was removed; proven
  # equivalent via pg_dump). V1 carries no CREATE DATABASE preamble (Flyway migrates an
  # already-selected DB), so create the database first, then pipe V1 into it.
  sudo -u postgres psql -q -c "CREATE DATABASE elwasys;"
  sudo -u postgres psql -q -d elwasys < "$REPO_ROOT/backend/src/main/resources/db/migration/V1__baseline_schema_0_4_0.sql"
fi
# The E2E tests seed fixtures via JDBC as the postgres superuser (they need to
# clean up credit_accounting, which the elwaportal role may not delete). Give
# postgres a password the driver can use over TCP.
sudo -u postgres psql -q -c "ALTER USER postgres WITH PASSWORD 'postgres';"

# 3. Ensure the aggregator parent POM is available in the local Maven repo so
# Client-Raspi's per-module build can resolve it. "mvn -N install" installs just
# that parent POM. (The former "common" module was dissolved after the migration;
# its classes now live in Client-Raspi/src/main, so there is nothing else to install.)
mvn -q -B -N -f "$REPO_ROOT/pom.xml" install -DskipTests

# 4. Build+start the backend jar and seed a terminal token (exports
#    ELWASYS_TEST_BACKEND_URL/-TOKEN, stops the backend on exit).
# Already in the script directory (cd above), so source relative to it - using
# "$(dirname "$0")" again here would double-nest the path when the script is invoked
# with a path prefix (e.g. CI's `bash Client-Raspi/run-ui-tests.sh`).
source ci-support/start-test-backend.sh
start_test_backend

TEST_ARG=()
if [ "$#" -gt 0 ]; then
  TEST_ARG=(-Dtest="$1")
fi

# 5. Run the UI tests headlessly.
xvfb-run -a --server-args="-screen 0 1024x768x24" \
  mvn -B test "${TEST_ARG[@]}"
