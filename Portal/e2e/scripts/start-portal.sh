#!/bin/bash
# Idempotently bring up the elwasys Portal for E2E tests:
#   1. start PostgreSQL, 2. seed the elwasys DB (once), 3. write the portal
#   config, 4. install Common, 5. run `mvn jetty:run` in the foreground.
# Playwright's webServer waits for http://localhost:8080 before running tests.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
PG_VER=16

echo "[start-portal] repo root: $REPO_ROOT"

# 1. Start PostgreSQL cluster (ignore if already running)
sudo pg_ctlcluster "$PG_VER" main start 2>/dev/null || true
for i in $(seq 1 30); do pg_isready -q && break; sleep 1; done
pg_isready || { echo "[start-portal] PostgreSQL not ready"; exit 1; }

# 2. Seed the database once (database-init.sql creates DB, schema, roles, seeds)
if ! sudo -u postgres psql -lqt | cut -d'|' -f1 | grep -qw elwasys; then
  echo "[start-portal] initializing elwasys database"
  # Feed the SQL via stdin: the postgres OS user may not have read access to
  # the repo checkout (e.g. under /home/runner in CI), so read it as the
  # invoking user and pipe it in.
  sudo -u postgres psql -q < "$REPO_ROOT/Common/resources/database-init.sql"
fi
# Ensure the portal DB user has a password the JDBC driver can use (SCRAM)
sudo -u postgres psql -q -c "ALTER USER elwaportal WITH PASSWORD 'elwaportal';"

# 3. Portal configuration
sudo mkdir -p /etc/elwaportal
sudo tee /etc/elwaportal/elwaportal.properties >/dev/null <<'EOF'
database.server=localhost:5432
database.name=elwasys
database.user=elwaportal
database.password=elwaportal
smtp.server=
smtp.port=465
smtp.user=
smtp.password=
smtp.useSSL=false
smtp.senderAddress=noreply@example.com
maintenance.timeout=20
maintenance.server.port=3591
admin.password=admin
EOF

# 4. Make sure Common is available to the Portal build
mvn -q -B -f "$REPO_ROOT/Common/pom.xml" install -DskipTests

# 5. Run the portal (foreground; Playwright tears this down after the run)
echo "[start-portal] starting Jetty on :8080"
exec mvn -B -f "$REPO_ROOT/Portal/pom.xml" jetty:run
