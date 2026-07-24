#!/bin/bash
# Verifiziert den eigentlichen Cutover-Migrationspfad end-to-end, gegen eine lokale Kopie des
# Bestandsschemas (Phase 6 AP1, siehe docs/kb/05-migration-plan.md "Produktivumschaltung"). Dies ist
# das wartbare Cutover-Verifikationsskript: es prüft nicht Schema-Gleichheit gegen ein frisches
# Flyway-Schema (diese Prämisse gilt seit V2 nicht mehr), sondern die tatsächlich für den
# Cutover relevanten Eigenschaften explizit per Assert-Liste (siehe unten).
#
# Ablauf:
#   1. Test-DB als Kopie des Bestandsschemas anlegen (per Flyway-V1-Baseline, siehe Schritt 1
#      unten - V1__baseline_schema_0_4_0.sql ist die einzige Quelle des Alt-Schemas, seit das
#      frühere, byte-äquivalente database/database-init.sql entfernt wurde; die Test-DB wird
#      selbst vorher angelegt, eigener Name/Port konfigurierbar).
#   2. VOR der Migration realistische Bestandsdaten einfügen (je eine Zeile: user_group,
#      Nicht-Admin-Nutzer, Standort, Gerät, Programm, Geräte-Programm-Zuordnung, Ausführung,
#      Abrechnungsposten) - Beweis für Datenerhalt beim Cutover.
#   3. Backend-Jar gegen diese Alt-Weg-DB starten (Flyway baselined automatisch auf V1 und
#      wendet V2..V<neueste> an, siehe application.yml "baseline-on-migrate"), warten bis
#      /actuator/health/liveness UP (Prozess-Health; Root-Health steht ohne Terminal auf 503,
#      AP6 #32), dann sauber stoppen (trap - kein hängender Prozess, auch bei Fehlschlag).
#   4. Asserts per psql: Flyway-Historie (BASELINE@1 + V2..V<neueste> je success=true, die
#      erwartete Liste wird aus dem Migrationsordner ABGELEITET - siehe derive_expected_history,
#      damit sie nicht bei neuen Migrationen still veraltet, H6/#85), die vor der Migration
#      eingefügten Bestandsdaten UNVERÄNDERT, Schema-Härtung wirksam (siehe einzelne Asserts
#      unten für die vollständige Liste).
#   5. Aufräumen: Backend-Prozess beenden (trap). Die Test-DB selbst wird NICHT gedroppt -
#      ein künftiges Rollback-Arbeitspaket (Phase 6 AP2) kann auf ihr aufbauen.
#
# Konfigurierbar (kollidiert bewusst nicht mit anderen Test-DBs/-Ports im Repo, siehe deren
# Skripte: elwasys_backend_it/run-backend-tests.sh, elwasys_backend_e2e+Port 8081/backend/e2e):
#   CUTOVER_VERIFY_DB   Name der Test-DB (Default: elwasys_cutover_verify)
#   CUTOVER_VERIFY_PORT Port des Backends während der Verifikation (Default: 18090)
#
# Requires: JDK 21, Maven, lokales PostgreSQL 16 (pg_ctlcluster) + sudo - wie
# backend/run-backend-tests.sh. Reihenfolge-unabhängig wiederholt ausführbar (Test-DB wird bei
# jedem Lauf per DROP+CREATE frisch angelegt).
set -euo pipefail

cd "$(dirname "$0")/../.."   # Repo-Wurzel (deploy/cutover/.. = deploy, ../.. = Repo-Wurzel)

CUTOVER_VERIFY_DB="${CUTOVER_VERIFY_DB:-elwasys_cutover_verify}"
CUTOVER_VERIFY_PORT="${CUTOVER_VERIFY_PORT:-18090}"

MIGRATION_DIR="backend/src/main/resources/db/migration"

# Leitet die ERWARTETE Flyway-Historie aus dem Migrationsordner ab (H6/#85). Vorher war die
# Liste handgepflegt (»V2..V10«) und veraltete still, als V11 hinzukam - genau dieses Skript
# soll aber vor dem Feldeinsatz real laufen. Jetzt gilt: V1 ist die Baseline (BASELINE@1 durch
# baseline-on-migrate, V1 selbst wird NICHT angewendet), V2..V<neueste> je eine erfolgreich
# angewendete SQL-Migration. Neue Migrationen ziehen das Gate damit automatisch nach. Nur
# versionierte Migrationen (V<n>__) zählen; repeatable (R__) haben bewusst keine Historie-Zeile.
derive_expected_history() {
  local versions
  versions="$(ls "${MIGRATION_DIR}"/V*__*.sql 2>/dev/null \
    | sed -E 's#.*/V([0-9]+)__.*#\1#' | sort -n)"
  if [[ -z "${versions}" ]]; then
    echo "FEHLER: keine Flyway-Migrationen (V<n>__*.sql) in ${MIGRATION_DIR} gefunden." >&2
    return 1
  fi
  local v
  for v in ${versions}; do
    if [[ "${v}" == "1" ]]; then
      echo "1|BASELINE|true"
    else
      echo "${v}|SQL|true"
    fi
  done
}

# Offline-Selbsttest-Haken (kein PostgreSQL/Maven nötig): gibt nur die abgeleitete Erwartung aus
# und beendet sich. Der CI-Selftest (siehe verify-cutover-migration-selftest.sh) nutzt das, um
# Skript ↔ Migrationsordner abzugleichen, ohne den vollen (schweren) Verifikationslauf.
if [[ "${1:-}" == "--print-expected-history" ]]; then
  derive_expected_history
  exit 0
fi

FAIL_COUNT=0
assert_eq() {
  local desc="$1" expected="$2" actual="$3"
  if [[ "${actual}" == "${expected}" ]]; then
    echo "PASS: ${desc}"
  else
    echo "FAIL: ${desc}"
    echo "      erwartet: '${expected}'"
    echo "      erhalten: '${actual}'"
    FAIL_COUNT=$((FAIL_COUNT + 1))
  fi
}

BACKEND_PID=""
cleanup() {
  if [[ -n "${BACKEND_PID}" ]] && kill -0 "${BACKEND_PID}" 2>/dev/null; then
    echo "[cleanup] stoppe Backend (PID ${BACKEND_PID}) ..."
    kill "${BACKEND_PID}" 2>/dev/null || true
    wait "${BACKEND_PID}" 2>/dev/null || true
  fi
}
trap cleanup EXIT

echo "== 0) PostgreSQL sicherstellen =="
sudo pg_ctlcluster 16 main start 2>/dev/null || true
for i in $(seq 1 30); do pg_isready -q && break; sleep 1; done
pg_isready
sudo -u postgres psql -q -c "ALTER USER postgres WITH PASSWORD 'postgres';"

echo "== 1) Test-DB als Kopie des Bestandsschemas -> ${CUTOVER_VERIFY_DB} =="
sudo -u postgres psql -q -c "DROP DATABASE IF EXISTS ${CUTOVER_VERIFY_DB};"
sudo -u postgres psql -q -c "CREATE DATABASE ${CUTOVER_VERIFY_DB};"
# Das Bestandsschema 0.4.0 wird über die Flyway-V1-Baseline eingespielt - die EINZIGE Quelle
# des Alt-Schemas (das frühere, byte-äquivalente database/database-init.sql wurde entfernt).
# V1 hat bewusst keine "CREATE DATABASE"/"\connect"-Präambel (Flyway migriert eine bereits
# gewählte DB), daher spielen wir es direkt gegen die oben angelegte Test-DB ein - genau so
# entsteht eine 0.4.0-DB OHNE flyway_schema_history, d.h. eine echte pre-Flyway-Produktiv-DB,
# gegen die dann baseline-on-migrate getestet wird.
#
# NOTE: In diesem geteilten Test-Cluster (dieselbe Postgres-Instanz wie
# backend/run-backend-tests.sh, backend/e2e/, Client-Raspi/run-*-e2e.sh) sind die Rollen
# elwaclient1/elwaportal/elwaapi typischerweise schon aus einem früheren Lauf vorhanden -
# Rollen sind clusterweit, nicht pro Datenbank (siehe V1-Header). V1 legt sie idempotent an
# (IF NOT EXISTS), daher laufen alle nachfolgenden Schema-/Daten-/Grant-Statements normal
# durch. Eine echte Produktiv-DB hat nur EINEN Cluster für sich.
sudo -u postgres psql -q -d "${CUTOVER_VERIFY_DB}" < backend/src/main/resources/db/migration/V1__baseline_schema_0_4_0.sql

echo "== 2) Realistische Bestandsdaten VOR der Migration einfügen (Beweis für Datenerhalt) =="
sudo -u postgres psql -v ON_ERROR_STOP=1 -q -d "${CUTOVER_VERIFY_DB}" <<'SQL'
INSERT INTO user_groups (name, discount_type, discount_value)
    VALUES ('WG Musterstrasse', 'FACTOR', 0.9);
INSERT INTO users (name, username, password, card_ids, is_admin, group_id)
    VALUES ('Max Mustermann', 'mmustermann', 'd033e22ae348aeb5660fc2140aec35850c4da997',
            '1234567890', FALSE, (SELECT id FROM user_groups WHERE name = 'WG Musterstrasse'));
INSERT INTO locations (name) VALUES ('Kellerwaschkueche');
INSERT INTO devices (name, position, location_id, fhem_name)
    VALUES ('Waschmaschine 1', 1, (SELECT id FROM locations WHERE name = 'Kellerwaschkueche'),
            'wm1');
INSERT INTO programs (name, type, max_duration, flagfall, rate, time_unit)
    VALUES ('60 Grad Buntwaesche', 'FIXED', 5400, 1.5, NULL, NULL);
INSERT INTO device_program_rel (device_id, program_id)
    VALUES ((SELECT id FROM devices WHERE name = 'Waschmaschine 1'),
            (SELECT id FROM programs WHERE name = '60 Grad Buntwaesche'));
INSERT INTO executions (device_id, program_id, user_id, start, stop, finished)
    VALUES ((SELECT id FROM devices WHERE name = 'Waschmaschine 1'),
            (SELECT id FROM programs WHERE name = '60 Grad Buntwaesche'),
            (SELECT id FROM users WHERE username = 'mmustermann'),
            '2026-01-10 09:00:00', '2026-01-10 10:30:00', TRUE);
INSERT INTO credit_accounting (user_id, execution_id, amount, description)
    VALUES ((SELECT id FROM users WHERE username = 'mmustermann'),
            (SELECT id FROM executions ORDER BY id DESC LIMIT 1),
            -1.5, 'Waschmaschine 1 - 60 Grad Buntwaesche');
SQL
echo "  Bestandsdaten eingefügt."

echo "== 3) Backend-Jar bauen (Produktionsmodus - siehe docs/kb/04-build-and-run.md, laenger laufender Prozess braucht -Pproduction) =="
mvn -q -B -f pom.xml package -pl backend -Pproduction -DskipTests

echo "== 4) Backend gegen die Alt-Weg-DB starten (Flyway migriert automatisch: BASELINE@1, dann V2..V<neueste>) =="
ELWASYS_DB_URL="jdbc:postgresql://localhost:5432/${CUTOVER_VERIFY_DB}" \
ELWASYS_DB_USER="postgres" \
ELWASYS_DB_PASSWORD="postgres" \
java -jar backend/target/elwasys-backend.jar --server.port="${CUTOVER_VERIFY_PORT}" \
    > /tmp/verify-cutover-backend.log 2>&1 &
BACKEND_PID=$!

HEALTH=""
for i in $(seq 1 60); do
  # Liveness-Gruppe (nur Prozess-Status): das Root-/actuator/health steht hier auf 503, weil die
  # migrierte Bestands-DB Standorte/Geraete enthaelt, aber kein Terminal verbunden ist (AP6 #32).
  if curl -sf "http://localhost:${CUTOVER_VERIFY_PORT}/actuator/health/liveness" > /dev/null 2>&1; then
    HEALTH="$(curl -s "http://localhost:${CUTOVER_VERIFY_PORT}/actuator/health/liveness")"
    break
  fi
  sleep 1
done
echo "  Backend health: ${HEALTH}"
if [[ "${HEALTH}" != *'"status":"UP"'* ]]; then
  echo "FAIL: Backend gegen die Alt-Weg-DB kam nicht UP (Log: /tmp/verify-cutover-backend.log)"
  tail -n 60 /tmp/verify-cutover-backend.log || true
  exit 1
fi

echo "  Backend UP - stoppe es wieder, die restlichen Asserts laufen direkt per psql gegen die DB."
kill "${BACKEND_PID}" 2>/dev/null || true
wait "${BACKEND_PID}" 2>/dev/null || true
BACKEND_PID=""

echo
echo "== 5) Asserts =="

PSQL() { sudo -u postgres psql -tA -d "${CUTOVER_VERIFY_DB}" -c "$1"; }
col_exists() {
  PSQL "SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='$1' AND column_name='$2');"
}
table_exists() {
  PSQL "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='$1');"
}

echo "--- Flyway-Historie ---"
# Aus dem Migrationsordner abgeleitet (H6/#85, siehe derive_expected_history oben) statt hart
# codiert - so bleibt das Gate bei neuen Migrationen automatisch aktuell.
EXPECTED_HISTORY="$(derive_expected_history)"
ACTUAL_HISTORY="$(PSQL "SELECT version || '|' || type || '|' || success FROM flyway_schema_history ORDER BY installed_rank;")"
assert_eq "flyway_schema_history: genau BASELINE@1 gefolgt von V2..V<neueste>, alle success=true" \
    "${EXPECTED_HISTORY}" "${ACTUAL_HISTORY}"

echo "--- Bestandsdaten unverändert (Datenerhalt) ---"
# Hinweis: boolean || text konkateniert zu den
# SQL-Standardwörtern "true"/"false" - NICHT zu psql's rohem 't'/'f'-Ausgabeformat (das sieht
# man nur bei einer nackten "SELECT bool_spalte" ohne Konkatenation, siehe col_exists() unten).
assert_eq "user_groups: WG Musterstrasse mit FACTOR/0.9 erhalten" \
    "WG Musterstrasse|FACTOR|0.9" \
    "$(PSQL "SELECT name || '|' || discount_type || '|' || discount_value FROM user_groups WHERE name = 'WG Musterstrasse';")"
assert_eq "users: mmustermann (Nicht-Admin) erhalten" \
    "Max Mustermann|mmustermann|false|1234567890" \
    "$(PSQL "SELECT name || '|' || username || '|' || is_admin || '|' || card_ids FROM users WHERE username = 'mmustermann';")"
assert_eq "locations: Kellerwaschkueche erhalten" \
    "Kellerwaschkueche" \
    "$(PSQL "SELECT name FROM locations WHERE name = 'Kellerwaschkueche';")"
assert_eq "devices: Waschmaschine 1 erhalten (inkl. umbenannter Spalte auto_end_power_threshold, Default 0.5)" \
    "Waschmaschine 1|wm1|0.5" \
    "$(PSQL "SELECT name || '|' || fhem_name || '|' || auto_end_power_threshold FROM devices WHERE name = 'Waschmaschine 1';")"
assert_eq "programs: 60 Grad Buntwaesche erhalten" \
    "60 Grad Buntwaesche|FIXED|5400|1.5" \
    "$(PSQL "SELECT name || '|' || type || '|' || max_duration || '|' || flagfall FROM programs WHERE name = '60 Grad Buntwaesche';")"
assert_eq "device_program_rel: Zuordnung erhalten" \
    "1" \
    "$(PSQL "SELECT COUNT(*) FROM device_program_rel dpr JOIN devices d ON d.id = dpr.device_id JOIN programs p ON p.id = dpr.program_id WHERE d.name = 'Waschmaschine 1' AND p.name = '60 Grad Buntwaesche';")"
assert_eq "executions: Ausführung erhalten (Start/Ende/finished)" \
    "2026-01-10 09:00:00|2026-01-10 10:30:00|true" \
    "$(PSQL "SELECT start || '|' || stop || '|' || finished FROM executions e JOIN users u ON u.id = e.user_id WHERE u.username = 'mmustermann';")"
assert_eq "credit_accounting: Abrechnungsposten erhalten" \
    "-1.5|Waschmaschine 1 - 60 Grad Buntwaesche" \
    "$(PSQL "SELECT amount || '|' || description FROM credit_accounting ca JOIN users u ON u.id = ca.user_id WHERE u.username = 'mmustermann';")"

echo "--- Schema-Härtung wirksam ---"
assert_eq "devices.auto_end_power_threshold existiert (V8: Typo-Fix)" "t" "$(col_exists devices auto_end_power_threshold)"
assert_eq "devices.auto_end_power_threashold existiert NICHT MEHR (V8: Typo-Fix)" "f" "$(col_exists devices auto_end_power_threashold)"
assert_eq "locations.client_ip existiert NICHT MEHR (V9)" "f" "$(col_exists locations client_ip)"
assert_eq "locations.client_uid existiert NICHT MEHR (V9)" "f" "$(col_exists locations client_uid)"
assert_eq "locations.client_port existiert NICHT MEHR (V9)" "f" "$(col_exists locations client_port)"
assert_eq "locations.client_last_seen existiert NICHT MEHR (V9)" "f" "$(col_exists locations client_last_seen)"
assert_eq "reservations existiert NICHT MEHR (V10)" "f" "$(table_exists reservations)"
assert_eq "foreign_authkeys existiert NICHT MEHR (V10)" "f" "$(table_exists foreign_authkeys)"
assert_eq "users.auth_key existiert NICHT MEHR (V10)" "f" "$(col_exists users auth_key)"
assert_eq "users.app_id existiert NICHT MEHR (V10)" "f" "$(col_exists users app_id)"
assert_eq "users.access_key existiert NICHT MEHR (V10)" "f" "$(col_exists users access_key)"
assert_eq "admin-Passwort ist NULL (V7: Default-Admin-Passwort entfernt)" \
    "" "$(PSQL "SELECT password FROM users WHERE username = 'admin';")"
# Rollen selbst sind clusterweit (siehe V6-Header) - kein Fehlschlag, falls elwaclient1/elwaapi
# noch aus einer ANDEREN Test-DB desselben Clusters existieren. Was für DIESE DB tatsächlich
# zählt, sind die Grants/Privilegien in dieser DB - die müssen weg sein.
assert_eq "keine Tabellen-Privilegien mehr für elwaclient1/elwaapi in dieser DB (V6, Rollen selbst nur informativ, da clusterweit)" \
    "0" "$(PSQL "SELECT COUNT(*) FROM information_schema.table_privileges WHERE grantee IN ('elwaclient1', 'elwaapi');")"

echo
echo "================================================================"
if [[ "${FAIL_COUNT}" == "0" ]]; then
  echo "ALLE ASSERTS PASS. Test-DB '${CUTOVER_VERIFY_DB}' bleibt bestehen (nicht gedroppt) -"
  echo "Grundlage für das Rollback-Arbeitspaket (Phase 6 AP2)."
  echo "================================================================"
  exit 0
else
  echo "${FAIL_COUNT} ASSERT(S) FEHLGESCHLAGEN - siehe FAIL-Zeilen oben."
  echo "================================================================"
  exit 1
fi
