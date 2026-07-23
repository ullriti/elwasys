#!/bin/bash
# Trockentests fuer das Terminal-Auto-Update (Issues #34, #62, #63) - vollstaendig OFFLINE,
# ohne echte Downloads/apt/sudo/JVM. Baut in einem Temp-ELWA_ROOT mit Fake-Jars, Fake-Restart,
# Fake-pgrep und (fuer #62) einem Fake-wget die relevanten Szenarien nach und prueft das
# beobachtbare Verhalten von auto-update-watchdog.sh / update.sh.
#
# Aufruf:
#   deploy/terminal/auto-update-selftest.sh
# Exit 0 = alle Faelle bestanden, sonst != 0. Kein Netz/Root noetig.
#
# Abgedeckte Faelle:
#   #34-A  Erster Cron-Lauf mit kaputtem Update (Start wird nie bedienbereit) -> Rollback,
#          ${ELWA_ROOT}/.update-failed enthaelt die kaputte Zielversion, gecachtes Jar geloescht.
#   #34-B  ZWEITER Cron-Lauf bei UNVERAENDERTER kaputter Zielversion -> KEIN erneuter
#          update.sh-/Restart-Versuch (Endlosschleifen-Schutz). Das ist der Kern-Regressionstest.
#   #34-C  Erscheint eine ANDERE Zielversion -> Sperre aufgehoben, Update wird wieder versucht.
#   #62-A  Download mit falscher SHA-256 -> verworfen, kein Deploy (Symlink unveraendert).
#   #62-B  Download mit korrekter SHA-256 + gueltigem Zip -> ausgerollt (Positiv-Kontrolle;
#          wird uebersprungen, wenn kein zip/python3 zum Bauen eines gueltigen Jars da ist).
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WATCHDOG="${SCRIPT_DIR}/auto-update-watchdog.sh"
UPDATE_SH="${SCRIPT_DIR}/update.sh"

PASS=0
FAIL=0
ok()   { echo "  PASS: $*"; PASS=$((PASS + 1)); }
bad()  { echo "  FAIL: $*"; FAIL=$((FAIL + 1)); }

WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"' EXIT

# --- gemeinsame Fakes -------------------------------------------------------
BIN="${WORK}/bin"
mkdir -p "${BIN}"
# Fake-Restart: protokolliert nur und beendet mit 0 (simuliert "java beendet" - ohne echten
# Supervisor wird der Readiness-Marker NIE fortgeschrieben, also gilt der Start als gescheitert).
cat > "${BIN}/fake-restart.sh" <<EOF
#!/bin/bash
echo "RESTART \$*" >> "${WORK}/restart.log"
exit 0
EOF
chmod +x "${BIN}/fake-restart.sh"
# Wrapper um update.sh: protokolliert jeden Aufruf und reicht an das echte update.sh durch.
cat > "${BIN}/update-wrapper.sh" <<EOF
#!/bin/bash
echo "UPDATE \$*" >> "${WORK}/update.log"
exec bash "${UPDATE_SH}" "\$@"
EOF
chmod +x "${BIN}/update-wrapper.sh"

reset_logs() { : > "${WORK}/restart.log"; : > "${WORK}/update.log"; }

# Baut ein frisches Terminal-Layout in $1 (ELWA_ROOT): latest -> 1.0.0, previous -> 0.9.0.
make_root() {
    local root="$1"
    rm -rf "${root}"; mkdir -p "${root}"
    ( cd "${root}"
      echo "jar-0.9.0" > raspi-client-0.9.0.jar
      echo "jar-1.0.0" > raspi-client-1.0.0.jar
      ln -sfn raspi-client-1.0.0.jar raspi-client.latest.jar
      ln -sfn raspi-client-0.9.0.jar raspi-client.previous.jar )
}

link_of() { basename "$(readlink "$1")"; }

# ============================================================================
# #34 – Endlosschleifen-Schutz
# ============================================================================
echo "== #34: Endlosschleifen-Schutz nach fehlgeschlagenem Update =="
ROOT="${WORK}/root34"
make_root "${ROOT}"
# Kaputte Zielversion 2.0.0 vorab ablegen, damit update.sh NICHT herunterlaedt (Download ist
# nicht Gegenstand dieses Falls) - der START scheitert (kein Marker), das ist der Ausloeser.
echo "jar-2.0.0-broken" > "${ROOT}/raspi-client-2.0.0.jar"

run_watchdog() {
    local target="$1"
    ELWA_ROOT="${ROOT}" \
    ELWA_UPDATE_SCRIPT="${BIN}/update-wrapper.sh" \
    ELWA_RESTART_CMD="${BIN}/fake-restart.sh" \
    ELWA_JAVA_PGREP="true" \
    ELWA_LATEST_VERSION_CMD="echo ${target}" \
    ELWA_UPDATE_DEADLINE="1" \
    ELWA_IDLE_GATE_ENABLED="0" \
    bash "${WATCHDOG}" > "${WORK}/wd.out" 2>&1
    return $?
}

# --- #34-A: erster Lauf -> Rollback + .update-failed
reset_logs
run_watchdog "2.0.0" || true
if [[ -f "${ROOT}/.update-failed" && "$(cat "${ROOT}/.update-failed")" == "2.0.0" ]]; then
    ok "#34-A .update-failed enthaelt die gescheiterte Zielversion 2.0.0"
else
    bad "#34-A .update-failed fehlt/falsch (Inhalt: '$(cat "${ROOT}/.update-failed" 2>/dev/null)')"
fi
if [[ "$(link_of "${ROOT}/raspi-client.latest.jar")" == "raspi-client-1.0.0.jar" ]]; then
    ok "#34-A latest wurde auf die vorige Version (1.0.0) zurueckgerollt"
else
    bad "#34-A latest zeigt nicht auf 1.0.0 (sondern $(link_of "${ROOT}/raspi-client.latest.jar"))"
fi
if [[ ! -f "${ROOT}/raspi-client-2.0.0.jar" ]]; then
    ok "#34-A gecachtes kaputtes Jar raspi-client-2.0.0.jar wurde geloescht"
else
    bad "#34-A kaputtes Jar raspi-client-2.0.0.jar liegt noch da"
fi
if grep -q UPDATE "${WORK}/update.log" && grep -q RESTART "${WORK}/restart.log"; then
    ok "#34-A erster Lauf hat update.sh UND Restart tatsaechlich versucht"
else
    bad "#34-A erster Lauf hat update.sh/Restart nicht versucht (unerwartet)"
fi

# --- #34-B: zweiter Lauf bei unveraenderter kaputter Zielversion -> No-op (Kern-Regressionstest)
reset_logs
# Kaputtes Jar erneut bereitstellen (der Guard darf trotzdem NICHT anfassen).
echo "jar-2.0.0-broken" > "${ROOT}/raspi-client-2.0.0.jar"
run_watchdog "2.0.0"; rc=$?
if [[ "${rc}" -eq 0 ]]; then
    ok "#34-B zweiter Lauf endet als No-op (Exit 0)"
else
    bad "#34-B zweiter Lauf endet mit Exit ${rc} (erwartet 0)"
fi
if [[ ! -s "${WORK}/update.log" ]]; then
    ok "#34-B zweiter Lauf ruft update.sh NICHT erneut auf (kein erneutes kaputtes Update)"
else
    bad "#34-B zweiter Lauf hat update.sh erneut aufgerufen: $(cat "${WORK}/update.log")"
fi
if [[ ! -s "${WORK}/restart.log" ]]; then
    ok "#34-B zweiter Lauf loest KEINEN java-Kill/Neustart aus"
else
    bad "#34-B zweiter Lauf hat einen Restart ausgeloest: $(cat "${WORK}/restart.log")"
fi

# --- #34-C: andere Zielversion -> Sperre aufgehoben, Update wird wieder versucht
reset_logs
echo "jar-3.0.0-broken" > "${ROOT}/raspi-client-3.0.0.jar"
run_watchdog "3.0.0" || true
if grep -q UPDATE "${WORK}/update.log"; then
    ok "#34-C neue Zielversion 3.0.0 hebt die Sperre auf (Update wird wieder versucht)"
else
    bad "#34-C neue Zielversion wurde faelschlich weiter gesperrt"
fi

# ============================================================================
# #62 – Integritaetspruefung des Downloads
# ============================================================================
echo "== #62: Integritaetspruefung (SHA-256 + Zip-Struktur) =="
ROOT62="${WORK}/root62"
make_root "${ROOT62}"

# Fake-wget: schreibt fuer *.jar den Inhalt aus FAKE_JAR_FILE, fuer *.jar.sha256 eine Pruefzeile
# mit FAKE_SHA. So durchlaeuft update.sh --version den ECHTEN verify_jar_integrity-Pfad offline.
cat > "${BIN}/wget" <<EOF
#!/bin/bash
out=""; url=""
while [[ \$# -gt 0 ]]; do
  case "\$1" in
    -O) out="\$2"; shift 2 ;;
    -*) shift ;;
    *) url="\$1"; shift ;;
  esac
done
case "\$url" in
  *.jar.sha256) printf '%s  %s\n' "\${FAKE_SHA}" "\$(basename "\${url%.sha256}")" > "\$out" ;;
  *.jar)        cp "\${FAKE_JAR_FILE}" "\$out" ;;
esac
exit 0
EOF
chmod +x "${BIN}/wget"

run_update_version() {
    local target="$1"
    PATH="${BIN}:${PATH}" \
    ELWA_ROOT="${ROOT62}" \
    ELWA_RESTART_CMD="${BIN}/fake-restart.sh" \
    ELWA_JAVA_PGREP="true" \
    FAKE_SHA="${FAKE_SHA}" \
    FAKE_JAR_FILE="${FAKE_JAR_FILE}" \
    bash "${UPDATE_SH}" --version "${target}" > "${WORK}/upd.out" 2>&1
    return $?
}

# --- #62-A: falsche Pruefsumme -> verworfen, kein Deploy
FAKE_JAR_FILE="${WORK}/corrupt.jar"
printf 'CORRUPT-NOT-A-VALID-ZIP' > "${FAKE_JAR_FILE}"
FAKE_SHA="0000000000000000000000000000000000000000000000000000000000000000"
run_update_version "2.0.0"; rc=$?
if [[ "${rc}" -ne 0 ]]; then
    ok "#62-A manipuliertes Jar (falsche SHA-256) -> update.sh bricht ab (Exit ${rc})"
else
    bad "#62-A manipuliertes Jar wurde NICHT abgelehnt (Exit 0)"
fi
if [[ "$(link_of "${ROOT62}/raspi-client.latest.jar")" == "raspi-client-1.0.0.jar" && ! -f "${ROOT62}/raspi-client-2.0.0.jar" ]]; then
    ok "#62-A kein Deploy: latest unveraendert (1.0.0), kein raspi-client-2.0.0.jar abgelegt"
else
    bad "#62-A trotz falscher Pruefsumme etwas ausgerollt/abgelegt"
fi
if [[ ! -f "${ROOT62}/raspi-client-2.0.0.jar.part" ]]; then
    ok "#62-A verworfener .part-Download hinterlaesst keine Reste"
else
    bad "#62-A .part-Datei wurde nicht aufgeraeumt"
fi

# --- #62-B: korrekte Pruefsumme + gueltiges Zip -> ausgerollt (Positiv-Kontrolle)
GOODJAR="${WORK}/good.jar"
built=0
if command -v zip >/dev/null 2>&1; then
    ( cd "${WORK}" && echo hi > _e.txt && zip -q good.jar _e.txt ) && built=1
elif command -v python3 >/dev/null 2>&1; then
    python3 - "$GOODJAR" <<'PY' && built=1
import sys, zipfile
with zipfile.ZipFile(sys.argv[1], "w") as z:
    z.writestr("e.txt", "hi")
PY
fi
if [[ "${built}" -eq 1 ]]; then
    FAKE_JAR_FILE="${GOODJAR}"
    FAKE_SHA="$(sha256sum "${GOODJAR}" | awk '{print $1}')"
    make_root "${ROOT62}"   # sauberes Layout
    run_update_version "2.0.0"; rc=$?
    if [[ "${rc}" -eq 0 && "$(link_of "${ROOT62}/raspi-client.latest.jar")" == "raspi-client-2.0.0.jar" ]]; then
        ok "#62-B gueltiges Jar (passende SHA-256 + Zip) wird ausgerollt (latest -> 2.0.0)"
    else
        bad "#62-B gueltiges Jar wurde NICHT ausgerollt (Exit ${rc}, latest=$(link_of "${ROOT62}/raspi-client.latest.jar"))"
    fi
else
    echo "  SKIP: #62-B (weder zip noch python3 verfuegbar, um ein gueltiges Test-Jar zu bauen)"
fi

# ============================================================================
echo
echo "================================================================"
echo "Ergebnis: ${PASS} bestanden, ${FAIL} fehlgeschlagen."
echo "================================================================"
[[ "${FAIL}" -eq 0 ]]
