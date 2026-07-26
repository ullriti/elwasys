#!/bin/bash
# elwasys DB-Restore (Issue #84/H5). Spielt ein mit backup-db.sh (bzw. dem Runbook-pg_dump-Cron)
# gezogenes komprimiertes SQL-Backup Schritt für Schritt in eine leere Datenbank zurück - der
# Wiederherstellungsfall, der in der finalen Review (Track R5) nur als Satz existierte ("Restore
# regelmäßig proben"), aber weder ausgeschrieben noch geskriptet war. Ein nie geprobter Restore
# ist kein Backup.
#
# Szenario: "Der Server ist weg" (Blitz/Diebstahl/Plattentod). Neuer Host, frisches PostgreSQL,
# das jüngste Offsite-Backup vorliegend -> dieses Skript stellt die DB wieder her, dann das
# Backend dagegen starten (Flyway validiert die bereits im Dump enthaltene Historie).
#
# Ablauf (siehe auch deploy/backup/README.md und CUTOVER-RUNBOOK.md Kap. 7a):
#   1. Owner-Rolle sicherstellen (elwaportal; die gehärteten App-Rollen legt die Migration V6
#      an - für den reinen Restore reicht der Owner, siehe README).
#   2. Leere Ziel-DB anlegen (Owner = elwaportal).
#   3. Dump einspielen (gunzip -c … | psql, bzw. cat bei unkomprimiertem .sql).
#   4. Stichproben-Verifikation (Zeilenzahlen der Ledger-/Stammdaten-Tabellen).
#   5. Hinweis: Backend dagegen starten, /actuator/health/liveness prüfen.
#
# DESTRUKTIV: schreibt in eine DB. Läuft nur nach Bestätigung (oder --yes); --dry-run zeigt den
# Plan, ohne irgendetwas auszuführen (das nutzt auch der Offline-Selbsttest).
#
# Requires: psql, createdb, gunzip (nur bei .gz). Als DB-Superuser ausführen (Default "postgres"),
# der Rollen/DB anlegen darf.
set -uo pipefail

# --- Defaults / Konfiguration ---------------------------------------------------------------
PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
SUPERUSER="${RESTORE_SUPERUSER:-postgres}"
TARGET_DB="${RESTORE_TARGET_DB:-elwasys}"
OWNER_ROLE="${RESTORE_OWNER_ROLE:-elwaportal}"
DUMP=""
CREATE_DB=1
ASSUME_YES=0
DRY_RUN=0

# Test-/Override-Hook: ersetzt die tatsächliche Ausführung eines Kommandos (eval). Der
# Offline-Selbsttest setzt ihn NICHT - er nutzt --dry-run. Im Betrieb leer lassen.
RESTORE_RUN_CMD="${RESTORE_RUN_CMD:-}"

usage() {
    cat <<EOF
Usage: $(basename "$0") --dump <datei.sql.gz|.sql> [Optionen]

  --dump <pfad>        Pfad zum Backup (pg_dump-Plain-SQL, .gz oder unkomprimiert). PFLICHT.
  --host <host>        DB-Host (Default: ${PGHOST}, bzw. \$PGHOST)
  --port <port>        DB-Port (Default: ${PGPORT}, bzw. \$PGPORT)
  --dbname <name>      Ziel-DB, wird neu angelegt (Default: ${TARGET_DB})
  --owner <rolle>      Owner-Rolle der Ziel-DB (Default: ${OWNER_ROLE})
  --superuser <rolle>  DB-Superuser für Rollen-/DB-Anlage + Restore (Default: ${SUPERUSER})
  --no-create-db       Ziel-DB NICHT anlegen (muss dann leer vorhanden sein)
  --yes                Nicht interaktiv rückfragen (für Automatisierung)
  --dry-run            Nur den Plan zeigen, nichts ausführen
  -h, --help           Diese Hilfe

Beispiel:
  sudo -u postgres $(basename "$0") --dump /var/backups/elwasys/elwasys-2026-07-24.sql.gz
EOF
}

# --- Argumente --------------------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
    case "$1" in
        --dump) DUMP="${2:-}"; shift 2 ;;
        --host) PGHOST="${2:-}"; shift 2 ;;
        --port) PGPORT="${2:-}"; shift 2 ;;
        --dbname) TARGET_DB="${2:-}"; shift 2 ;;
        --owner) OWNER_ROLE="${2:-}"; shift 2 ;;
        --superuser) SUPERUSER="${2:-}"; shift 2 ;;
        --no-create-db) CREATE_DB=0; shift ;;
        --yes) ASSUME_YES=1; shift ;;
        --dry-run) DRY_RUN=1; shift ;;
        -h|--help) usage; exit 0 ;;
        *) echo "FEHLER: unbekanntes Argument '$1'." >&2; usage >&2; exit 2 ;;
    esac
done

if [[ -z "${DUMP}" ]]; then
    echo "FEHLER: --dump <backup-datei> ist erforderlich." >&2
    usage >&2
    exit 2
fi
if [[ ! -f "${DUMP}" ]]; then
    echo "FEHLER: Backup-Datei '${DUMP}' existiert nicht." >&2
    exit 2
fi

# Kompressionsart am Dateinamen erkennen (Dump = Plain SQL, ggf. gzip).
case "${DUMP}" in
    *.gz) READ_CMD="gunzip -c $(printf '%q' "${DUMP}")" ;;
    *)    READ_CMD="cat $(printf '%q' "${DUMP}")" ;;
esac

PSQL_BASE="psql -v ON_ERROR_STOP=1 -h $(printf '%q' "${PGHOST}") -p $(printf '%q' "${PGPORT}") -U $(printf '%q' "${SUPERUSER}")"

# Führt ein Kommando aus - oder zeigt es nur (dry-run) bzw. reicht es an den Test-Hook.
run() {
    local desc="$1" cmd="$2"
    echo ">> ${desc}"
    echo "   ${cmd}"
    if [[ "${DRY_RUN}" == "1" ]]; then
        return 0
    fi
    if [[ -n "${RESTORE_RUN_CMD}" ]]; then
        eval "${RESTORE_RUN_CMD} $(printf '%q' "${cmd}")"
        return
    fi
    eval "${cmd}"
}

# Wie run(), bricht aber bei Fehlschlag den Restore ab (set -e ist bewusst aus). So läuft der
# Ablauf nicht nach einer fehlgeschlagenen Rollen-/DB-Anlage oder einem gescheiterten Einspielen
# weiter und druckt am Ende trotzdem die Erfolgs-Meldung.
run_or_die() {
    if ! run "$1" "$2"; then
        echo "FEHLER: Schritt fehlgeschlagen: $1 - Restore abgebrochen." >&2
        exit 1
    fi
}

echo "=============================================================="
echo " elwasys DB-Restore"
echo "   Backup:     ${DUMP}"
echo "   Ziel-DB:    ${TARGET_DB} @ ${PGHOST}:${PGPORT} (Owner ${OWNER_ROLE}, Superuser ${SUPERUSER})"
echo "   Neu anlegen: $([[ "${CREATE_DB}" == "1" ]] && echo ja || echo 'nein (muss leer existieren)')"
echo "   Modus:      $([[ "${DRY_RUN}" == "1" ]] && echo 'DRY-RUN (nichts wird ausgeführt)' || echo 'AUSFÜHREN (destruktiv)')"
echo "=============================================================="

if [[ "${DRY_RUN}" != "1" && "${ASSUME_YES}" != "1" ]]; then
    read -r -p "Fortfahren? Die Ziel-DB '${TARGET_DB}' wird (neu) beschrieben. [y/N] " answer
    case "${answer}" in
        y|Y|yes|j|J) ;;
        *) echo "Abgebrochen."; exit 1 ;;
    esac
fi

# 1) Owner-Rolle sicherstellen (idempotent).
run_or_die "Owner-Rolle '${OWNER_ROLE}' sicherstellen" \
    "${PSQL_BASE} -d postgres -c \"DO \\\$\\\$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='${OWNER_ROLE}') THEN CREATE ROLE ${OWNER_ROLE} LOGIN; END IF; END \\\$\\\$;\""

# 2) Leere Ziel-DB anlegen.
if [[ "${CREATE_DB}" == "1" ]]; then
    run_or_die "Leere Ziel-DB '${TARGET_DB}' anlegen (Owner ${OWNER_ROLE})" \
        "createdb -h $(printf '%q' "${PGHOST}") -p $(printf '%q' "${PGPORT}") -U $(printf '%q' "${SUPERUSER}") -O $(printf '%q' "${OWNER_ROLE}") $(printf '%q' "${TARGET_DB}")"
fi

# 3) Dump einspielen (psql läuft mit ON_ERROR_STOP=1 -> scheitert laut; run_or_die bricht ab,
#    damit die Erfolgs-Meldung unten NUR nach einem tatsächlich geglückten Einspielen erscheint).
run_or_die "Backup einspielen (${DUMP} -> ${TARGET_DB})" \
    "${READ_CMD} | ${PSQL_BASE} -d $(printf '%q' "${TARGET_DB}")"

# 4) Stichproben-Verifikation (informativ - eine fehlende Tabelle soll den bereits erfolgten
#    Restore nicht als Fehlschlag darstellen, nur warnen).
run "Stichprobe: Zeilenzahlen der Kern-/Ledger-Tabellen" \
    "${PSQL_BASE} -d $(printf '%q' "${TARGET_DB}") -c \"SELECT 'users' AS tabelle, count(*) FROM users UNION ALL SELECT 'executions', count(*) FROM executions UNION ALL SELECT 'credit_accounting', count(*) FROM credit_accounting;\"" \
    || echo "WARNUNG: Stichprobe fehlgeschlagen - Restore lief durch, aber die Zeilenzahlen konnten nicht ermittelt werden (Tabellen prüfen)." >&2

echo
echo "--------------------------------------------------------------"
if [[ "${DRY_RUN}" == "1" ]]; then
    echo "DRY-RUN abgeschlossen - es wurde NICHTS ausgeführt. Ohne --dry-run erneut aufrufen."
else
    echo "Restore abgeschlossen. Nächste Schritte:"
    echo "  1. Backend gegen '${TARGET_DB}' starten (ELWASYS_DB_URL/-USER/-PASSWORD passend setzen);"
    echo "     Flyway validiert die im Dump enthaltene Historie (kein erneutes Migrieren)."
    echo "  2. /actuator/health/liveness prüfen (UP) und einen Admin-Login/Stichprobe im Portal."
    echo "  3. RPO (Alter des Backups) und benötigte RTO (Dauer dieses Restores) festhalten."
fi
echo "--------------------------------------------------------------"
