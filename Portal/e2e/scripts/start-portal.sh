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
  sudo -u postgres psql -q -f "$REPO_ROOT/Common/resources/database-init.sql"
fi
# Ensure the portal DB user has a password the JDBC driver can use (SCRAM)
sudo -u postgres psql -q -c "ALTER USER elwaportal WITH PASSWORD 'elwaportal';"

# Remove leftover E2E fixtures so the (virtualized) Vaadin tables stay small and
# freshly created rows are rendered. Done as postgres to satisfy the FK chain.
sudo -u postgres psql -q -d elwasys <<'SQL' || true
DELETE FROM credit_accounting WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'e2e\_%' ESCAPE '\')
   OR execution_id IN (SELECT id FROM executions WHERE device_id IN (SELECT id FROM devices WHERE name LIKE 'E2E-%'));
DELETE FROM executions WHERE device_id IN (SELECT id FROM devices WHERE name LIKE 'E2E-%')
   OR user_id IN (SELECT id FROM users WHERE username LIKE 'e2e\_%' ESCAPE '\');
DELETE FROM reservations WHERE device_id IN (SELECT id FROM devices WHERE name LIKE 'E2E-%')
   OR user_id IN (SELECT id FROM users WHERE username LIKE 'e2e\_%' ESCAPE '\');
DELETE FROM devices WHERE name LIKE 'E2E-%';
DELETE FROM programs WHERE name LIKE 'E2E-%';
DELETE FROM users WHERE username LIKE 'e2e\_%' ESCAPE '\';
DELETE FROM user_groups WHERE name LIKE 'E2E-%';
SQL

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
