#!/bin/bash
# Cutover-Preflight-Check (Phase 6 AP1, siehe kb/05-migration-plan.md "Produktivumschaltung").
#
# REIN LESEND - dieses Skript verändert NICHTS an der Datenbank (nur SELECT-Abfragen). Es
# erstellt einen Readiness-Report für den Cutover der Bestands-DB (angelegt über den Alt-Weg
# database/database-init.sql) auf das neue Flyway-verwaltete Schema: welche Alt-
# Artefakte sind noch da, ein Dateninventar, und Warnungen für alles, was vor dem eigentlichen
# Cutover (Backend gegen diese DB starten, siehe deploy/cutover/README.md) Aufmerksamkeit
# braucht.
#
# Verbindung per Umgebungsvariablen (dieselben wie das Backend selbst kennt, siehe
# application.yml) - siehe lib-db-env.sh für Details/Defaults:
#   ELWASYS_DB_URL, ELWASYS_DB_USER, ELWASYS_DB_PASSWORD
#
# Usage:
#   ELWASYS_DB_URL=jdbc:postgresql://prod-db:5432/elwasys \
#   ELWASYS_DB_USER=elwaportal ELWASYS_DB_PASSWORD=<...> \
#   deploy/cutover/01-preflight-check.sh
set -euo pipefail

cd "$(dirname "$0")"
# shellcheck source=./lib-db-env.sh
source ./lib-db-env.sh

WARNINGS=0

warn() {
  echo "  WARNUNG: $*"
  WARNINGS=$((WARNINGS + 1))
}

# tA-Query mit genau einer Zeile/Spalte -> Wert direkt zurückgeben (kein Trailing-Newline-Ärger).
q1() {
  psql_cutover -tA -c "$1" | head -n1
}

table_exists() {
  local t="$1"
  [[ "$(q1 "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='${t}');")" == "t" ]]
}

column_exists() {
  local t="$1" c="$2"
  [[ "$(q1 "SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='${t}' AND column_name='${c}');")" == "t" ]]
}

echo "================================================================"
echo "elwasys Cutover-Preflight-Check"
echo "  DB: ${CUTOVER_DB_HOST}:${CUTOVER_DB_PORT}/${CUTOVER_DB_NAME} (User: ${ELWASYS_DB_USER})"
echo "================================================================"

if ! psql_cutover -tA -c "SELECT 1;" > /dev/null 2>&1; then
  echo "FEHLER: Verbindung zur Datenbank fehlgeschlagen. ELWASYS_DB_URL/_USER/_PASSWORD prüfen." >&2
  exit 1
fi

echo
echo "--- 1) Flyway-Status ---"
if table_exists "flyway_schema_history"; then
  ROWS="$(q1 "SELECT COUNT(*) FROM flyway_schema_history;")"
  LATEST="$(q1 "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;")"
  FAILED="$(q1 "SELECT COUNT(*) FROM flyway_schema_history WHERE success = false;")"
  echo "  flyway_schema_history existiert bereits: ${ROWS} Einträge, letzte angewendete Version: ${LATEST}."
  echo "  (Diese DB wurde also schon mindestens einmal gegen das neue Backend migriert.)"
  if [[ "${FAILED}" != "0" ]]; then
    warn "${FAILED} fehlgeschlagene Migrations-Einträge (success=false) - vor dem Cutover klären, nicht einfach erneut starten!"
  fi
  if [[ "${LATEST}" != "10" ]]; then
    warn "Letzte angewendete Version ist ${LATEST}, nicht 10 (aktueller Stand laut backend/src/main/resources/db/migration) - Backend-Version prüfen bzw. Migration nachholen."
  fi
else
  echo "  flyway_schema_history existiert NOCH NICHT - das ist der erwartete Zustand einer"
  echo "  über database-init.sql angelegten Bestands-DB vor dem ersten Start des neuen"
  echo "  Backends. Beim ersten Start baselined Flyway diese DB auf Version 1 und wendet"
  echo "  danach V2..V10 automatisch an (baseline-on-migrate, siehe application.yml)."
fi

echo
echo "--- 2) Noch vorhandene Alt-Artefakte (werden von V6/V8/V9/V10 bereinigt) ---"
ALT_FOUND=0
for col in client_uid client_ip client_port client_last_seen; do
  if column_exists "locations" "${col}"; then
    echo "  vorhanden: locations.${col} (V9 entfernt diese Spalte)"
    ALT_FOUND=1
  fi
done
for col in app_id access_key auth_key; do
  if column_exists "users" "${col}"; then
    echo "  vorhanden: users.${col} (V10 entfernt diese Spalte)"
    ALT_FOUND=1
  fi
done
if column_exists "devices" "auto_end_power_threashold"; then
  echo "  vorhanden: devices.auto_end_power_threashold (Tippfehler-Spalte, V8 benennt sie in auto_end_power_threshold um)"
  ALT_FOUND=1
fi
if table_exists "reservations"; then
  echo "  vorhanden: Tabelle reservations (V10 entfernt sie)"
  ALT_FOUND=1
fi
if table_exists "foreign_authkeys"; then
  echo "  vorhanden: Tabelle foreign_authkeys (V10 entfernt sie)"
  ALT_FOUND=1
fi
ALT_ROLES="$(q1 "SELECT string_agg(rolname, ', ') FROM pg_catalog.pg_roles WHERE rolname IN ('elwaclient1', 'elwaapi', 'elwaclients');")"
if [[ -n "${ALT_ROLES}" ]]; then
  echo "  vorhanden (clusterweit, nicht pro DB - nur informativ): Rollen/Gruppe ${ALT_ROLES} (V6 entfernt sie)"
  ALT_FOUND=1
fi
if [[ "${ALT_FOUND}" == "0" ]]; then
  echo "  Keine Alt-Artefakte gefunden - Schema ist bereits (oder schon) auf dem gehärteten Stand."
else
  echo "  -> Diese Artefakte sind harmlos: sie werden automatisch beim ersten Start des neuen"
  echo "     Backends gegen diese DB per Flyway (V6/V8/V9/V10) entfernt. Nur zur Information,"
  echo "     keine Handlung nötig - außer bei den Rollen ggf. Zugangsdaten/Firewalls prüfen,"
  echo "     die noch auf elwaclient1/elwaapi setzen (sollten seit Phase 4 nicht mehr existieren)."
fi

echo
echo "--- 3) Dateninventar ---"
USER_COUNT="$(q1 "SELECT COUNT(*) FROM users WHERE deleted = false;")"
ADMIN_ROW="$(psql_cutover -tA -F'|' -c "SELECT username, is_admin, (password IS NOT NULL) FROM users WHERE username = 'admin';")"
echo "  users (nicht gelöscht): ${USER_COUNT}"
if [[ -z "${ADMIN_ROW}" ]]; then
  warn "kein Benutzer 'admin' gefunden - vor dem Cutover klären, wer Administrator ist."
else
  IFS='|' read -r ADMIN_NAME ADMIN_IS_ADMIN ADMIN_HAS_PW <<< "${ADMIN_ROW}"
  echo "  admin-Benutzer vorhanden (is_admin=${ADMIN_IS_ADMIN}, Passwort gesetzt=${ADMIN_HAS_PW})"
  if [[ "${ADMIN_IS_ADMIN}" != "t" ]]; then
    warn "Benutzer 'admin' hat is_admin=false - kein Admin-Zugang über diesen Account."
  fi
  if [[ "${ADMIN_HAS_PW}" != "t" ]]; then
    warn "admin-Benutzer hat KEIN Passwort gesetzt (NULL - z.B. weil V7 das alte Default-Passwort entfernt hat, oder frische Installation) - vor Inbetriebnahme mit deploy/cutover/03-set-admin-password.sh setzen, sonst kein Portal-Login möglich."
  fi
fi

LOCATION_COUNT="$(q1 "SELECT COUNT(*) FROM locations;")"
echo "  locations: ${LOCATION_COUNT}"
if table_exists "terminal_tokens"; then
  echo "  Standort-Übersicht (Geräte / aktive Standort-Tokens je Standort):"
  # Redirection statt Pipe (< <(...)), damit die while-Schleife NICHT in einer Subshell läuft -
  # sonst würde "warn" (WARNINGS++) innerhalb der Schleife verlorengehen.
  while IFS='|' read -r lid lname ldevices ltokens; do
    echo "    [${lid}] ${lname}: ${ldevices} Geräte, ${ltokens} aktive Tokens"
    if [[ "${ltokens}" == "0" ]]; then
      warn "Standort '${lname}' (id=${lid}) hat noch KEIN aktives Standort-Token - vor Terminal-Umstellung mit deploy/cutover/02-issue-terminal-tokens.sh eines ausstellen."
    fi
  done < <(psql_cutover -tA -F'|' -c "
    SELECT l.id, l.name,
           (SELECT COUNT(*) FROM devices d WHERE d.location_id = l.id) AS devices,
           (SELECT COUNT(*) FROM terminal_tokens t WHERE t.location_id = l.id AND t.revoked_at IS NULL) AS active_tokens
    FROM locations l ORDER BY l.id;")
else
  echo "  (Tabelle terminal_tokens existiert noch nicht - erscheint erst nach der ersten"
  echo "   Flyway-Migration dieser DB [V3], danach erneut prüfen.)"
fi

DEVICE_COUNT="$(q1 "SELECT COUNT(*) FROM devices;")"
PROGRAM_COUNT="$(q1 "SELECT COUNT(*) FROM programs;")"
EXECUTION_COUNT="$(q1 "SELECT COUNT(*) FROM executions;")"
CREDIT_COUNT="$(q1 "SELECT COUNT(*) FROM credit_accounting;")"
echo "  devices: ${DEVICE_COUNT}"
echo "  programs: ${PROGRAM_COUNT}"
echo "  executions: ${EXECUTION_COUNT}"
echo "  credit_accounting: ${CREDIT_COUNT}"

echo
echo "================================================================"
if [[ "${WARNINGS}" == "0" ]]; then
  echo "Ergebnis: keine Warnungen. Bereit für den Cutover-Schritt (siehe deploy/cutover/README.md)."
else
  echo "Ergebnis: ${WARNINGS} Warnung(en) oben - vor dem Cutover prüfen/beheben."
fi
echo "================================================================"
