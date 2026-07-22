#!/bin/bash
# Cutover: Standort-Token für ein Terminal ausstellen (Phase 6 AP1, siehe
# kb/05-migration-plan.md "Produktivumschaltung"). Dünner Wrapper um das bestehende
# token-cli-Profil (TerminalTokenCliRunner, siehe kb/04-build-and-run.md "Standort-Tokens
# erzeugen/widerrufen") - listet zuerst die vorhandenen Standorte, erzeugt dann für den per
# --location angegebenen Standort ein neues Token.
#
# Verbindung per Umgebungsvariablen (dieselben wie das Backend selbst kennt, siehe
# application.yml) - siehe lib-db-env.sh für Details/Defaults:
#   ELWASYS_DB_URL, ELWASYS_DB_USER, ELWASYS_DB_PASSWORD
#
# Usage:
#   deploy/cutover/02-issue-terminal-tokens.sh --location=<Standortname> [--label=<Text>]
#
# Das erzeugte Klartext-Token wird NUR EINMAL auf stdout ausgegeben und von diesem Skript
# NIRGENDS gespeichert (weder in eine Datei noch in eine Variable, die über das Skriptende
# hinaus lebt) - sofort sicher in die Terminal-Konfiguration übernehmen
# (Client-Raspi/elwasys.example.properties: backend.token), ein erneutes Anzeigen ist NICHT
# möglich (nur der Hash landet in der DB, siehe TerminalTokenService).
set -euo pipefail

cd "$(dirname "$0")"
# shellcheck source=./lib-db-env.sh
source ./lib-db-env.sh

REPO_ROOT="$(cd ../.. && pwd)"
JAR="${REPO_ROOT}/backend/target/elwasys-backend.jar"

LOCATION=""
LABEL=""
for arg in "$@"; do
  case "${arg}" in
    --location=*) LOCATION="${arg#--location=}" ;;
    --label=*) LABEL="${arg#--label=}" ;;
    *)
      echo "Unbekanntes Argument: ${arg}" >&2
      echo "Usage: $0 --location=<Standortname> [--label=<Text>]" >&2
      exit 1
      ;;
  esac
done

if [[ ! -f "${JAR}" ]]; then
  echo "FEHLER: ${JAR} nicht gefunden. Erst bauen:" >&2
  echo "  mvn -q -f ${REPO_ROOT}/pom.xml package -pl backend -DskipTests" >&2
  exit 1
fi

echo "Vorhandene Standorte:"
psql_cutover -tA -F' | ' -c "SELECT id, name FROM locations ORDER BY id;" | while IFS='|' read -r lid lname; do
  echo "  [${lid}] ${lname}"
done
echo

if [[ -z "${LOCATION}" ]]; then
  echo "Kein --location angegeben - nur die Standortliste oben wurde ausgegeben."
  echo "Usage: $0 --location=<Standortname> [--label=<Text>]"
  exit 1
fi

echo "Erzeuge Standort-Token für '${LOCATION}' ..."
JAVA_ARGS=(--spring.profiles.active=token-cli "--location=${LOCATION}")
if [[ -n "${LABEL}" ]]; then
  JAVA_ARGS+=("--label=${LABEL}")
fi

# stdout des token-cli-Laufs abfangen (nur transient in dieser Shell-Variable, nie auf Platte
# geschrieben), um bei einem Fehlschlag klar zu scheitern statt das Klartext-Token stumm zu
# verlieren - danach sofort ausgegeben und die Variable ist mit Skriptende wieder weg.
set +e
TOKEN_OUTPUT="$(ELWASYS_DB_URL="${ELWASYS_DB_URL}" ELWASYS_DB_USER="${ELWASYS_DB_USER}" \
  ELWASYS_DB_PASSWORD="${ELWASYS_DB_PASSWORD}" \
  java -jar "${JAR}" "${JAVA_ARGS[@]}" 2>&1)"
STATUS=$?
set -e

echo "${TOKEN_OUTPUT}"
if [[ ${STATUS} -ne 0 ]]; then
  echo >&2
  echo "FEHLER: token-cli ist mit Exit-Code ${STATUS} fehlgeschlagen (siehe Ausgabe oben)." >&2
  exit "${STATUS}"
fi

echo
echo "================================================================"
echo "WICHTIG: Das Klartext-Token oben ist die EINZIGE Anzeige - es wird nirgends"
echo "gespeichert (auch nicht von diesem Skript). Jetzt sofort in die Terminal-"
echo "Konfiguration übernehmen (backend.token in elwasys.properties am Terminal)."
echo "================================================================"
