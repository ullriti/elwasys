#!/bin/bash
# Run the Raspi-client END-TO-END test: it launches the real JavaFX application
# headlessly against the elwasys backend (REST API v1, Phase 4 AP4 - see
# kb/05-migration-plan.md "Client-Cutover") and the in-project fhem/deCONZ
# simulators. This script makes the environment reproducible:
#   1. start PostgreSQL, 2. seed the elwasys DB (once), 3. build+start the
#      backend jar (runs Flyway automatically) and seed a terminal token,
#      4. run the E2E test under Xvfb.
# See kb/06-ui-tests.md.
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
  echo "[run-client-e2e] initializing elwasys database"
  # Feed the SQL via stdin: the postgres OS user may not have read access to
  # the repo checkout (e.g. under /home/runner in CI), so read it as the
  # invoking user and pipe it in.
  sudo -u postgres psql -q < "$REPO_ROOT/database/database-init.sql"
fi
# The E2E tests seed/clean fixtures (devices, programs, users, executions,
# credit_accounting) via JDBC as the postgres superuser, which needs a password
# the driver can use over TCP.
sudo -u postgres psql -q -c "ALTER USER postgres WITH PASSWORD 'postgres';"

# 3. Ensure the aggregator parent POM is installed, then build the backend jar.
#    "mvn -N install" installs just that parent POM into the local repo so the
#    per-module builds can resolve it. (The former "common" module was dissolved
#    after the migration; its classes now live in Client-Raspi/src/main.)
mvn -q -B -N -f "$REPO_ROOT/pom.xml" install -DskipTests
# Already in the script directory (cd above), so source relative to it - using
# "$(dirname "$0")" again here would double-nest the path when the script is invoked
# with a path prefix (e.g. CI's `bash Client-Raspi/run-client-e2e.sh`).
source ci-support/start-test-backend.sh
start_test_backend

# 4. Run the E2E tests headlessly.
xvfb-run -a --server-args="-screen 0 1024x768x24" \
  mvn -B test -Dtest='*E2ETest'
