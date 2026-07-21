#!/bin/bash
# Run the Client-Raspi JavaFX UI tests headlessly via Xvfb.
# Usage:
#   ./run-ui-tests.sh                 # run all UI tests
#   ./run-ui-tests.sh HeadlessFxSmokeTest   # run a single test class
#
# Requires: JDK, Maven, Xvfb (installed by the SessionStart hook / cloud-init).
# See kb/06-ui-tests.md and kb/07-cloud-init.md.
#
# Since Phase 4 AP4 (client cutover to the REST API, see kb/05-migration-plan.md)
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

# 2. Seed the database once (creates DB, schema, roles incl. elwaclient1 and elwaportal -
#    elwaportal is also the role the backend connects as, see backend/application.yml)
if ! sudo -u postgres psql -lqt | cut -d'|' -f1 | grep -qw elwasys; then
  echo "[run-ui-tests] initializing elwasys database"
  sudo -u postgres psql -q < "$REPO_ROOT/Common/resources/database-init.sql"
fi
# The E2E tests seed fixtures via JDBC as the postgres superuser (they need to
# clean up credit_accounting, which the elwaportal role may not delete). Give
# postgres a password the driver can use over TCP.
sudo -u postgres psql -q -c "ALTER USER postgres WITH PASSWORD 'postgres';"

# 3. Ensure the Common library (and its parent POM) are available in the local
# Maven repo. Building via the root reactor (-pl Common -am) also installs
# the aggregator parent POM, which "mvn -f Common/pom.xml install" alone does
# not — and Client-Raspi's dependency resolution needs it on the local repo.
mvn -q -B -f "$REPO_ROOT/pom.xml" install -pl Common -am -DskipTests

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
