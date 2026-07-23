#!/bin/bash
# Verifiziert den Rollback-/Rückbau-Pfad end-to-end (Phase 6 AP2, siehe
# docs/kb/05-migration-plan.md "Produktivumschaltung (Cutover)" und deploy/cutover/README.md,
# Abschnitt "Rollback"). Gegenstück zu verify-cutover-migration.sh (AP1), das den HINWEG
# verifiziert - dieses Skript verifiziert den RÜCKWEG (rollback-cutover.sh/.sql).
#
# Ablauf:
#   1. Sicherstellen, dass eine migrierte Bestands-DB vorliegt (elwasys_cutover_verify, per
#      CUTOVER_VERIFY_DB konfigurierbar - siehe verify-cutover-migration.sh). Fehlt sie oder
#      ist sie nicht bis V10 migriert, wird verify-cutover-migration.sh aufgerufen, um sie
#      herzustellen (dieselbe Vorbedingung, die AP1 bereits hinterlassen hat).
#   2. rollback-cutover.sh gegen diese DB ausführen (als Superuser "postgres" - siehe
#      rollback-cutover.sh-Header, V6-Umkehrung braucht CREATEROLE).
#   3. Asserts: (i) Alt-Schema wieder da, (ii) Geschäftsdaten unverändert erhalten.
#   4. Idempotenz: rollback-cutover.sh EIN ZWEITES MAL ausführen -> ohne Fehler, zentrale
#      Asserts erneut PASS.
#   5. Re-Cutover-Beweis: Backend-Jar erneut gegen dieselbe (jetzt zurückgebaute) DB starten -
#      Flyway baselined erneut auf V1 und wendet V2..V10 erneut an, genau wie beim allerersten
#      Cutover (siehe verify-cutover-migration.sh). Beweist: die DB ist nach dem Rollback
#      wieder sauber cutover-fähig, kein "kaputter Zwischenzustand".
#   6. Aufräumen: Backend-Prozess beenden (trap - läuft auch bei Fehlschlag).
#
# Hinweis (cluster-weite Rollen, siehe V6__harden_db_roles.sql-Header und
# rollback-cutover.sql): dieses Skript legt in Schritt 2 die Alt-Rollen elwaclient1/elwaapi
# CLUSTERWEIT wieder an. Das ist im geteilten Test-Cluster dieses Projekts harmlos - der
# nächste Lauf von backend/run-backend-tests.sh (dessen Test-DB ebenfalls V6 durchläuft)
# droppt sie beim Migrieren seiner eigenen Test-DB wieder idempotent (siehe dessen
# Regressionscheck unten in der Empfehlung des Arbeitspakets). Keine fremden Fixtures, keine
# hängenden java-Prozesse (trap).
#
# Konfigurierbar:
#   CUTOVER_VERIFY_DB     Name der Bestands-DB (Default: elwasys_cutover_verify, wie AP1)
#   ROLLBACK_VERIFY_PORT  Port des Backends während des Re-Cutover-Beweises (Default: 18091 -
#                         bewusst NICHT 18090/CUTOVER_VERIFY_PORT, um Kollisionen bei
#                         versehentlich parallelem Lauf beider Verify-Skripte zu vermeiden)
#
# Requires: JDK 21, Maven, lokales PostgreSQL 16 (pg_ctlcluster) + sudo - wie
# verify-cutover-migration.sh.
set -euo pipefail

cd "$(dirname "$0")/../.."   # Repo-Wurzel (deploy/cutover/.. = deploy, ../.. = Repo-Wurzel)

CUTOVER_VERIFY_DB="${CUTOVER_VERIFY_DB:-elwasys_cutover_verify}"
ROLLBACK_VERIFY_PORT="${ROLLBACK_VERIFY_PORT:-18091}"

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

PSQL() { sudo -u postgres psql -tA -d "${CUTOVER_VERIFY_DB}" -c "$1"; }
table_exists() {
  PSQL "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='$1');"
}
col_exists() {
  PSQL "SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='$1' AND column_name='$2');"
}
trigger_exists() {
  PSQL "SELECT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='$1' AND NOT tgisinternal);"
}

echo
echo "== 1) migrierte Bestands-DB '${CUTOVER_VERIFY_DB}' sicherstellen =="
DB_READY="false"
DB_EXISTS="$(sudo -u postgres psql -tA -c "SELECT 1 FROM pg_database WHERE datname = '${CUTOVER_VERIFY_DB}';")"
if [[ "${DB_EXISTS}" == "1" ]]; then
  MIGRATED_TO_V10="$(PSQL "SELECT version || '|' || success FROM flyway_schema_history WHERE version = '10';" 2>/dev/null || true)"
  if [[ "${MIGRATED_TO_V10}" == "10|true" ]]; then
    DB_READY="true"
  fi
fi
if [[ "${DB_READY}" == "true" ]]; then
  echo "  '${CUTOVER_VERIFY_DB}' existiert bereits und ist bis V10 migriert - wiederverwendet."
else
  echo "  '${CUTOVER_VERIFY_DB}' fehlt oder ist nicht bis V10 migriert - stelle sie über"
  echo "  verify-cutover-migration.sh her ..."
  CUTOVER_VERIFY_DB="${CUTOVER_VERIFY_DB}" deploy/cutover/verify-cutover-migration.sh
fi

echo
echo "== 2) rollback-cutover.sh ausführen (1. Lauf) =="
ELWASYS_DB_URL="jdbc:postgresql://localhost:5432/${CUTOVER_VERIFY_DB}" \
ELWASYS_DB_USER="postgres" \
ELWASYS_DB_PASSWORD="postgres" \
deploy/cutover/rollback-cutover.sh

echo
echo "== 3) Asserts nach dem 1. Rollback-Lauf =="

run_core_asserts() {
  echo "--- (i) Alt-Schema wieder da ---"
  assert_eq "users.auth_key existiert wieder (V10-Umkehrung)" "t" "$(col_exists users auth_key)"
  assert_eq "users.app_id existiert wieder (V10-Umkehrung)" "t" "$(col_exists users app_id)"
  assert_eq "users.access_key existiert wieder (V10-Umkehrung)" "t" "$(col_exists users access_key)"
  assert_eq "Trigger user_authkey_trigger existiert wieder (V10-Umkehrung)" "t" "$(trigger_exists user_authkey_trigger)"
  assert_eq "Tabelle reservations existiert wieder (V10-Umkehrung)" "t" "$(table_exists reservations)"
  assert_eq "Tabelle foreign_authkeys existiert wieder (V10-Umkehrung)" "t" "$(table_exists foreign_authkeys)"
  assert_eq "locations.client_ip existiert wieder (V9-Umkehrung)" "t" "$(col_exists locations client_ip)"
  assert_eq "locations.client_uid existiert wieder (V9-Umkehrung)" "t" "$(col_exists locations client_uid)"
  assert_eq "locations.client_port existiert wieder (V9-Umkehrung)" "t" "$(col_exists locations client_port)"
  assert_eq "locations.client_last_seen existiert wieder (V9-Umkehrung)" "t" "$(col_exists locations client_last_seen)"
  assert_eq "devices.auto_end_power_threashold (alter Tippfehler-Name) existiert wieder (V8-Umkehrung)" \
      "t" "$(col_exists devices auto_end_power_threashold)"
  assert_eq "devices.auto_end_power_threshold (neuer Name) existiert NICHT MEHR (V8-Umkehrung)" \
      "f" "$(col_exists devices auto_end_power_threshold)"
  assert_eq "config authkey.prefix existiert wieder (V10-Umkehrung)" \
      "1" "$(PSQL "SELECT COUNT(*) FROM config WHERE key = 'authkey.prefix';")"
  assert_eq "config reservation.duration = 900 wieder da (V10-Umkehrung)" \
      "900" "$(PSQL "SELECT value FROM config WHERE key = 'reservation.duration';")"
  assert_eq "admin-Passwort wieder auf Alt-Default-Hash (V7-Umkehrung, war NULL)" \
      "d033e22ae348aeb5660fc2140aec35850c4da997" "$(PSQL "SELECT password FROM users WHERE username = 'admin';")"
  assert_eq "flyway_schema_history existiert NICHT MEHR" "f" "$(table_exists flyway_schema_history)"
  assert_eq "terminal_tokens existiert NICHT MEHR (V3-Umkehrung)" "f" "$(table_exists terminal_tokens)"
  assert_eq "terminal_idempotency_keys existiert NICHT MEHR (V4-Umkehrung)" "f" "$(table_exists terminal_idempotency_keys)"
  assert_eq "locations.offline_max_duration_minutes existiert NICHT MEHR (V5-Umkehrung)" \
      "f" "$(col_exists locations offline_max_duration_minutes)"
  assert_eq "elwaclient1-Rolle existiert wieder (V6-Umkehrung)" \
      "t" "$(PSQL "SELECT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'elwaclient1');")"
  assert_eq "elwaapi-Rolle existiert wieder (V6-Umkehrung)" \
      "t" "$(PSQL "SELECT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'elwaapi');")"

  echo "--- (ii) Geschäftsdaten unverändert erhalten (dieselben Bestandsdaten wie in"
  echo "    verify-cutover-migration.sh eingefügt) ---"
  # Hinweis (wie in verify-cutover-migration.sh): boolean || text konkateniert zu den
  # SQL-Standardwörtern "true"/"false", NICHT zu psqls rohem t/f-Ausgabeformat.
  assert_eq "user_groups: WG Musterstrasse mit FACTOR/0.9 erhalten" \
      "WG Musterstrasse|FACTOR|0.9" \
      "$(PSQL "SELECT name || '|' || discount_type || '|' || discount_value FROM user_groups WHERE name = 'WG Musterstrasse';")"
  assert_eq "users: mmustermann (Nicht-Admin) erhalten" \
      "Max Mustermann|mmustermann|false|1234567890" \
      "$(PSQL "SELECT name || '|' || username || '|' || is_admin || '|' || card_ids FROM users WHERE username = 'mmustermann';")"
  assert_eq "locations: Kellerwaschkueche erhalten" \
      "Kellerwaschkueche" \
      "$(PSQL "SELECT name FROM locations WHERE name = 'Kellerwaschkueche';")"
  assert_eq "devices: Waschmaschine 1 erhalten (inkl. zurückbenannter Spalte auto_end_power_threashold, Default 0.5)" \
      "Waschmaschine 1|wm1|0.5" \
      "$(PSQL "SELECT name || '|' || fhem_name || '|' || auto_end_power_threashold FROM devices WHERE name = 'Waschmaschine 1';")"
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
}

run_core_asserts

echo
echo "== 4) Idempotenz: rollback-cutover.sh EIN ZWEITES MAL ausführen =="
ELWASYS_DB_URL="jdbc:postgresql://localhost:5432/${CUTOVER_VERIFY_DB}" \
ELWASYS_DB_USER="postgres" \
ELWASYS_DB_PASSWORD="postgres" \
deploy/cutover/rollback-cutover.sh
echo "  zweiter Lauf ohne Fehler durchgelaufen - Zustand darf sich nicht verändert haben:"
run_core_asserts

echo
echo "== 5) Re-Cutover-Beweis: Backend erneut gegen die (jetzt zurückgebaute) DB starten =="
echo "   (Backend-Jar bauen - Produktionsmodus wie in verify-cutover-migration.sh)"
mvn -q -B -f pom.xml package -pl backend -Pproduction -DskipTests

ELWASYS_DB_URL="jdbc:postgresql://localhost:5432/${CUTOVER_VERIFY_DB}" \
ELWASYS_DB_USER="postgres" \
ELWASYS_DB_PASSWORD="postgres" \
java -jar backend/target/elwasys-backend.jar --server.port="${ROLLBACK_VERIFY_PORT}" \
    > /tmp/verify-rollback-backend.log 2>&1 &
BACKEND_PID=$!

HEALTH=""
for i in $(seq 1 60); do
  # Liveness-Gruppe (nur Prozess-Status): das Root-/actuator/health steht auf 503, weil die DB
  # Standorte/Geraete enthaelt, aber kein Terminal verbunden ist (AP6 #32).
  if curl -sf "http://localhost:${ROLLBACK_VERIFY_PORT}/actuator/health/liveness" > /dev/null 2>&1; then
    HEALTH="$(curl -s "http://localhost:${ROLLBACK_VERIFY_PORT}/actuator/health/liveness")"
    break
  fi
  sleep 1
done
echo "  Backend health: ${HEALTH}"
if [[ "${HEALTH}" != *'"status":"UP"'* ]]; then
  echo "FAIL: Backend kam nach dem Rollback nicht erneut UP (Log: /tmp/verify-rollback-backend.log)"
  tail -n 60 /tmp/verify-rollback-backend.log || true
  exit 1
fi

echo "  Backend UP - stoppe es wieder, der Flyway-Assert läuft direkt per psql gegen die DB."
kill "${BACKEND_PID}" 2>/dev/null || true
wait "${BACKEND_PID}" 2>/dev/null || true
BACKEND_PID=""

echo "--- Re-Cutover: Flyway-Historie erneut exakt BASELINE@1 + V2..V10 success ---"
EXPECTED_HISTORY="1|BASELINE|true
2|SQL|true
3|SQL|true
4|SQL|true
5|SQL|true
6|SQL|true
7|SQL|true
8|SQL|true
9|SQL|true
10|SQL|true"
ACTUAL_HISTORY="$(PSQL "SELECT version || '|' || type || '|' || success FROM flyway_schema_history ORDER BY installed_rank;")"
assert_eq "Re-Cutover: flyway_schema_history erneut genau BASELINE@1 gefolgt von V2..V10, alle success=true" \
    "${EXPECTED_HISTORY}" "${ACTUAL_HISTORY}"
assert_eq "Re-Cutover: devices.auto_end_power_threshold (neuer Name) wieder da (V8 erneut angewendet)" \
    "t" "$(col_exists devices auto_end_power_threshold)"
assert_eq "Re-Cutover: users.auth_key wieder weg (V10 erneut angewendet)" \
    "f" "$(col_exists users auth_key)"
assert_eq "Re-Cutover: Geschäftsdaten weiterhin erhalten (mmustermann)" \
    "mmustermann" "$(PSQL "SELECT username FROM users WHERE username = 'mmustermann';")"

echo
echo "================================================================"
if [[ "${FAIL_COUNT}" == "0" ]]; then
  echo "ALLE ASSERTS PASS (1. Rollback-Lauf, Idempotenz-Wiederholung, Re-Cutover-Beweis)."
  echo "================================================================"
  exit 0
else
  echo "${FAIL_COUNT} ASSERT(S) FEHLGESCHLAGEN - siehe FAIL-Zeilen oben."
  echo "================================================================"
  exit 1
fi
