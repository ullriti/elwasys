#!/bin/bash
# Offline-Selbsttest für restore-db.sh und backup-db.sh (Issue #84/H5). Kein PostgreSQL nötig:
# geprüft werden Argument-Validierung und der --dry-run-PLAN (welche Kommandos WÜRDEN laufen),
# nicht der echte Restore (der braucht eine DB + ein Dump und ist Teil der Generalprobe). Läuft
# in der CI (siehe .github/workflows/ci.yml, Job "cutover-scripts").
set -uo pipefail

cd "$(dirname "$0")"
RESTORE="./restore-db.sh"
BACKUP="./backup-db.sh"

WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"' EXIT

FAIL=0
check() {
    local desc="$1" expected="$2" actual="$3"
    if [[ "${actual}" == "${expected}" ]]; then
        echo "PASS: ${desc}"
    else
        echo "FAIL: ${desc}"
        echo "      erwartet: '${expected}'"
        echo "      erhalten: '${actual}'"
        FAIL=$((FAIL + 1))
    fi
}
contains() {
    local desc="$1" haystack="$2" needle="$3"
    if [[ "${haystack}" == *"${needle}"* ]]; then
        echo "PASS: ${desc}"
    else
        echo "FAIL: ${desc} (fehlt: '${needle}')"
        FAIL=$((FAIL + 1))
    fi
}
not_contains() {
    local desc="$1" haystack="$2" needle="$3"
    if [[ "${haystack}" != *"${needle}"* ]]; then
        echo "PASS: ${desc}"
    else
        echo "FAIL: ${desc} (unerwartet vorhanden: '${needle}')"
        FAIL=$((FAIL + 1))
    fi
}

echo "== restore: fehlendes --dump -> Fehler =="
rc=0; bash "${RESTORE}" >/dev/null 2>&1 || rc=$?
check "Exit 2 ohne --dump" "2" "${rc}"

echo "== restore: nicht existierendes Backup -> Fehler =="
rc=0; bash "${RESTORE}" --dump "${WORK}/gibtsnicht.sql.gz" >/dev/null 2>&1 || rc=$?
check "Exit 2 bei fehlender Backup-Datei" "2" "${rc}"

echo "== restore: --dry-run (.gz) zeigt den vollständigen Plan, führt nichts aus =="
GZDUMP="${WORK}/elwasys-2026-07-24.sql.gz"; echo "-- fake dump" | gzip > "${GZDUMP}"
out="$(bash "${RESTORE}" --dump "${GZDUMP}" --dry-run 2>&1)"; rc=$?
check "Exit 0 im dry-run" "0" "${rc}"
contains "Plan legt Owner-Rolle an" "${out}" "Owner-Rolle 'elwaportal'"
contains "Plan legt Ziel-DB an (createdb)" "${out}" "createdb"
contains "Plan entpackt das .gz (gunzip)" "${out}" "gunzip -c"
contains "Plan spielt in die Ziel-DB ein (psql)" "${out}" "psql"
contains "Plan macht eine Stichprobe (credit_accounting)" "${out}" "credit_accounting"
contains "dry-run sagt ausdrücklich: nichts ausgeführt" "${out}" "NICHTS ausgeführt"

echo "== restore: --dry-run (unkomprimiert) nutzt cat statt gunzip =="
PLAINDUMP="${WORK}/elwasys.sql"; echo "-- fake dump" > "${PLAINDUMP}"
out="$(bash "${RESTORE}" --dump "${PLAINDUMP}" --dry-run 2>&1)"
contains "unkomprimiertes Backup wird per cat eingelesen" "${out}" "cat "
not_contains "kein gunzip bei .sql" "${out}" "gunzip -c"

echo "== restore: --no-create-db lässt die DB-Anlage weg =="
out="$(bash "${RESTORE}" --dump "${GZDUMP}" --no-create-db --dry-run 2>&1)"
not_contains "kein createdb bei --no-create-db" "${out}" "createdb"

echo "== restore: --dbname/--owner landen im Plan =="
out="$(bash "${RESTORE}" --dump "${GZDUMP}" --dbname elwatest --owner elwaowner --dry-run 2>&1)"
contains "eigene Ziel-DB" "${out}" "elwatest"
contains "eigener Owner" "${out}" "elwaowner"

echo "== backup: --dry-run zeigt pg_dump + Retention (deterministischer Dateiname) =="
out="$(BACKUP_DATE=2026-07-24 bash "${BACKUP}" --out-dir "${WORK}/backups" --dry-run 2>&1)"; rc=$?
check "Exit 0 im backup-dry-run" "0" "${rc}"
contains "Dateiname trägt das Datum" "${out}" "elwasys-2026-07-24.sql.gz"
contains "pg_dump + gzip" "${out}" "pg_dump"
contains "Kompression" "${out}" "gzip"
contains "Retention über find -mtime" "${out}" "find"

echo "== backup: --docker-container schaltet auf docker exec um =="
out="$(BACKUP_DATE=2026-07-24 bash "${BACKUP}" --docker-container elwasys-postgres --dry-run 2>&1)"
contains "docker exec statt direktem pg_dump" "${out}" "docker exec"

# --- Regressionstests für die Review-Findings (fehlschlagende Ausführung, kein --dry-run) -----
# Ausführungs-Hook: protokolliert jedes Kommando und scheitert gezielt bei einem Muster ($FAIL_ON).
cat > "${WORK}/runhook.sh" <<'EOF'
#!/bin/bash
echo "$1" >> "${RUN_LOG}"
case "$1" in
  *"${FAIL_ON}"*) exit 1 ;;
esac
exit 0
EOF
chmod +x "${WORK}/runhook.sh"

echo "== backup: fehlgeschlagener Dump bricht VOR der Retention ab (Review-Finding #1) =="
RUN_LOG="${WORK}/backup-run.log"; : > "${RUN_LOG}"
rc=0
env RUN_LOG="${RUN_LOG}" FAIL_ON="pg_dump" BACKUP_RUN_CMD="bash ${WORK}/runhook.sh" BACKUP_DATE=2026-07-24 \
    bash "${BACKUP}" --out-dir "${WORK}/backups2" >/dev/null 2>&1 || rc=$?
runlog="$(cat "${RUN_LOG}")"
check "Exit != 0 bei fehlgeschlagenem Dump" "1" "${rc}"
contains "der Dump-Schritt wurde versucht" "${runlog}" "pg_dump"
not_contains "die Retention (find -delete) lief NICHT nach dem Fehlschlag" "${runlog}" "find "

echo "== restore: fehlgeschlagenes createdb bricht VOR dem Einspielen ab (Review-Finding #2) =="
RUN_LOG="${WORK}/restore-run.log"; : > "${RUN_LOG}"
rc=0
out="$(env RUN_LOG="${RUN_LOG}" FAIL_ON="createdb" RESTORE_RUN_CMD="bash ${WORK}/runhook.sh" \
    bash "${RESTORE}" --dump "${GZDUMP}" --yes 2>&1)" || rc=$?
runlog="$(cat "${RUN_LOG}")"
check "Exit != 0 bei fehlgeschlagenem createdb" "1" "${rc}"
contains "createdb wurde versucht" "${runlog}" "createdb"
not_contains "das Einspielen (gunzip|psql) lief NICHT nach dem createdb-Fehlschlag" "${runlog}" "gunzip -c"
not_contains "keine Erfolgs-Meldung nach Abbruch" "${out}" "Restore abgeschlossen. Nächste Schritte"

echo
if [[ "${FAIL}" == "0" ]]; then
    echo "ALLE SELBSTTEST-CHECKS PASS."
    exit 0
else
    echo "${FAIL} SELBSTTEST-CHECK(S) FEHLGESCHLAGEN."
    exit 1
fi
