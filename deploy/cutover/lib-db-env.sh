#!/bin/bash
# Gemeinsame DB-Verbindungslogik für die deploy/cutover/*.sh Skripte (01/02/03). Wird per
# "source" eingebunden - kein eigenständiges Skript, kein Shebang-Aufruf vorgesehen.
#
# Liest dieselben drei Umgebungsvariablen, die auch das Backend selbst kennt (siehe
# backend/src/main/resources/application.yml: ELWASYS_DB_URL/_USER/_PASSWORD) - Defaults
# passen zur lokalen Entwicklungsumgebung, siehe docs/kb/04-build-and-run.md:
#   ELWASYS_DB_URL=jdbc:postgresql://localhost:5432/elwasys
#   ELWASYS_DB_USER=elwaportal
#   ELWASYS_DB_PASSWORD=elwaportal
# Für eine Produktiv-DB einfach vor dem Skriptaufruf exportieren bzw. voranstellen, z.B.:
#   ELWASYS_DB_URL=jdbc:postgresql://prod-db.example.internal:5432/elwasys \
#   ELWASYS_DB_USER=elwaportal ELWASYS_DB_PASSWORD=<echtes Passwort> \
#   deploy/cutover/01-preflight-check.sh
: "${ELWASYS_DB_URL:=jdbc:postgresql://localhost:5432/elwasys}"
: "${ELWASYS_DB_USER:=elwaportal}"
: "${ELWASYS_DB_PASSWORD:=elwaportal}"

# psql versteht kein "jdbc:"-Präfix und keine reine URL ohne Schema-Zerlegung für -h/-p/-d -
# JDBC-URL daher in host/port/dbname zerlegen. Erwartetes Format:
#   jdbc:postgresql://<host>[:<port>]/<dbname>[?params]
_cutover_jdbc_rest="${ELWASYS_DB_URL#jdbc:postgresql://}"
if [[ "${_cutover_jdbc_rest}" == "${ELWASYS_DB_URL}" ]]; then
  echo "FEHLER: ELWASYS_DB_URL muss mit 'jdbc:postgresql://' beginnen (war: '${ELWASYS_DB_URL}')." >&2
  exit 1
fi
_cutover_hostport="${_cutover_jdbc_rest%%/*}"
CUTOVER_DB_NAME="${_cutover_jdbc_rest#*/}"
CUTOVER_DB_NAME="${CUTOVER_DB_NAME%%\?*}"
CUTOVER_DB_HOST="${_cutover_hostport%%:*}"
if [[ "${_cutover_hostport}" == *:* ]]; then
  CUTOVER_DB_PORT="${_cutover_hostport##*:}"
else
  CUTOVER_DB_PORT="5432"
fi
unset _cutover_jdbc_rest _cutover_hostport

# PGPASSWORD statt Klartext-Argument: taucht damit nicht in "ps"/Shell-History der psql-Aufrufe
# auf (nur als Umgebungsvariable dieses Prozesses und seiner Kindprozesse sichtbar).
export PGPASSWORD="${ELWASYS_DB_PASSWORD}"

# Dünner Wrapper um psql mit den oben zerlegten Verbindungsdaten - von den aufrufenden
# Skripten statt eines rohen "psql ..." zu benutzen.
psql_cutover() {
  psql -h "${CUTOVER_DB_HOST}" -p "${CUTOVER_DB_PORT}" -U "${ELWASYS_DB_USER}" -d "${CUTOVER_DB_NAME}" "$@"
}
