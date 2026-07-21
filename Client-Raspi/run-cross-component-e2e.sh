#!/bin/bash
# Run the CROSS-COMPONENT end-to-end test (test plan P21/P22): it boots the real
# JavaFX client headlessly and a real MaintenanceServer (the class the portal
# wraps) and drives the portal⇄client maintenance channel over a TCP socket.
# Steps: 1. start PostgreSQL, 2. seed the elwasys DB (once), 3. build+start the
#      backend jar and seed a terminal token, 4. run the maintenance-connection
#      E2E under Xvfb.
# See kb/06-ui-tests.md and kb/08-test-plan.md.
#
# Since Phase 4 AP4 (client cutover to the REST API, see kb/05-migration-plan.md) the full
# app startup path (ElwaManager#initiate, exercised by ClientMaintenanceConnectionE2ETest via
# Main) always wires an ApiClient, so this test needs a running backend + seeded terminal
# token exactly like run-ui-tests.sh/run-client-e2e.sh, even though the maintenance channel
# itself stays on the transitional direct-DB path (LocationManager) until AP5.
set -euo pipefail

cd "$(dirname "$0")"
REPO_ROOT="$(cd .. && pwd)"
PG_VER=16

# 1. PostgreSQL
sudo pg_ctlcluster "$PG_VER" main start 2>/dev/null || true
for i in $(seq 1 30); do pg_isready -q && break; sleep 1; done
pg_isready || { echo "PostgreSQL not ready"; exit 1; }

# 2. Seed the database once (creates DB, schema, roles incl. elwaclient1)
if ! sudo -u postgres psql -lqt | cut -d'|' -f1 | grep -qw elwasys; then
  echo "[run-cross-component-e2e] initializing elwasys database"
  sudo -u postgres psql -q -f "$REPO_ROOT/Common/resources/database-init.sql"
fi
# The test resets the location registration via JDBC as the postgres superuser,
# which needs a password the driver can use over TCP.
sudo -u postgres psql -q -c "ALTER USER postgres WITH PASSWORD 'postgres';"

# 3. Ensure Common (and its parent POM) are installed. Building via the root
#    reactor (-pl Common -am) also installs the aggregator parent POM into the
#    local repo, which a plain "mvn -f Common/pom.xml install" does not.
mvn -q -B -f "$REPO_ROOT/pom.xml" install -pl Common -am -DskipTests

# 4. Build+start the backend jar and seed a terminal token (exports
#    ELWASYS_TEST_BACKEND_URL/-TOKEN, stops the backend on exit) - see comment above.
source "$(dirname "$0")/ci-support/start-test-backend.sh"
start_test_backend

# 5. Run the cross-component E2E headlessly.
xvfb-run -a --server-args="-screen 0 1024x768x24" \
  mvn -B test -Dtest=ClientMaintenanceConnectionE2ETest
