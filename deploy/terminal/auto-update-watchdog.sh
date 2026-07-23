#!/bin/bash
# Auto-Update mit Rollback fuer Raspi-Terminals (Phase 6 AP5, siehe
# docs/kb/05-migration-plan.md "Phase 6 - Produktivumschaltung", Roadmap-Punkt
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
# in docs/kb/05-migration-plan.md.
#
# QA-Nacharbeiten (Phase-6-Review, siehe docs/kb/05 Aenderungslog "Phase 6 QA-Nacharbeiten"):
#   - B1 (Blocker behoben): ein blosser Fetch-/Download-Fehlschlag von update.sh
#     (Netz/Tag/Platte - VOR jedem Symlink-Wechsel) wird jetzt von einem echten
#     fehlgeschlagenen Deploy unterschieden. Nur wenn update.sh den latest-Symlink
#     tatsaechlich umgehaengt hat, der Marker danach aber nicht vorrueckt, wird
#     zurueckgerollt + java beendet. Ein reiner No-op-Fehlschlag (latest
#     unveraendert) wird nur gewarnt + non-zero beendet - kein Symlink-Anfassen,
#     kein java-Kill; der naechste Cron-Lauf versucht den Fetch erneut.
#   - M1 (Major behoben): zusaetzliches Leerlauf-Gate VOR dem eigentlichen Update.
#     Ist ein Geraet des Standorts belegt (Backend GET .../devices/overview,
#     "occupied":true) ODER wurde der Readiness-Marker sehr kuerzlich aktualisiert
#     (kuerzliche Bedienung), wird das Update auf den naechsten Cron-Lauf
#     verschoben - KEIN java-Kill. Jede Unsicherheit (Backend nicht erreichbar,
#     kein Token konfiguriert) faellt fail-safe auf "beschaeftigt" zurueck. Das
#     deckt laufende Ausfuehrungen und die Zeit unmittelbar nach einer Bedienung
#     ab; die kurze Login-/Programmwahl-Phase VOR dem Start einer Ausfuehrung
#     bleibt ein dokumentiertes Restrisiko (siehe deploy/terminal/README.md).
set -euo pipefail

# ==============================================================================
# Konfiguration (per Env ueberschreibbar - u.a. fuer die Trocken-Tests)
# ==============================================================================

# Installationswurzel des Terminals (setup.sh: /opt/elwasys).
ELWA_ROOT="${ELWA_ROOT:-/opt/elwasys}"

# GitHub-Repo/Host wie in setup.sh install_elwasys / update.sh (kanonisch: ullriti/elwasys,
# Issue #64).
ELWA_GITHUB_REPO="${ELWA_GITHUB_REPO:-ullriti/elwasys}"

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

# --- Leerlauf-Gate (M1, siehe deploy/terminal/README.md) --------------------

# 1 (Default) = Leerlauf-Gate aktiv (fail-safe: jede Unsicherheit -> verschieben,
# nie durchlassen). 0 = Gate abschalten (altes Verhalten: nur der JVM-laeuft-Check
# oben) - z.B. fuer erzwungene Updates oder wenn kein Backend-Zugriff moeglich ist.
ELWA_IDLE_GATE_ENABLED="${ELWA_IDLE_GATE_ENABLED:-1}"

# Kommando, dessen STDOUT die anonyme Geraete-Uebersicht des Standorts als JSON
# liefert (Backend GET /api/v1/devices/overview, Standort-Token). Leer (Default)
# => eingebaute curl-Abfrage gegen backend.url/backend.token aus
# ${ELWA_ROOT}/elwasys.properties. Exit != 0 ODER leere Ausgabe zaehlt als
# "Status nicht ermittelbar" (faellt fail-safe auf "beschaeftigt" zurueck). Fuer
# Trocken-Tests ueberschreibbar - am robustesten mit einer Fixture-DATEI statt
# einem Inline-JSON-Literal (rohe "{"/","/"[" wuerden von der Bash-Klammer-
# Expansion in "eval" sonst leicht verstuemmelt), z.B.
# 'cat /pfad/zu/fixture-occupied.json' oder schlicht 'false' (nicht ermittelbar).
ELWA_DEVICE_OVERVIEW_CMD="${ELWA_DEVICE_OVERVIEW_CMD:-}"

# Timeout (Sekunden) fuer die eingebaute curl-Abfrage der Geraete-Uebersicht.
ELWA_DEVICE_OVERVIEW_TIMEOUT="${ELWA_DEVICE_OVERVIEW_TIMEOUT:-5}"

# Marker-mtime juenger als dieser Wert (Sekunden) gilt als "gerade eben bedient"
# und wird - zusaetzlich zum Belegt-Status - als Leerlauf-Gate-Signal gewertet
# (Default an sessionTimeout in elwasys.properties angelehnt).
ELWA_RECENT_INTERACTION_SECONDS="${ELWA_RECENT_INTERACTION_SECONDS:-60}"

LATEST_LINK="raspi-client.latest.jar"
PREVIOUS_LINK="raspi-client.previous.jar"
LOCK_DIR="${ELWA_ROOT}/.watchdog.lock"
LOG_DIR="${ELWA_ROOT}/log"
LOG_FILE="${LOG_DIR}/auto-update-watchdog.log"
CONFIG_FILE="${ELWA_ROOT}/elwasys.properties"
CONFIG_SNAPSHOT="${ELWA_ROOT}/elwasys.properties.previous"
# Optionaler ops/backend-getriggerter Override der Ziel-Version.
UPDATE_TARGET_FILE="${ELWA_ROOT}/.update-target"
# Persistierte, zuletzt GESCHEITERTE Ziel-Version (Issue #34): verhindert die Endlosschleife
# Update->Rollback->Update... bei einem kaputten Release. Enthält genau eine Versionszeile.
UPDATE_FAILED_FILE="${ELWA_ROOT}/.update-failed"

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

# Liest einen einzelnen Wert aus elwasys.properties (Format "key: value" bzw.
# "key=value", wie von Client-Raspi/setup.sh geschrieben). $1 ist ein
# regex-tauglicher Schluessel (Punkte vorab escapen, z.B. 'backend\.url').
# Leer, wenn die Datei oder der Schluessel fehlt.
properties_get() {
    local key="$1"
    [[ -f "${CONFIG_FILE}" ]] || return 0
    sed -n "s/^[[:space:]]*${key}[[:space:]]*[:=][[:space:]]*//p" "${CONFIG_FILE}" 2>/dev/null \
        | head -n1 | tr -d '\r'
}

# Liefert die anonyme Geraete-Uebersicht des Standorts als JSON auf stdout
# (siehe ELWA_DEVICE_OVERVIEW_CMD oben). Leere Ausgabe/Exit != 0 bei jedem
# Fehler (kein Override, kein Token, kein curl, Netzfehler) - der Aufrufer
# behandelt das fail-safe als "nicht ermittelbar".
device_overview_json() {
    if [[ -n "${ELWA_DEVICE_OVERVIEW_CMD}" ]]; then
        # set -f (noglob) fuer den Override-Aufruf: robuste Zusatz-Absicherung
        # gegen Pfadnamen-Expansion, falls ein Override-Kommando doch einmal
        # ein unquotiertes Glob-Zeichen ausgibt (siehe Fixture-Datei-Empfehlung
        # oben - eval kann JSON-Klammern sonst per Bash-Klammer-Expansion
        # verstuemmeln, was "set -f" allein NICHT abdeckt).
        # (Exit-Code von eval bewusst in "rc" gemerkt, damit "set +f" ihn NICHT
        # ueberschreibt, bevor er per "return" nach aussen gereicht wird.)
        local rc
        set -f
        eval "${ELWA_DEVICE_OVERVIEW_CMD}" 2>/dev/null
        rc=$?
        set +f
        return "${rc}"
    fi
    local backend_url backend_token
    backend_url="$(properties_get 'backend\.url')"
    backend_token="$(properties_get 'backend\.token')"
    [[ -n "${backend_url}" && -n "${backend_token}" ]] || return 1
    curl --silent --fail --max-time "${ELWA_DEVICE_OVERVIEW_TIMEOUT}" \
        -H "Authorization: Bearer ${backend_token}" \
        "${backend_url%/}/api/v1/devices/overview" 2>/dev/null
}

# Fail-safe Leerlauf-Pruefung (M1): Exit 0 == "vermutlich beschaeftigt, Update
# verschieben". Exit 1 == "vermutlich frei, Update darf laufen". JEDE
# Unsicherheit (Backend/Netz nicht erreichbar, kein Token konfiguriert, kaputte
# Antwort) faellt auf Exit 0 (beschaeftigt) zurueck - nie durchlassen im Zweifel.
terminal_is_busy() {
    local marker_ts marker_age
    marker_ts="$(marker_mtime)"
    if (( marker_ts > 0 )); then
        marker_age=$(( $(date +%s) - marker_ts ))
        if (( marker_age < ELWA_RECENT_INTERACTION_SECONDS )); then
            log "Leerlauf-Gate: Readiness-Marker ist erst ${marker_age}s alt (< ${ELWA_RECENT_INTERACTION_SECONDS}s) - werte als kuerzliche Bedienung."
            return 0
        fi
    fi

    local overview
    overview="$(device_overview_json)"
    if [[ -z "${overview}" ]]; then
        log "Leerlauf-Gate: Geraetestatus nicht ermittelbar (Backend/Netz/Token?) - werte vorsichtshalber als beschaeftigt."
        return 0
    fi
    if [[ "${overview}" == *'"occupied":true'* ]]; then
        log "Leerlauf-Gate: mindestens ein Geraet dieses Standorts ist aktuell belegt (occupied:true)."
        return 0
    fi
    return 1
}

# Persistiert die gescheiterte Ziel-Version (Issue #34) und raeumt so auf, dass ein erneuter
# Cron-Lauf denselben kaputten Versuch NICHT wiederholt:
#   1) ${UPDATE_FAILED_FILE} <- Version (der Guard am Laufanfang behandelt sie dann wie up-to-date),
#   2) das lokal gecachte, fehlgeschlagene raspi-client-<version>.jar loeschen (nur wenn es NICHT
#      das aktuell/vorherig verlinkte ist), damit ein unter DEMSELBEN Tag repariertes Asset spaeter
#      neu geladen wird (sonst wuerde update.sh das alte, kaputte Jar behalten),
#   3) einen etwaigen ops-Trigger (.update-target) konsumieren, damit er nicht erneut dieselbe
#      kaputte Version anstoesst.
mark_update_failed() {
    local ver="$1"
    echo "${ver}" > "${UPDATE_FAILED_FILE}" 2>/dev/null || true
    log "Ziel-Version ${ver} als GESCHEITERT markiert (${UPDATE_FAILED_FILE}) - kein erneuter Auto-Update-Versuch, bis eine andere Version erscheint oder die Datei entfernt wird."
    local failed_jar="raspi-client-${ver}.jar"
    local latest_now previous_now
    latest_now="$(link_target "${LATEST_LINK}")"
    previous_now="$(link_target "${PREVIOUS_LINK}")"
    if [[ "${failed_jar}" != "${latest_now}" && "${failed_jar}" != "${previous_now}" && -f "${failed_jar}" ]]; then
        log "Entferne lokal gecachtes fehlgeschlagenes Jar ${failed_jar} (ein repariertes Release-Asset wird dann neu geladen)."
        rm -f "${failed_jar}" 2>/dev/null || true
    fi
    if [[ -f "${UPDATE_TARGET_FILE}" ]]; then
        log "Konsumiere ops-Trigger ${UPDATE_TARGET_FILE} (kaputte Ziel-Version)."
        rm -f "${UPDATE_TARGET_FILE}" 2>/dev/null || true
    fi
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
# Zuvor gescheiterte Ziel-Version? -> wie up-to-date behandeln (Issue #34)
# ==============================================================================
# Nach einem Rollback wird die kaputte Ziel-Version in ${UPDATE_FAILED_FILE} festgehalten.
# Solange GitHub/ .update-target GENAU diese Version weiterhin als Ziel meldet, darf der
# Watchdog sie NICHT erneut ausrollen - sonst entsteht die Endlosschleife
# Update -> Start scheitert -> Rollback -> naechster Cron-Lauf -> Update ... Erscheint dagegen
# eine ANDERE Ziel-Version (repariertes Release ODER manuell entferntes .update-failed), gilt
# die Sperre nicht mehr und wird aufgehoben.
failed_version=""
if [[ -f "${UPDATE_FAILED_FILE}" ]]; then
    failed_version="$(head -n1 "${UPDATE_FAILED_FILE}" | tr -d '[:space:]')"
fi
if [[ -n "${failed_version}" && "${target_version}" != "${failed_version}" ]]; then
    log "Neue Ziel-Version ${target_version} weicht von der zuvor gescheiterten ${failed_version} ab - hebe die Fehlschlag-Sperre auf (entferne ${UPDATE_FAILED_FILE})."
    rm -f "${UPDATE_FAILED_FILE}" 2>/dev/null || true
    failed_version=""
fi
if [[ -n "${failed_version}" && "${target_version}" == "${failed_version}" ]]; then
    # Bewusst leise (Cron-freundlich): nur in die Logdatei. KEIN Kill, KEIN Update-Versuch.
    if [[ -d "${LOG_DIR}" ]]; then
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] Ziel-Version ${target_version} ist als GESCHEITERT markiert (${UPDATE_FAILED_FILE}) - kein erneuter Update-Versuch. Zum erneuten Versuch ein repariertes Release bereitstellen oder ${UPDATE_FAILED_FILE} entfernen." >> "${LOG_FILE}" 2>/dev/null || true
    fi
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
# Leerlauf-Gate (M1): beschaeftigtes Terminal nicht anfassen
# ==============================================================================
# Analog zum manuellen Cutover-Grundsatz ("die Terminal-Charge erst anfassen,
# wenn ihre Geraete frei sind", CUTOVER-RUNBOOK.md) - hier fuer den
# wiederkehrenden, unbeaufsichtigten Cron-Lauf: laeuft am Standort ein Geraet
# ODER wurde der Terminal-Marker sehr kuerzlich aktualisiert, wird das Update
# auf den naechsten Cron-Lauf verschoben. KEIN java-Kill in diesem Fall.
if [[ "${ELWA_IDLE_GATE_ENABLED}" == "1" ]] && terminal_is_busy; then
    log "Update auf ${target_version} verschoben (Leerlauf-Gate) - naechster Cron-Lauf versucht es erneut."
    exit 0
fi

# ==============================================================================
# Update mit Verifikation
# ==============================================================================

# 1) Konfig-Snapshot (fuer die "+ Konfiguration"-Formulierung der Roadmap;
#    update.sh aendert die Konfig zwar nicht, Snapshot dennoch fuer Vollstaendigkeit
#    und als Rollback-Absicherung).
#    T1 (QA-Review): update.sh ruehrt elwasys.properties heute nachweislich nicht an
#    (siehe update.sh-Header) - der Restore-Zweig unten ist deshalb aktuell
#    unerreichbarer, aber bewusst belassener Vorwaerts-Schutz fuer den Fall, dass
#    ein kuenftiges update.sh die Konfig doch einmal beruehrt.
if [[ -f "${CONFIG_FILE}" ]]; then
    cp -f "${CONFIG_FILE}" "${CONFIG_SNAPSHOT}" 2>/dev/null || true
fi

# 2) Neustart-Epoche + aktuellen Marker-mtime merken.
restart_epoch="$(date +%s)"
marker_before="$(marker_mtime)"
log "Starte Update auf ${target_version} (restart_epoch=${restart_epoch}, marker_before=${marker_before})."

# 3) update.sh aufrufen (rotiert latest/previous + Neustart-Trigger). Overrides
#    durchreichen.
update_ok=1
# Exit-Code von update.sh bewusst merken (set -e temporaer aus), um Fehlerklassen zu trennen.
set +e
ELWA_ROOT="${ELWA_ROOT}" \
   ELWA_GITHUB_REPO="${ELWA_GITHUB_REPO}" \
   ELWA_RESTART_CMD="${ELWA_RESTART_CMD}" \
   ELWA_JAVA_PGREP="${ELWA_JAVA_PGREP}" \
   bash "${ELWA_UPDATE_SCRIPT}" --version "${target_version}"
update_rc=$?
set -e
if (( update_rc == 0 )); then
    update_ok=0
elif (( update_rc == 3 )); then
    # Exit 3 (Issue #63): update.sh hat den Symlink zwar umgehaengt, konnte aber den Neustart
    # NICHT ausloesen (fehlende sudoers-Rechte fuer 'killall java'). Der alte java-Prozess laeuft
    # unveraendert weiter - das Terminal ist funktional weiterhin auf der bisherigen Version. Ein
    # kill-basierter Rollback wuerde am selben Rechteproblem scheitern; deshalb NUR den Symlink
    # konsistent zuruecksetzen (latest -> bisheriges Jar), die Ziel-Version als gescheitert
    # markieren und OHNE java-Kill beenden. Das verhindert den grundlosen Rollback UND (via
    # mark_update_failed) die Endlosschleife (Issue #34).
    alert "FEHLER: update.sh --version ${target_version} konnte den Neustart nicht ausloesen (Rechte/sudoers?). Setze ${LATEST_LINK} zurueck auf ${current_jar}, markiere ${target_version} als gescheitert (KEIN java-Kill)."
    ln -sfn "${current_jar}" "${LATEST_LINK}"
    mark_update_failed "${target_version}"
    exit 2
else
    # B1-Fix: ein Fehlschlag von update.sh ist NICHT automatisch ein fehlgeschlagenes
    # Deploy - update.sh kann auch VOR jedem Symlink-Wechsel scheitern (Download/Netz/
    # Tag/Platte/Integritaet, siehe update.sh "Neues Jar bereitstellen"). Unterscheiden anhand
    # des latest-Symlinks: hat sich sein Ziel NICHT geaendert, wurde nie etwas ausgerollt -
    # dann NICHT in den Rollback-/java-Kill-Pfad, sondern nur warnen + non-zero beenden,
    # der naechste Cron-Lauf versucht den Fetch erneut.
    latest_after_attempt="$(link_target "${LATEST_LINK}")"
    if [[ "${latest_after_attempt}" == "${current_jar}" ]]; then
        alert "WARNUNG: update.sh --version ${target_version} ist fehlgeschlagen, OHNE ${LATEST_LINK} zu aendern (weiterhin ${current_jar} - vermutlich Download-/Netzwerk-/Integritaetsfehler vor jedem Symlink-Wechsel). KEIN Rollback, KEIN java-Neustart - naechster Cron-Lauf versucht den Fetch erneut."
        exit 1
    else
        alert "FEHLER: update.sh --version ${target_version} ist fehlgeschlagen, NACHDEM ${LATEST_LINK} bereits auf ${latest_after_attempt} umgehaengt wurde (ungewoehnlich) - behandle wie einen fehlgeschlagenen Start, leite Rollback ein."
    fi
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

# a2) Gescheiterte Ziel-Version persistieren (Issue #34): verhindert, dass der naechste
#     Cron-Lauf dieselbe kaputte Version erneut ausrollt (Endlosschleife). Loescht zugleich das
#     lokal gecachte, fehlgeschlagene Jar und konsumiert einen etwaigen .update-target-Trigger.
mark_update_failed "${target_version}"

# b) Konfig aus Snapshot zurueckspielen, falls sie sich geaendert hat. (T1: in der
#    Praxis aktuell immer unveraendert, siehe Kommentar beim Snapshot oben.)
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
    # Kill-Exit-Code pruefen (Issue #63): scheitert der Kill an fehlenden Rechten (sudoers), kann
    # der Supervisor die vorige Version nicht neu starten - das explizit als Rechteproblem melden,
    # statt es stumm zu verschlucken. Der Recovery-Marker-Check unten stellt den Endzustand fest.
    rb_out="$(${ELWA_RESTART_CMD} 2>&1)"; rb_rc=$?
    if (( rb_rc != 0 )) && printf '%s' "${rb_out}" | grep -qiE 'sudo|passwo|not allowed|permission|operation not permitted'; then
        alert "FAILURE (KRITISCH): Rollback-Kill scheiterte an Rechten (sudoers?): ${rb_out}. Der Supervisor kann die vorige Version nicht automatisch neu starten - manueller Eingriff noetig."
    fi
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
