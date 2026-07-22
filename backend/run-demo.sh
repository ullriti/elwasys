#!/bin/bash
# Startet das elwasys-Backend samt Portal (Vaadin Flow) mit dem Profil "demo" gegen eine
# lokale PostgreSQL-Demo-Datenbank und legt dabei ueber den DemoDataSeeder einen realistischen,
# wiederverwendbaren Beispielbestand an - gedacht fuer das visuelle Pruefen von UI-Aenderungen,
# ohne die Daten jedes Mal von Hand anzulegen (siehe DemoDataSeeder-Javadoc, docs/kb/06-ui-tests.md).
#
# Ablauf (analog zu backend/e2e/scripts/start-backend.sh, aber NICHT ephemer):
#   1. PostgreSQL starten
#   2. Demo-Datenbank anlegen, falls sie noch nicht existiert (Daten bleiben ueber Neustarts
#      erhalten; der Seeder ist idempotent). Mit RESET_DEMO_DB=1 wird sie zuvor verworfen und
#      neu angelegt -> frischer Bestand.
#   3. Backend-Jar im Produktionsmodus bauen (-Pproduction: der einzige in dieser Sandbox
#      lizenzcheck-freie Build-Weg, siehe docs/kb/05-migration-plan.md "Phase 3 AP2")
#   4. Jar im Vordergrund starten (Profil demo) - Portal danach unter http://localhost:8080
#
# Login am Portal: admin/admin (Admin) bzw. anna|ben|clara|david|eva mit Passwort "demo".
#
# Requires: JDK 21, Maven, lokales PostgreSQL 16 (pg_ctlcluster) + sudo, wie vom
# SessionStart-Hook / Cloud-Init bereitgestellt. Siehe docs/kb/04-build-and-run.md, docs/kb/07-cloud-init.md.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PG_VER=16
DB_NAME="${DEMO_DB_NAME:-elwasys_demo}"
PORT="${SERVER_PORT:-8080}"

echo "[run-demo] repo root: $REPO_ROOT"

# 1. PostgreSQL-Cluster starten (ignorieren, falls bereits laeuft)
sudo pg_ctlcluster "$PG_VER" main start 2>/dev/null || true
for i in $(seq 1 30); do pg_isready -q && break; sleep 1; done
pg_isready || { echo "[run-demo] PostgreSQL not ready"; exit 1; }

# Verbindung als postgres-Superuser ueber TCP (gleiches Muster wie die uebrigen Harness-Skripte).
sudo -u postgres psql -q -c "ALTER USER postgres WITH PASSWORD 'postgres';"

# 2. Demo-Datenbank sicherstellen (optional neu erzeugen).
if [ "${RESET_DEMO_DB:-0}" = "1" ]; then
  echo "[run-demo] RESET_DEMO_DB=1 -> verwerfe und erzeuge Datenbank $DB_NAME neu"
  sudo -u postgres psql -q -c "DROP DATABASE IF EXISTS ${DB_NAME};"
fi
if ! sudo -u postgres psql -tAc "SELECT 1 FROM pg_database WHERE datname='${DB_NAME}';" | grep -q 1; then
  echo "[run-demo] lege Datenbank $DB_NAME an"
  sudo -u postgres psql -q -c "CREATE DATABASE ${DB_NAME};"
else
  echo "[run-demo] Datenbank $DB_NAME existiert bereits (Seeder ist idempotent)"
fi

# 3. Backend-Jar im Produktionsmodus bauen (ueber den Root-Reactor, loest die Parent-POM mit auf).
echo "[run-demo] baue Backend-Jar (-Pproduction)"
mvn -q -B -f "$REPO_ROOT/pom.xml" package -pl backend -Pproduction -DskipTests

# 4. Backend starten (Profil demo, Vordergrund).
export ELWASYS_DB_URL="jdbc:postgresql://localhost:5432/${DB_NAME}"
export ELWASYS_DB_USER="postgres"
export ELWASYS_DB_PASSWORD="postgres"
export SERVER_PORT="$PORT"

echo "[run-demo] starte Backend (Profil demo) auf :${PORT} - Portal: http://localhost:${PORT}"
echo "[run-demo] Login: admin/admin bzw. anna|ben|clara|david|eva mit Passwort 'demo'"
exec java -jar "$REPO_ROOT/backend/target/elwasys-backend.jar" --spring.profiles.active=demo
