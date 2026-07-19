#!/bin/bash
# Run the Raspi-client END-TO-END test: it launches the real JavaFX application
# headlessly against a local PostgreSQL database and the in-project fhem
# simulator. This script makes the environment reproducible:
#   1. start PostgreSQL, 2. seed the elwasys DB (once), 3. run the E2E test
#      under Xvfb.
# See kb/06-ui-tests.md.
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
  echo "[run-client-e2e] initializing elwasys database"
  sudo -u postgres psql -q -f "$REPO_ROOT/Common/resources/database-init.sql"
fi

# 3. Ensure Common is installed, then run the E2E test headlessly
mvn -q -B -f "$REPO_ROOT/Common/pom.xml" install -DskipTests
exec xvfb-run -a --server-args="-screen 0 1024x768x24" \
  mvn -B test -Dtest=ClientAppE2ETest
