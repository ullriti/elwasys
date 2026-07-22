#!/bin/bash
# Cutover: Admin-/Benutzer-Passwort setzen (Phase 6 AP1, siehe kb/05-migration-plan.md
# "Produktivumschaltung"). Seit Phase 5 (V7__remove_default_admin_password.sql) hat eine
# frische bzw. gehärtete Installation KEIN bekanntes Default-Admin-Passwort mehr - das MUSS
# vor der Inbetriebnahme gesetzt werden, sonst ist kein Portal-Login möglich. Dünner Wrapper
# um das bestehende admin-cli-Profil (AdminPasswordCliRunner, siehe kb/04-build-and-run.md
# "Admin-/Benutzer-Passwort setzen").
#
# Verbindung per Umgebungsvariablen (dieselben wie das Backend selbst kennt, siehe
# application.yml) - siehe lib-db-env.sh für Details/Defaults:
#   ELWASYS_DB_URL, ELWASYS_DB_USER, ELWASYS_DB_PASSWORD
#
# Usage:
#   deploy/cutover/03-set-admin-password.sh [--username=admin]
#
# Das neue Passwort wird interaktiv per "read -s" abgefragt (nicht als Klartext-Argument
# dieses Skripts übergeben - taucht damit nicht in der Shell-History oder im "ps"-Auszug
# DIESES Skriptaufrufs auf). Bekannte Einschränkung, NICHT Teil dieses Arbeitspakets: das
# darunterliegende admin-cli (AdminPasswordCliRunner) selbst nimmt das Passwort weiterhin als
# "--password=..."-Kommandozeilenargument des kurzlebigen Java-Prozesses entgegen (siehe
# dessen Javadoc/kb/04-build-and-run.md) - das ist während der wenigen Sekunden dieses
# Prozesses grundsätzlich per "ps aux" auf demselben Host sichtbar. Dieses Skript vermeidet nur
# die zusätzliche, vermeidbare Exposition über seinen EIGENEN Aufruf/History; eine Behebung der
# darunterliegenden Einschränkung wäre eine Änderung an AdminPasswordCliRunner selbst
# (außerhalb des Scope dieses Arbeitspakets).
set -euo pipefail

cd "$(dirname "$0")"
# shellcheck source=./lib-db-env.sh
source ./lib-db-env.sh

REPO_ROOT="$(cd ../.. && pwd)"
JAR="${REPO_ROOT}/backend/target/elwasys-backend.jar"

USERNAME="admin"
for arg in "$@"; do
  case "${arg}" in
    --username=*) USERNAME="${arg#--username=}" ;;
    *)
      echo "Unbekanntes Argument: ${arg}" >&2
      echo "Usage: $0 [--username=<Benutzername>]" >&2
      exit 1
      ;;
  esac
done

if [[ ! -f "${JAR}" ]]; then
  echo "FEHLER: ${JAR} nicht gefunden. Erst bauen:" >&2
  echo "  mvn -q -f ${REPO_ROOT}/pom.xml package -pl backend -DskipTests" >&2
  exit 1
fi

echo "Neues Passwort für Benutzer '${USERNAME}' setzen."
read -r -s -p "Neues Passwort: " PASSWORD_1
echo
read -r -s -p "Passwort wiederholen: " PASSWORD_2
echo

if [[ "${PASSWORD_1}" != "${PASSWORD_2}" ]]; then
  echo "FEHLER: Die beiden Eingaben stimmen nicht überein. Abgebrochen." >&2
  exit 1
fi
if [[ -z "${PASSWORD_1}" ]]; then
  echo "FEHLER: Leeres Passwort ist nicht zulässig." >&2
  exit 1
fi
if [[ "${#PASSWORD_1}" -lt 8 ]]; then
  echo "WARNUNG: Das Passwort ist kürzer als 8 Zeichen - trotzdem fortfahren? [j/N]"
  read -r CONFIRM
  if [[ "${CONFIRM}" != "j" && "${CONFIRM}" != "J" ]]; then
    echo "Abgebrochen."
    exit 1
  fi
fi

ELWASYS_DB_URL="${ELWASYS_DB_URL}" ELWASYS_DB_USER="${ELWASYS_DB_USER}" \
  ELWASYS_DB_PASSWORD="${ELWASYS_DB_PASSWORD}" \
  java -jar "${JAR}" --spring.profiles.active=admin-cli \
    "--username=${USERNAME}" "--password=${PASSWORD_1}"

# Passwort-Variablen nicht länger als nötig im Prozessspeicher dieser Shell halten.
unset PASSWORD_1 PASSWORD_2

echo "Fertig - Passwort für '${USERNAME}' wurde gesetzt (Argon2id, siehe PasswordService)."
