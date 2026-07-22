#!/bin/bash
# Cutover: Rollback-/Rückbau-Skript für einen ABGEBROCHENEN Cutover (Phase 6 AP2, siehe
# kb/05-migration-plan.md "Produktivumschaltung (Cutover)"). Spielt
# deploy/cutover/rollback-cutover.sql (idempotentes Reverse-DDL für V3..V10) gegen die
# Ziel-DB ein - macht sie wieder zum ALTEN Feld-Schema (Alt-Portal-WAR + Alt-Client mit
# JDBC-Direktzugriff), OHNE Geschäftsdaten zu verlieren. Details/Herleitung jeder einzelnen
# Umkehrung siehe die Kommentare in rollback-cutover.sql selbst.
#
# WICHTIG - Rollback ist die SEKUNDÄRE Rückwegoption. PRIMÄR/SICHERSTE Option bleibt immer
# das vor dem Cutover gezogene DB-Backup (siehe README.md, Abschnitt "Vorher: Backup!" und
# "Rollback") - dieses Skript ist für den Fall gedacht, dass im Cutover-Fenster bereits
# entstandene Neu-System-Daten (Terminal-Tokens/Idempotenz-Einträge/Offline-Konfiguration)
# erhalten bleiben sollen bzw. kein Backup-Restore gewünscht/möglich ist.
#
# Verbindung per Umgebungsvariablen (dieselben wie das Backend selbst kennt, siehe
# application.yml) - siehe lib-db-env.sh für Details/Defaults:
#   ELWASYS_DB_URL, ELWASYS_DB_USER, ELWASYS_DB_PASSWORD
#
# ACHTUNG - anders als 01/02/03 (die mit dem normalen Anwendungs-User elwaportal auskommen):
# die Umkehrung von V6 (Alt-Rollen elwaclient1/elwaapi/Gruppe elwaclients wieder ANLEGEN,
# siehe rollback-cutover.sql) braucht CREATEROLE-Rechte bzw. eine Superuser-Verbindung - exakt
# dieselbe Voraussetzung, die V6 selbst beim ursprünglichen Cutover schon hatte (deshalb
# verbindet sich auch verify-cutover-migration.sh für den eigentlichen Migrationsschritt als
# "postgres", nicht als "elwaportal" - siehe dessen Kommentare). Der normale Anwendungs-User
# "elwaportal" hat KEIN CREATEROLE und schlägt bei diesem Schritt fehl.
#
# Usage:
#   ELWASYS_DB_URL=jdbc:postgresql://prod-db:5432/elwasys \
#   ELWASYS_DB_USER=postgres ELWASYS_DB_PASSWORD=<Superuser-Passwort> \
#   deploy/cutover/rollback-cutover.sh
#
# set -euo pipefail + "psql -v ON_ERROR_STOP=1": bricht beim ersten Fehler ab, das SQL läuft
# ohnehin als EINE Transaktion (BEGIN...COMMIT in rollback-cutover.sql) - ein Fehlschlag lässt
# die DB unverändert (ROLLBACK), kein halb zurückgebauter Zwischenzustand.
set -euo pipefail

cd "$(dirname "$0")"
# shellcheck source=./lib-db-env.sh
source ./lib-db-env.sh

SQL_FILE="$(pwd)/rollback-cutover.sql"
if [[ ! -f "${SQL_FILE}" ]]; then
  echo "FEHLER: ${SQL_FILE} nicht gefunden." >&2
  exit 1
fi

echo "================================================================"
echo "ROLLBACK-/RÜCKBAU-SKRIPT für einen abgebrochenen Cutover"
echo "================================================================"
echo "Ziel-DB : ${ELWASYS_DB_URL}"
echo "DB-User : ${ELWASYS_DB_USER}"
echo
echo "WARNUNG: Dieses Skript ändert Schema/Rollen der oben genannten Datenbank. Auch wenn"
echo "         jede Umkehrung idempotent ist (mehrfaches Ausführen ist sicher), gilt wie bei"
echo "         jedem Schritt gegen eine echte Produktiv-DB: VORHER ein vollständiges"
echo "         Datenbank-Backup ziehen (siehe README.md, 'Vorher: Backup!'). Primärer,"
echo "         sicherster Rückweg bleibt immer das VOR dem Cutover gezogene Backup - dieses"
echo "         Skript ist die sekundäre Option."
echo
echo "SICHERHEITS-HINWEIS: Dieser Rollback legt die Alt-Rollen 'elwaclient1' (Passwort"
echo "         'elwaclient1') und 'elwaapi' (Passwort 'api1234') mit ihren historischen"
echo "         DEFAULT-Klartext-Passwörtern wieder an, falls sie nicht mehr existieren."
echo "         Diese Passwörter NACH dem Rollback zeitnah rotieren (ALTER USER ... WITH"
echo "         PASSWORD '<neu>')."
echo
echo "Spiele rollback-cutover.sql ein ..."
psql_cutover -v ON_ERROR_STOP=1 -f "${SQL_FILE}"

echo
echo "================================================================"
echo "Rollback abgeschlossen. Die DB entspricht wieder dem Alt-Weg-Schema (V3..V10"
echo "zurückgebaut, V2 bewusst unangetastet, flyway_schema_history entfernt - siehe"
echo "rollback-cutover.sql für Details je Umkehrung)."
echo
echo "Nächste Schritte:"
echo "  1. Alt-Rollen-Passwörter rotieren (siehe SICHERHEITS-HINWEIS oben)."
echo "  2. Alt-Portal/Alt-Client wieder gegen diese DB in Betrieb nehmen."
echo "================================================================"
