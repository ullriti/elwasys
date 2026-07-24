#!/bin/bash
# elwasys Betriebs-Alerting (Issue #83/H4, dazu Cert-Ablauf/Plattenplatz aus #89). Pollt die
# betriebliche Health-Gruppe des Backends und meldet Fehlerbilder an einen Menschen - der bisher
# im Repo/Runbook nur EMPFOHLENE, aber nirgends MITGELIEFERTE Alarmkanal (finale Review R5).
#
# Warum: Das Backend liefert unter /actuator/health/operational sauber HTTP 503 bei
# betrieblichen Fehlerbildern (Standort ohne verbundenes Terminal, offene abgelaufene
# Executions), und Backend/DB-Ausfall macht den Endpoint unerreichbar. Ohne einen Poller
# erreicht ein stiller 503 NIEMANDEN - für einen ehrenamtlichen Betreiber wird ein kleiner
# Vorfall so zum stillen Ausfall. Dieses Skript ist der verdrahtete Kanal: es läuft periodisch
# (systemd-Timer/Cron, siehe deploy/monitoring/README.md) und alarmiert per Pushover und/oder
# Mail.
#
# Abgedeckte Signale:
#   1. Betriebs-Health   /actuator/health/operational != HTTP 200 ODER nicht erreichbar
#                        (Backend down, DB down, Terminal-WS fehlt, abgelaufene Execution).
#   2. Zertifikats-Ablauf  (#89) TLS-Zert des öffentlichen Endpoints läuft in < N Tagen ab
#                        (Selbstverwaltungs-Pfad ohne Auto-Erneuerung → Totalausfall-Risiko).
#   3. Plattenplatz      (#89) Füllstand einer Partition über Schwellwert (langsames Volllaufen
#                        bliebe sonst unsichtbar).
#
# Anti-Spam: Zustandswechsel-basiert. Ein Alarm geht raus, wenn ein Check NEU in den Fehler-
# zustand kippt und wieder bei der Erholung; solange er fehlerhaft BLEIBT, wird nur alle
# ELWASYS_ALERT_RENOTIFY_SECONDS erneut erinnert (Default 6 h) - nicht bei jedem Poll.
#
# Konfiguration: per Env (systemd EnvironmentFile bzw. Cron-Wrapper), siehe
# elwasys-health-alert.env.example. Für den Offline-Selbsttest sind die drei Sonden und der
# Zustellkanal per *_CMD-Hook überschreibbar (siehe elwasys-health-alert-selftest.sh) - so
# läuft die volle Logik ohne Netz/Backend/Pushover.
set -uo pipefail

# ==============================================================================
# Konfiguration (per Env überschreibbar)
# ==============================================================================

# 1) Betriebs-Health-Endpoint. Die dedizierte Alerting-Gruppe (nur die betrieblichen
#    Indicators), NICHT /liveness (das ist nur Prozess-Status, siehe application.yml/Runbook 7b).
ELWASYS_ALERT_HEALTH_URL="${ELWASYS_ALERT_HEALTH_URL:-http://127.0.0.1:8080/actuator/health/operational}"
ELWASYS_ALERT_HTTP_TIMEOUT="${ELWASYS_ALERT_HTTP_TIMEOUT:-10}"

# 2) Zertifikats-Ablauf (#89). Leer = Check aus. Sonst "host" oder "host:port" des öffentlichen
#    HTTPS-Endpoints (z.B. elwasys.example.com bzw. elwasys.example.com:443).
ELWASYS_ALERT_TLS_HOST="${ELWASYS_ALERT_TLS_HOST:-}"
ELWASYS_ALERT_CERT_MIN_DAYS="${ELWASYS_ALERT_CERT_MIN_DAYS:-14}"

# 3) Plattenplatz (#89). Leerzeichen-getrennte Liste von Pfaden; leer = Check aus. Alarm, wenn
#    die Partition eines Pfads über ELWASYS_ALERT_DISK_MAX_PERCENT gefüllt ist.
ELWASYS_ALERT_DISK_PATHS="${ELWASYS_ALERT_DISK_PATHS:-}"
ELWASYS_ALERT_DISK_MAX_PERCENT="${ELWASYS_ALERT_DISK_MAX_PERCENT:-90}"

# Zustands-/Anti-Spam-Verzeichnis (überdauert die Läufe).
ELWASYS_ALERT_STATE_DIR="${ELWASYS_ALERT_STATE_DIR:-/var/lib/elwasys-health-alert}"
# Wiederholungs-Erinnerung, solange ein Check fehlerhaft BLEIBT (Sekunden; 0 = nie wiederholen,
# nur auf Wechsel alarmieren). Default 6 h, damit ein am Transitions-Moment verpasster Alarm
# (z.B. Pushover kurz down) nicht in dauerhaftes Schweigen mündet.
ELWASYS_ALERT_RENOTIFY_SECONDS="${ELWASYS_ALERT_RENOTIFY_SECONDS:-21600}"

# --- Zustellkanäle ---------------------------------------------------------------------------
# Pushover (dieselbe App-Token-Mechanik, die das Backend für Nutzer-Benachrichtigungen nutzt -
# hier ein EIGENER Token/User für Betriebs-Alarme, bewusst getrennt vom Nutzerversand).
ELWASYS_ALERT_PUSHOVER_TOKEN="${ELWASYS_ALERT_PUSHOVER_TOKEN:-}"
ELWASYS_ALERT_PUSHOVER_USER="${ELWASYS_ALERT_PUSHOVER_USER:-}"
# Mail: Empfänger + Versandkommando (Default 'mail -s'; Aufruf: <cmd> "<Betreff>" "<Empfänger>",
# Body über stdin). Leerer Empfänger = Mail-Kanal aus.
ELWASYS_ALERT_MAIL_TO="${ELWASYS_ALERT_MAIL_TO:-}"
ELWASYS_ALERT_MAIL_CMD="${ELWASYS_ALERT_MAIL_CMD:-mail -s}"

# --- Test-Hooks (nur für den Offline-Selbsttest; im Betrieb leer lassen) ----------------------
# Jeder Hook ersetzt die echte Sonde/Zustellung durch ein Kommando (eval).
ELWASYS_ALERT_HTTP_CMD="${ELWASYS_ALERT_HTTP_CMD:-}"       # Args: <url> -> gibt HTTP-Code aus
ELWASYS_ALERT_CERT_DAYS_CMD="${ELWASYS_ALERT_CERT_DAYS_CMD:-}"  # Args: <host:port> -> Resttage
ELWASYS_ALERT_DF_CMD="${ELWASYS_ALERT_DF_CMD:-}"          # Args: <pfad> -> Füllgrad in Prozent
ELWASYS_ALERT_NOTIFY_CMD="${ELWASYS_ALERT_NOTIFY_CMD:-}"  # Args: <betreff> <body>
ELWASYS_ALERT_NOW="${ELWASYS_ALERT_NOW:-}"                # Epoch-Sekunden (Zeit-Override)

# ==============================================================================
# Hilfsfunktionen
# ==============================================================================

now_epoch() {
    if [[ -n "${ELWASYS_ALERT_NOW}" ]]; then echo "${ELWASYS_ALERT_NOW}"; else date +%s; fi
}

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"; }

# HTTP-Status-Code einer URL (000 bei Nichterreichbarkeit = zählt als Alarm).
http_status() {
    if [[ -n "${ELWASYS_ALERT_HTTP_CMD}" ]]; then
        eval "${ELWASYS_ALERT_HTTP_CMD} $(printf '%q' "$1")"
        return
    fi
    local code
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time "${ELWASYS_ALERT_HTTP_TIMEOUT}" "$1" 2>/dev/null || true)"
    echo "${code:-000}"
}

# Resttage bis zum Zertifikatsablauf (leer bei Fehler = Check meldet "unklar", kein Fehlalarm).
cert_days_remaining() {
    local hostport="$1"
    if [[ -n "${ELWASYS_ALERT_CERT_DAYS_CMD}" ]]; then
        eval "${ELWASYS_ALERT_CERT_DAYS_CMD} $(printf '%q' "${hostport}")"
        return
    fi
    local host="${hostport%%:*}" port="${hostport##*:}"
    [[ "${port}" == "${host}" ]] && port=443
    local enddate end_epoch
    enddate="$(echo | openssl s_client -connect "${host}:${port}" -servername "${host}" 2>/dev/null \
        | openssl x509 -noout -enddate 2>/dev/null | sed 's/notAfter=//')"
    [[ -n "${enddate}" ]] || { echo ""; return; }
    end_epoch="$(date -d "${enddate}" +%s 2>/dev/null || echo '')"
    [[ -n "${end_epoch}" ]] || { echo ""; return; }
    echo $(( (end_epoch - "$(now_epoch)") / 86400 ))
}

# Füllgrad (Prozent, ohne %) der Partition eines Pfads; leer bei Fehler.
disk_percent() {
    local path="$1"
    if [[ -n "${ELWASYS_ALERT_DF_CMD}" ]]; then
        eval "${ELWASYS_ALERT_DF_CMD} $(printf '%q' "${path}")"
        return
    fi
    df -P "${path}" 2>/dev/null | awk 'NR==2 {gsub(/%/,"",$5); print $5}'
}

# Zustellung eines Alarms/Recovery an alle konfigurierten Kanäle.
notify() {
    local subject="$1" body="$2"
    if [[ -n "${ELWASYS_ALERT_NOTIFY_CMD}" ]]; then
        eval "${ELWASYS_ALERT_NOTIFY_CMD} $(printf '%q' "${subject}") $(printf '%q' "${body}")"
        return
    fi
    local delivered=0
    if [[ -n "${ELWASYS_ALERT_PUSHOVER_TOKEN}" && -n "${ELWASYS_ALERT_PUSHOVER_USER}" ]]; then
        if curl -s --max-time 10 \
                --form-string "token=${ELWASYS_ALERT_PUSHOVER_TOKEN}" \
                --form-string "user=${ELWASYS_ALERT_PUSHOVER_USER}" \
                --form-string "title=${subject}" \
                --form-string "message=${body}" \
                https://api.pushover.net/1/messages.json >/dev/null 2>&1; then
            delivered=1
        fi
    fi
    if [[ -n "${ELWASYS_ALERT_MAIL_TO}" ]]; then
        if printf '%s\n' "${body}" | ${ELWASYS_ALERT_MAIL_CMD} "${subject}" "${ELWASYS_ALERT_MAIL_TO}" >/dev/null 2>&1; then
            delivered=1
        fi
    fi
    if (( delivered == 0 )); then
        # Kein Kanal konfiguriert/erreichbar: der Alarm darf NICHT still verschwinden - auf
        # stderr (systemd-journal/Cron-Mail) ausgeben, damit er wenigstens dort landet.
        echo "ALARM (kein Kanal erreichbar): ${subject} - ${body}" >&2
    fi
}

# Zustandswechsel-basiertes Melden (Anti-Spam). $1 Key, $2 OK|FAIL, $3 Meldung.
# Alarmiert bei Wechsel nach FAIL und bei Erholung (FAIL->OK); wiederholt einen anhaltenden
# FAIL nur alle ELWASYS_ALERT_RENOTIFY_SECONDS.
process_check() {
    local key="$1" status="$2" message="$3"
    local statefile="${ELWASYS_ALERT_STATE_DIR}/${key}.state"
    local tsfile="${ELWASYS_ALERT_STATE_DIR}/${key}.ts"
    local prev="OK" last_ts=0 now
    now="$(now_epoch)"
    [[ -f "${statefile}" ]] && prev="$(cat "${statefile}" 2>/dev/null || echo OK)"
    [[ -f "${tsfile}" ]] && last_ts="$(cat "${tsfile}" 2>/dev/null || echo 0)"

    if [[ "${status}" == "FAIL" ]]; then
        if [[ "${prev}" != "FAIL" ]]; then
            notify "[elwasys] ALARM: ${key}" "${message}"
            echo "${now}" > "${tsfile}"
        elif (( ELWASYS_ALERT_RENOTIFY_SECONDS > 0 )) && (( now - last_ts >= ELWASYS_ALERT_RENOTIFY_SECONDS )); then
            notify "[elwasys] ALARM (weiterhin): ${key}" "${message}"
            echo "${now}" > "${tsfile}"
        fi
    else # OK
        if [[ "${prev}" == "FAIL" ]]; then
            notify "[elwasys] behoben: ${key}" "${message}"
        fi
        rm -f "${tsfile}" 2>/dev/null || true
    fi
    echo "${status}" > "${statefile}"
}

# ==============================================================================
# Ablauf
# ==============================================================================

mkdir -p "${ELWASYS_ALERT_STATE_DIR}" 2>/dev/null || true
FAIL_TOTAL=0

# --- 1) Betriebs-Health -----------------------------------------------------------------------
code="$(http_status "${ELWASYS_ALERT_HEALTH_URL}")"
if [[ "${code}" == "200" ]]; then
    process_check "health" "OK" "Betriebs-Health wieder UP (HTTP 200): ${ELWASYS_ALERT_HEALTH_URL}"
    log "health: OK (HTTP 200)"
else
    reason="HTTP ${code}"
    [[ "${code}" == "000" ]] && reason="nicht erreichbar (Backend/DB down?)"
    process_check "health" "FAIL" "Betriebs-Health FEHLERHAFT: ${reason} an ${ELWASYS_ALERT_HEALTH_URL}. Mögliche Ursachen: Backend/DB down, Terminal-WS getrennt, offene abgelaufene Execution. Details (angemeldet): /actuator/health."
    log "health: FAIL (${reason})"
    FAIL_TOTAL=$((FAIL_TOTAL + 1))
fi

# --- 2) Zertifikats-Ablauf (#89) --------------------------------------------------------------
if [[ -n "${ELWASYS_ALERT_TLS_HOST}" ]]; then
    days="$(cert_days_remaining "${ELWASYS_ALERT_TLS_HOST}")"
    if [[ -z "${days}" ]]; then
        log "cert: unklar (Zert/Verbindung nicht auswertbar für ${ELWASYS_ALERT_TLS_HOST}) - kein Alarm"
    elif (( days < ELWASYS_ALERT_CERT_MIN_DAYS )); then
        process_check "cert" "FAIL" "TLS-Zertifikat für ${ELWASYS_ALERT_TLS_HOST} läuft in ${days} Tagen ab (< ${ELWASYS_ALERT_CERT_MIN_DAYS}). Ohne Erneuerung folgt ein Totalausfall (TLS-Pflicht, auch der Terminals)."
        log "cert: FAIL (${days} Tage)"
        FAIL_TOTAL=$((FAIL_TOTAL + 1))
    else
        process_check "cert" "OK" "TLS-Zertifikat für ${ELWASYS_ALERT_TLS_HOST} wieder gültig (${days} Tage Restlaufzeit)."
        log "cert: OK (${days} Tage)"
    fi
fi

# --- 3) Plattenplatz (#89) --------------------------------------------------------------------
if [[ -n "${ELWASYS_ALERT_DISK_PATHS}" ]]; then
    for path in ${ELWASYS_ALERT_DISK_PATHS}; do
        pct="$(disk_percent "${path}")"
        # Stabiler, dateinamen-tauglicher State-Key je Pfad: führende/abschließende Slashes weg,
        # restliche Nicht-Alnum -> '_' (printf statt echo, sonst schleppte ein Newline mit).
        key="disk_$(printf '%s' "${path}" | sed 's#^/##; s#/$##' | tr -c 'a-zA-Z0-9' '_')"
        if [[ -z "${pct}" ]]; then
            log "disk ${path}: unklar (df ohne Ergebnis) - kein Alarm"
        elif (( pct > ELWASYS_ALERT_DISK_MAX_PERCENT )); then
            process_check "${key}" "FAIL" "Partition von ${path} ist zu ${pct}% gefüllt (> ${ELWASYS_ALERT_DISK_MAX_PERCENT}%). Aufräumen/erweitern, bevor Backups oder die DB vollaufen."
            log "disk ${path}: FAIL (${pct}%)"
            FAIL_TOTAL=$((FAIL_TOTAL + 1))
        else
            process_check "${key}" "OK" "Partition von ${path} wieder unter Schwellwert (${pct}%)."
            log "disk ${path}: OK (${pct}%)"
        fi
    done
fi

# Exit-Code spiegelt den Gesamtzustand (systemd/Cron-Log/journal): 0 = alles OK, 1 = mind. ein
# fehlerhafter Check. Die eigentliche Menschen-Benachrichtigung ist bereits über notify() raus.
if (( FAIL_TOTAL > 0 )); then
    exit 1
fi
exit 0
