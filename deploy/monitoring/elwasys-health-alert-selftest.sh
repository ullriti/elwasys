#!/bin/bash
# Offline-Selbsttest für elwasys-health-alert.sh (Issue #83/H4). Kein Netz/Backend/Pushover
# nötig: die drei Sonden (HTTP-Health, Cert-Resttage, Plattenplatz) und der Zustellkanal werden
# über die *_CMD-Hooks durch Fixtures ersetzt, der Zustand in ein Temp-Verzeichnis gelenkt. So
# ist die volle Alarm-/Anti-Spam-/Recovery-Logik deterministisch prüfbar. Läuft in der CI
# (siehe .github/workflows/ci.yml, Job "cutover-scripts").
set -uo pipefail

cd "$(dirname "$0")"
SCRIPT="./elwasys-health-alert.sh"

WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"' EXIT

# Fixture-Sonden: ignorieren ihr Argument und geben den jeweiligen Env-Wert aus.
cat > "${WORK}/http.sh"  <<'EOF'
#!/bin/bash
echo "${HTTP_CODE:-200}"
EOF
cat > "${WORK}/cert.sh"  <<'EOF'
#!/bin/bash
echo "${CERT_DAYS:-}"
EOF
cat > "${WORK}/df.sh"    <<'EOF'
#!/bin/bash
echo "${DISK_PCT:-}"
EOF
# Recorder-Zustellkanal: hängt den Betreff (=$1) an die NOTIFY_LOG-Datei an.
cat > "${WORK}/notify.sh" <<'EOF'
#!/bin/bash
echo "$1" >> "${NOTIFY_LOG}"
EOF
chmod +x "${WORK}"/*.sh

export NOTIFY_LOG="${WORK}/notify.log"
: > "${NOTIFY_LOG}"

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

# Ruft das Skript mit den Fixture-Hooks auf. Die szenariospezifischen Variablen
# (HTTP_CODE/CERT_DAYS/DISK_PCT/STATE u.a.) werden als NAME=WERT-Argumente durchgereicht - über
# "env", weil aus "$@" expandierte NAME=WERT-Wörter von bash NICHT mehr als Zuweisungen erkannt
# würden (sie landeten sonst als Kommandoname -> exit 127).
run_alert() {
    env \
        ELWASYS_ALERT_HTTP_CMD="bash ${WORK}/http.sh" \
        ELWASYS_ALERT_CERT_DAYS_CMD="bash ${WORK}/cert.sh" \
        ELWASYS_ALERT_DF_CMD="bash ${WORK}/df.sh" \
        ELWASYS_ALERT_NOTIFY_CMD="bash ${WORK}/notify.sh" \
        "$@" bash "${SCRIPT}" >/dev/null 2>&1
    echo $?
}

notify_count() { wc -l < "${NOTIFY_LOG}" | tr -d ' '; }
last_notify()  { tail -n1 "${NOTIFY_LOG}" 2>/dev/null || echo ""; }

echo "== Szenario 1: alles OK -> kein Alarm, Exit 0 =="
S1="${WORK}/s1"; : > "${NOTIFY_LOG}"
rc="$(run_alert HTTP_CODE=200 ELWASYS_ALERT_STATE_DIR="${S1}")"
check "Exit 0 bei Health 200" "0" "${rc}"
check "keine Benachrichtigung bei OK" "0" "$(notify_count)"

echo "== Szenario 2: Health FAIL (503) -> genau ein ALARM, Exit 1 =="
S2="${WORK}/s2"; : > "${NOTIFY_LOG}"
rc="$(run_alert HTTP_CODE=503 ELWASYS_ALERT_STATE_DIR="${S2}")"
check "Exit 1 bei Health 503" "1" "${rc}"
check "genau eine Benachrichtigung" "1" "$(notify_count)"
check "Betreff ist ein health-ALARM" "[elwasys] ALARM: health" "$(last_notify)"

echo "== Szenario 3: Health BLEIBT FAIL (Renotify aus) -> kein neuer Alarm =="
# gleicher State wie S2, RENOTIFY=0 (nie wiederholen)
rc="$(run_alert HTTP_CODE=503 ELWASYS_ALERT_STATE_DIR="${S2}" ELWASYS_ALERT_RENOTIFY_SECONDS=0)"
check "Exit 1 (weiterhin FAIL)" "1" "${rc}"
check "keine ZWEITE Benachrichtigung (Anti-Spam)" "1" "$(notify_count)"

echo "== Szenario 4: Erholung (200) -> genau eine 'behoben'-Meldung =="
rc="$(run_alert HTTP_CODE=200 ELWASYS_ALERT_STATE_DIR="${S2}")"
check "Exit 0 nach Erholung" "0" "${rc}"
check "zweite Benachrichtigung (Recovery)" "2" "$(notify_count)"
check "Betreff ist eine Recovery-Meldung" "[elwasys] behoben: health" "$(last_notify)"

echo "== Szenario 5: Backend nicht erreichbar (000) -> ALARM =="
S5="${WORK}/s5"; : > "${NOTIFY_LOG}"
rc="$(run_alert HTTP_CODE=000 ELWASYS_ALERT_STATE_DIR="${S5}")"
check "Exit 1 bei Nichterreichbarkeit" "1" "${rc}"
check "Alarm bei 000" "[elwasys] ALARM: health" "$(last_notify)"

echo "== Szenario 6: Zertifikat läuft bald ab -> ALARM =="
S6="${WORK}/s6"; : > "${NOTIFY_LOG}"
rc="$(run_alert HTTP_CODE=200 CERT_DAYS=5 \
      ELWASYS_ALERT_TLS_HOST=elwasys.example.com \
      ELWASYS_ALERT_CERT_MIN_DAYS=14 ELWASYS_ALERT_STATE_DIR="${S6}")"
check "Exit 1 bei baldigem Cert-Ablauf" "1" "${rc}"
check "Cert-Alarm ausgelöst" "[elwasys] ALARM: cert" "$(last_notify)"

echo "== Szenario 6b: Zertifikat noch lange gültig -> kein Alarm =="
S6b="${WORK}/s6b"; : > "${NOTIFY_LOG}"
rc="$(run_alert HTTP_CODE=200 CERT_DAYS=90 \
      ELWASYS_ALERT_TLS_HOST=elwasys.example.com \
      ELWASYS_ALERT_CERT_MIN_DAYS=14 ELWASYS_ALERT_STATE_DIR="${S6b}")"
check "Exit 0 bei gültigem Zert" "0" "${rc}"
check "kein Cert-Alarm" "0" "$(notify_count)"

echo "== Szenario 7: Plattenplatz über Schwellwert -> ALARM =="
S7="${WORK}/s7"; : > "${NOTIFY_LOG}"
rc="$(run_alert HTTP_CODE=200 DISK_PCT=95 \
      ELWASYS_ALERT_DISK_PATHS=/data ELWASYS_ALERT_DISK_MAX_PERCENT=90 \
      ELWASYS_ALERT_STATE_DIR="${S7}")"
check "Exit 1 bei vollem Datenträger" "1" "${rc}"
check "Disk-Alarm ausgelöst" "[elwasys] ALARM: disk_data" "$(last_notify)"

echo "== Szenario 8: anhaltender FAIL mit Renotify-Fenster -> zweiter Alarm =="
S8="${WORK}/s8"; : > "${NOTIFY_LOG}"
rc="$(run_alert HTTP_CODE=503 ELWASYS_ALERT_STATE_DIR="${S8}" \
      ELWASYS_ALERT_RENOTIFY_SECONDS=100 ELWASYS_ALERT_NOW=1000)"
check "erster Alarm (t=1000)" "1" "$(notify_count)"
rc="$(run_alert HTTP_CODE=503 ELWASYS_ALERT_STATE_DIR="${S8}" \
      ELWASYS_ALERT_RENOTIFY_SECONDS=100 ELWASYS_ALERT_NOW=1200)"
check "Renotify nach Ablauf des Fensters (t=1200)" "2" "$(notify_count)"
check "Betreff ist die Wiederholungs-Erinnerung" "[elwasys] ALARM (weiterhin): health" "$(last_notify)"

echo
if [[ "${FAIL}" == "0" ]]; then
    echo "ALLE SELBSTTEST-CHECKS PASS."
    exit 0
else
    echo "${FAIL} SELBSTTEST-CHECK(S) FEHLGESCHLAGEN."
    exit 1
fi
