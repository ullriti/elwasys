#!/bin/bash
# elwasys DB-Backup (Issue #84/H5). Zieht ein komprimiertes Plain-SQL-Backup der elwasys-DB -
# das Gegenstück zu restore-db.sh und die geskriptete Form des pg_dump-Cron-Einzeilers aus dem
# Runbook (Kap. 7a). Kann direkt als Cron/systemd-Timer laufen (siehe deploy/backup/README.md).
#
# Zwei Bezugswege (wie im Runbook):
#   - Externe/Host-DB:   pg_dump direkt gegen --host/--port (Default).
#   - Compose-Container:  --docker-container <name> -> "docker exec <name> pg_dump ...".
#
# Retention: Dumps älter als --keep-days werden entfernt (Default 30).
#
# WICHTIG - Backup-Scope (Review R5): dieses Skript sichert NUR die Datenbank. Nicht erfasst und
# separat (verschlüsselt, offsite) zu sichern:
#   - Backend-Secrets: deploy/compose/.env bzw. Helm-Values (DB-/SMTP-Passwort, Pushover-Token).
#   - Je Terminal: /opt/elwasys/elwasys.properties (inkl. backend.token/Standort-Token).
#   - Bewusstes Nicht-Backup: das Offline-Journal auf der Terminal-SD-Karte (noch nicht replayte
#     Offline-Buchungen) - stirbt eine SD-Karte mit unrepliziertem Journal, ist diese Buchung
#     verloren. Als Betriebsrisiko benannt, siehe README/Runbook.
set -uo pipefail

PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
DB_USER="${BACKUP_DB_USER:-elwasys}"
DB_NAME="${BACKUP_DB_NAME:-elwasys}"
OUT_DIR="${BACKUP_OUT_DIR:-/var/backups/elwasys}"
KEEP_DAYS="${BACKUP_KEEP_DAYS:-30}"
DOCKER_CONTAINER=""
DRY_RUN=0

# Test-/Override-Hook (wie restore-db.sh): ersetzt die Ausführung. Im Betrieb leer lassen.
BACKUP_RUN_CMD="${BACKUP_RUN_CMD:-}"
# Datums-Override (nur Tests/Reproduzierbarkeit); im Betrieb leer = date +%F.
BACKUP_DATE="${BACKUP_DATE:-}"

usage() {
    cat <<EOF
Usage: $(basename "$0") [Optionen]

  --host <host>              DB-Host (Default: ${PGHOST})
  --port <port>              DB-Port (Default: ${PGPORT})
  --user <rolle>             DB-User (Default: ${DB_USER})
  --dbname <name>            DB-Name (Default: ${DB_NAME})
  --out-dir <pfad>           Zielverzeichnis (Default: ${OUT_DIR})
  --keep-days <n>            Retention in Tagen (Default: ${KEEP_DAYS})
  --docker-container <name>  pg_dump via "docker exec <name>" statt direkt
  --dry-run                  Nur den Plan zeigen, nichts ausführen
  -h, --help                 Diese Hilfe
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --host) PGHOST="${2:-}"; shift 2 ;;
        --port) PGPORT="${2:-}"; shift 2 ;;
        --user) DB_USER="${2:-}"; shift 2 ;;
        --dbname) DB_NAME="${2:-}"; shift 2 ;;
        --out-dir) OUT_DIR="${2:-}"; shift 2 ;;
        --keep-days) KEEP_DAYS="${2:-}"; shift 2 ;;
        --docker-container) DOCKER_CONTAINER="${2:-}"; shift 2 ;;
        --dry-run) DRY_RUN=1; shift ;;
        -h|--help) usage; exit 0 ;;
        *) echo "FEHLER: unbekanntes Argument '$1'." >&2; usage >&2; exit 2 ;;
    esac
done

stamp="${BACKUP_DATE}"
if [[ -z "${stamp}" ]]; then stamp="$(date +%F)"; fi
OUT_FILE="${OUT_DIR}/elwasys-${stamp}.sql.gz"

if [[ -n "${DOCKER_CONTAINER}" ]]; then
    DUMP_CMD="docker exec $(printf '%q' "${DOCKER_CONTAINER}") pg_dump -U $(printf '%q' "${DB_USER}") $(printf '%q' "${DB_NAME}")"
else
    DUMP_CMD="pg_dump -h $(printf '%q' "${PGHOST}") -p $(printf '%q' "${PGPORT}") -U $(printf '%q' "${DB_USER}") $(printf '%q' "${DB_NAME}")"
fi

run() {
    local desc="$1" cmd="$2"
    echo ">> ${desc}"
    echo "   ${cmd}"
    if [[ "${DRY_RUN}" == "1" ]]; then return 0; fi
    if [[ -n "${BACKUP_RUN_CMD}" ]]; then eval "${BACKUP_RUN_CMD} $(printf '%q' "${cmd}")"; return; fi
    eval "${cmd}"
}

echo "== elwasys DB-Backup -> ${OUT_FILE} =="
run "Zielverzeichnis sicherstellen" "mkdir -p $(printf '%q' "${OUT_DIR}")"
run "Dump ziehen + komprimieren" "${DUMP_CMD} | gzip > $(printf '%q' "${OUT_FILE}")"
run "Retention: Dumps älter als ${KEEP_DAYS} Tage entfernen" \
    "find $(printf '%q' "${OUT_DIR}") -name 'elwasys-*.sql.gz' -mtime +$(printf '%q' "${KEEP_DAYS}") -delete"

echo
if [[ "${DRY_RUN}" == "1" ]]; then
    echo "DRY-RUN - nichts ausgeführt."
else
    echo "Backup abgeschlossen: ${OUT_FILE}"
    echo "ERINNERUNG: Backup offsite/offhost spiegeln und Secrets/Terminal-Properties separat"
    echo "            sichern (siehe Kopf-Kommentar / README - der Blitz vernichtet sonst Host UND Backup)."
fi
