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
  # Feed the SQL via stdin: the postgres OS user may not have read access to
  # the repo checkout (e.g. under /home/runner in CI), so read it as the
  # invoking user and pipe it in.
  sudo -u postgres psql -q < "$REPO_ROOT/Common/resources/database-init.sql"
fi
# The E2E tests seed/clean fixtures (devices, programs, users, executions,
# credit_accounting) via JDBC as the postgres superuser, which needs a password
# the driver can use over TCP.
sudo -u postgres psql -q -c "ALTER USER postgres WITH PASSWORD 'postgres';"

# 3. Ensure Common (and its parent POM) are installed, then run the E2E tests
#    headlessly. Building via the root reactor (-pl Common -am) also installs
#    the aggregator parent POM into the local repo, which a plain
#    "mvn -f Common/pom.xml install" does not.
mvn -q -B -f "$REPO_ROOT/pom.xml" install -pl Common -am -DskipTests
exec xvfb-run -a --server-args="-screen 0 1024x768x24" \
  mvn -B test -Dtest='*E2ETest'
