#!/bin/bash
# Auto-Update mit Rollback fuer Raspi-Terminals (Phase 6 AP5, siehe
# kb/05-migration-plan.md "Phase 6 - Produktivumschaltung", Roadmap-Punkt
# "Auto-Update mit Rollback").
#
# Warum: Ein im Feld laufendes Terminal soll sich selbsttaetig auf eine neue
# Client-Version heben UND bei einem fehlgeschlagenen Start automatisch auf die
# vorige Version zurueckfallen - ohne systemd, ohne grossen Java-Umbau. Dieses
# Skript ist bewusst die schlanke Shell-/Cron-Variante (Auftraggeber-Entscheidung
# 2026-07-22, "kein Overkill"): Es pollt periodisch (Cron) die Ziel-Version,
# ruft bei Bedarf das bestehende update.sh (AP4) auf und verifiziert den Start
# ueber den vom Client geschriebenen Readiness-Marker.
#
# Zusammenspiel mit dem Client (Phase 6 AP5):
#   Der JavaFX-Client schreibt beim Erreichen des bedienbereiten Zustands
#   SELECT_DEVICE (u.a. beim frischen Start STARTUP->SELECT_DEVICE) eine
#   Marker-Datei mit frischem mtime (Klasse TerminalReadinessMarker, Default
#   ${ELWA_ROOT}/.terminal-ready). Ein mtime-Fortschritt NACH einem Update ist der
#   Beweis, dass die neue Version tatsaechlich hochgekommen ist. Bleibt er aus,
#   rollt dieses Skript zurueck.
#
# Supervisor-/Jar-Layout-Vertrag (aus AP3/AP4, siehe deploy/terminal/README.md):
#   raspi-client-<version>.jar   versionierte Jars, bleiben liegen
#   raspi-client.latest.jar      Symlink -> aktuell laufende Version
#   raspi-client.previous.jar    Symlink -> vorige Version (Rollback-Ziel)
#   Neustart == laufenden java-Prozess beenden; die run.sh-Loop relauncht das
#   dann aktuell verlinkte Jar. Rollback == latest zurueck auf previous + java
#   killen.
#
# HINWEIS: Echte Rollouts (GitHub-Download, sudo killall) wurden in dieser
# Umgebung NICHT ausgefuehrt, nur trocken verifiziert (Temp-ELWA_ROOT, Fake-Jars,
# Fake-java, Fake-Version-Cmd, kurze Deadline). Siehe Aenderungslog "Phase 6 AP5"
# in kb/05-migration-plan.md.
set -euo pipefail

# ==============================================================================
# Konfiguration (per Env ueberschreibbar - u.a. fuer die Trocken-Tests)
# ==============================================================================

# Installationswurzel des Terminals (setup.sh: /opt/elwasys).
ELWA_ROOT="${ELWA_ROOT:-/opt/elwasys}"

# GitHub-Repo/Host wie in setup.sh install_elwasys / update.sh.
ELWA_GITHUB_REPO="${ELWA_GITHUB_REPO:-kabieror/elwasys}"

# Kommando, das den laufenden java-Prozess beendet (Neustart-Trigger). Wird auch
# an update.sh durchgereicht.
ELWA_RESTART_CMD="${ELWA_RESTART_CMD:-sudo killall java}"

# Kommando, das prueft, ob eine JVM laeuft (Exit 0 == java laeuft). Wird auch an
# update.sh durchgereicht.
ELWA_JAVA_PGREP="${ELWA_JAVA_PGREP:-pgrep -x java}"

# Kommando, das die Ziel-Version (GitHub-Release "latest") ermittelt. Ausgabe:
# der Release-Tag auf stdout. Default: dasselbe Muster wie setup.sh
# (curl -qsI .../releases/latest, Tag aus der location:-Zeile). Fuer Trocken-Tests
# ueberschreibbar (z.B. 'echo 1.5.0').
ELWA_LATEST_VERSION_CMD="${ELWA_LATEST_VERSION_CMD:-curl --silent -qI https://github.com/${ELWA_GITHUB_REPO}/releases/latest | awk -F '/' '/^location/ {print substr(\$NF, 1, length(\$NF)-1)}'}"

# Sekunden, die auf den bedienbereiten Marker der NEUEN Version gewartet wird,
# bevor ein Update als fehlgeschlagen gilt und zurueckgerollt wird.
ELWA_UPDATE_DEADLINE="${ELWA_UPDATE_DEADLINE:-180}"

# Pfad zur Readiness-Marker-Datei (muss zum Client-Default bzw. der Property
# elwasys.readyMarkerFile passen).
ELWA_MARKER_FILE="${ELWA_MARKER_FILE:-${ELWA_ROOT}/.terminal-ready}"

# Pfad zu update.sh (Default: neben diesem Skript).
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ELWA_UPDATE_SCRIPT="${ELWA_UPDATE_SCRIPT:-${SCRIPT_DIR}/update.sh}"

LATEST_LINK="raspi-client.latest.jar"
PREVIOUS_LINK="raspi-client.previous.jar"
LOCK_DIR="${ELWA_ROOT}/.watchdog.lock"
LOG_DIR="${ELWA_ROOT}/log"
LOG_FILE="${LOG_DIR}/auto-update-watchdog.log"
CONFIG_FILE="${ELWA_ROOT}/elwasys.properties"
CONFIG_SNAPSHOT="${ELWA_ROOT}/elwasys.properties.previous"
# Optionaler ops/backend-getriggerter Override der Ziel-Version.
UPDATE_TARGET_FILE="${ELWA_ROOT}/.update-target"

# ==============================================================================
# Hilfsfunktionen
# ==============================================================================

# Zeitgestempelte Logzeile auf stdout UND (best effort) in die Logdatei.
log() {
    local ts
    ts="$(date '+%Y-%m-%d %H:%M:%S')"
    echo "[${ts}] $*"
    if [[ -d "${LOG_DIR}" ]]; then
        echo "[${ts}] $*" >> "${LOG_FILE}" 2>/dev/null || true
    fi
}

# Fehler/Alarm auf stderr UND in die Logdatei.
alert() {
    local ts
    ts="$(date '+%Y-%m-%d %H:%M:%S')"
    echo "[${ts}] $*" >&2
    if [[ -d "${LOG_DIR}" ]]; then
        echo "[${ts}] $*" >> "${LOG_FILE}" 2>/dev/null || true
    fi
}

# Version aus dem Basename eines latest-Symlink-Ziels ableiten:
#   raspi-client-<ver>.jar -> <ver>
version_from_jar() {
    local base="$1"
    base="${base#raspi-client-}"
    base="${base%.jar}"
    echo "${base}"
}

# Aktuelles Ziel-Basename eines Symlinks (leer, wenn nicht vorhanden).
link_target() {
    local link="$1"
    if [[ -L "${link}" ]]; then
        basename "$(readlink "${link}")"
    else
        echo ""
    fi
}

# mtime der Marker-Datei in Epoch-Sekunden (0, wenn nicht vorhanden).
marker_mtime() {
    if [[ -f "${ELWA_MARKER_FILE}" ]]; then
        stat -c %Y "${ELWA_MARKER_FILE}" 2>/dev/null || echo 0
    else
        echo 0
    fi
}

# Wartet bis zu ELWA_UPDATE_DEADLINE Sekunden darauf, dass der Marker-mtime den
# Wert $1 (eine Epoch-Sekunde) UEBERSCHREITET. Exit 0 bei Erfolg, sonst 1.
wait_for_marker_after() {
    local since="$1"
    local waited=0
    while (( waited < ELWA_UPDATE_DEADLINE )); do
        if (( "$(marker_mtime)" > since )); then
            return 0
        fi
        sleep 1
        waited=$(( waited + 1 ))
    done
    return 1
}

# Lockfile-Cleanup (via trap).
cleanup_lock() {
    rmdir "${LOCK_DIR}" 2>/dev/null || true
}

# ==============================================================================
# Vorbereitung
# ==============================================================================

[[ -d "${ELWA_ROOT}" ]] || { echo "FEHLER: ELWA_ROOT '${ELWA_ROOT}' existiert nicht." >&2; exit 1; }
mkdir -p "${LOG_DIR}" 2>/dev/null || true
cd "${ELWA_ROOT}"

# --- Lockfile gegen parallele Cron-Laeufe (atomar via mkdir) -----------------
if ! mkdir "${LOCK_DIR}" 2>/dev/null; then
    log "Ein anderer Watchdog-Lauf haelt bereits das Lock (${LOCK_DIR}) - beende (No-op)."
    exit 0
fi
trap cleanup_lock EXIT

# ==============================================================================
# Aktuelle Version aus dem latest-Symlink
# ==============================================================================

current_jar="$(link_target "${LATEST_LINK}")"
if [[ -z "${current_jar}" ]]; then
    alert "FEHLER: kein ${LATEST_LINK} in ${ELWA_ROOT} - Terminal nicht (korrekt) provisioniert. Kein Auto-Update."
    exit 1
fi
current_version="$(version_from_jar "${current_jar}")"

# ==============================================================================
# Ziel-Version ermitteln
# ==============================================================================
# Vorrang: ops/backend-getriggerter Override in ${ELWA_ROOT}/.update-target (so
# kann der Betrieb ein Update anstossen, ohne Backend-Code zu aendern). Sonst der
# GitHub-Poll (ELWA_LATEST_VERSION_CMD).

target_version=""
if [[ -f "${UPDATE_TARGET_FILE}" ]]; then
    target_version="$(head -n1 "${UPDATE_TARGET_FILE}" | tr -d '[:space:]')"
    if [[ -n "${target_version}" ]]; then
        log "Ziel-Version aus ${UPDATE_TARGET_FILE} (ops/backend-Override): ${target_version}"
    fi
fi
if [[ -z "${target_version}" ]]; then
    target_version="$(eval "${ELWA_LATEST_VERSION_CMD}" 2>/dev/null | head -n1 | tr -d '[:space:]' || true)"
fi

if [[ -z "${target_version}" ]]; then
    alert "WARNUNG: Ziel-Version konnte nicht ermittelt werden (Netz? GitHub?). Kein Update, kein Rollback."
    exit 0
fi

# ==============================================================================
# Up-to-date? -> stiller No-op
# ==============================================================================

if [[ "${target_version}" == "${current_version}" ]]; then
    # Bewusst leise (Cron-freundlich): nur in die Logdatei, nicht auf stdout.
    if [[ -d "${LOG_DIR}" ]]; then
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] Up-to-date (${current_version}) - kein Update noetig." >> "${LOG_FILE}" 2>/dev/null || true
    fi
    exit 0
fi

log "Neue Ziel-Version erkannt: aktuell=${current_version} -> ziel=${target_version}."

# ==============================================================================
# Laeuft ueberhaupt eine JVM? (Terminal aus -> kein Update erzwingen)
# ==============================================================================

if ! ${ELWA_JAVA_PGREP} > /dev/null 2>&1; then
    log "Kein laufender java-Prozess (Terminal evtl. aus) - verschiebe das Update auf einen spaeteren Lauf (kein erzwungener Start)."
    exit 0
fi

# ==============================================================================
# Update mit Verifikation
# ==============================================================================

# 1) Konfig-Snapshot (fuer die "+ Konfiguration"-Formulierung der Roadmap;
#    update.sh aendert die Konfig zwar nicht, Snapshot dennoch fuer Vollstaendigkeit
#    und als Rollback-Absicherung).
if [[ -f "${CONFIG_FILE}" ]]; then
    cp -f "${CONFIG_FILE}" "${CONFIG_SNAPSHOT}" 2>/dev/null || true
fi

# 2) Neustart-Epoche + aktuellen Marker-mtime merken.
restart_epoch="$(date +%s)"
marker_before="$(marker_mtime)"
log "Starte Update auf ${target_version} (restart_epoch=${restart_epoch}, marker_before=${marker_before})."

# 3) update.sh aufrufen (rotiert latest/previous + Neustart-Trigger). Overrides
#    durchreichen. Schlaegt update.sh selbst fehl, gilt das als Fehlschlag.
update_ok=1
if ELWA_ROOT="${ELWA_ROOT}" \
   ELWA_GITHUB_REPO="${ELWA_GITHUB_REPO}" \
   ELWA_RESTART_CMD="${ELWA_RESTART_CMD}" \
   ELWA_JAVA_PGREP="${ELWA_JAVA_PGREP}" \
   bash "${ELWA_UPDATE_SCRIPT}" --version "${target_version}"; then
    update_ok=0
else
    alert "FEHLER: update.sh --version ${target_version} ist fehlgeschlagen - leite Rollback ein."
fi

# 4) Verifizierter Start: auf einen frischen Marker-mtime (> restart_epoch) warten.
verified=1
if (( update_ok == 0 )); then
    log "Warte bis zu ${ELWA_UPDATE_DEADLINE}s darauf, dass ${target_version} SELECT_DEVICE erreicht (Readiness-Marker)."
    if wait_for_marker_after "${restart_epoch}"; then
        verified=0
    fi
fi

if (( verified == 0 )); then
    # 5) Erfolg.
    log "ERFOLG: ${target_version} ist bedienbereit (Marker-mtime > restart_epoch). Update abgeschlossen."
    rm -f "${CONFIG_SNAPSHOT}" 2>/dev/null || true
    log "  ${LATEST_LINK}   -> $(link_target "${LATEST_LINK}")"
    log "  ${PREVIOUS_LINK} -> $(link_target "${PREVIOUS_LINK}")"
    # Optionalen Trigger konsumieren, damit er nicht erneut feuert.
    if [[ -f "${UPDATE_TARGET_FILE}" ]]; then
        rm -f "${UPDATE_TARGET_FILE}" 2>/dev/null || true
    fi
    exit 0
fi

# ==============================================================================
# ROLLBACK (Deadline ueberschritten oder update.sh fehlgeschlagen)
# ==============================================================================

alert "FAILURE: ${target_version} wurde binnen ${ELWA_UPDATE_DEADLINE}s NICHT bedienbereit - ROLLBACK auf die vorige Version."

previous_jar="$(link_target "${PREVIOUS_LINK}")"
if [[ -z "${previous_jar}" ]]; then
    alert "FAILURE: kein ${PREVIOUS_LINK} vorhanden - Rollback nicht moeglich! Manueller Eingriff noetig."
    exit 2
fi

# a) latest zurueck auf das previous-Ziel haengen (Rollback des Jars).
log "Rollback: setze ${LATEST_LINK} zurueck -> ${previous_jar}"
ln -sfn "${previous_jar}" "${LATEST_LINK}"

# b) Konfig aus Snapshot zurueckspielen, falls sie sich geaendert hat.
if [[ -f "${CONFIG_SNAPSHOT}" ]]; then
    if [[ -f "${CONFIG_FILE}" ]] && cmp -s "${CONFIG_SNAPSHOT}" "${CONFIG_FILE}"; then
        : # unveraendert - nichts zu tun
    else
        log "Rollback: stelle ${CONFIG_FILE} aus dem Snapshot wieder her."
        cp -f "${CONFIG_SNAPSHOT}" "${CONFIG_FILE}" 2>/dev/null || true
    fi
fi

# c) java killen -> Supervisor relauncht die (jetzt wieder verlinkte) vorige Version.
rollback_epoch="$(date +%s)"
if ${ELWA_JAVA_PGREP} > /dev/null 2>&1; then
    log "Rollback: beende java-Prozess (Supervisor relauncht die vorige Version)."
    ${ELWA_RESTART_CMD} || true
else
    log "Rollback: kein java-Prozess aktiv - Supervisor startet beim naechsten Lauf die vorige Version."
fi

# d) Recovery bestaetigen: erneut kurz auf einen frischen Marker warten.
log "Warte bis zu ${ELWA_UPDATE_DEADLINE}s auf die Recovery der vorigen Version (Readiness-Marker)."
if wait_for_marker_after "${rollback_epoch}"; then
    alert "FAILURE (behoben): Rollback auf $(version_from_jar "${previous_jar}") erfolgreich - vorige Version wieder bedienbereit."
    exit 1
else
    alert "FAILURE (KRITISCH): Rollback ausgeloest, aber die vorige Version wurde binnen ${ELWA_UPDATE_DEADLINE}s NICHT bedienbereit! Manueller Eingriff noetig."
    exit 2
fi
