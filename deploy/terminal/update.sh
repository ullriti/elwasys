#!/bin/bash
# Client-Jar-Update fuer BESTEHENDE Raspi-Terminals (Phase 6 AP4, siehe
# docs/kb/05-migration-plan.md "Phase 6 - Produktivumschaltung").
#
# Warum: Ein bereits provisioniertes Terminal (aus setup.sh) soll auf ein neues
# Client-fat-jar gehoben werden, OHNE das interaktive setup.sh erneut zu fahren -
# elwasys.properties, logback.xml, run.sh und ~/.xsession bleiben unangetastet.
# Dieses Skript legt nur das neue Jar ab, haengt die Symlinks um und stoesst den
# Neustart gemaess Supervisor-Vertrag an.
#
# Supervisor-Vertrag (aus Phase 6 AP3, siehe deploy/terminal/README.md und der
# von setup.sh generierte run.sh-Loop): Das laufende run.sh ist eine Endlosschleife,
# die pro Iteration das per Symlink "raspi-client.latest.jar" referenzierte Jar
# NEU aufloest und startet. Ein externer Neustart == den laufenden java-Prozess
# beenden (Default: "sudo killall java"); die Loop relauncht dann automatisch das
# jetzt verlinkte (neue) Jar. "killall java" trifft nur die JVM, nicht den
# bash-Supervisor.
#
# Jar-Layout-Konvention (hier etabliert, AP5-Rollback baut darauf auf):
#   raspi-client-<version>.jar   versionierte Jars, bleiben liegen (kein Loeschen)
#   raspi-client.latest.jar      Symlink -> aktuell laufende Version
#   raspi-client.previous.jar    Symlink -> zuvor laufende Version (Rollback-Ziel)
# Update-Reihenfolge (atomar per "ln -sfn" auf relative Basenames):
#   1) bisheriges latest-Ziel als previous merken (Symlink umhaengen)
#   2) latest auf das neue Jar zeigen
#   3) Neustart ausloesen (java-Prozess beenden -> Loop startet neues Jar)
# AP5-Rollback braucht dann nur: latest zurueck auf das previous-Ziel + java killen.
#
# Bezug des neuen Jars:
#   --version <tag>       laedt raspi-client-<tag>.jar von der GitHub-Release-URL
#                         (gleiches Muster/Host wie setup.sh install_elwasys)
#   --jar <lokaler Pfad>  nutzt ein bereits vorliegendes Jar (Offline-Rollout)
#
# HINWEIS: Der GitHub-Download (--version) laeuft real nur auf dem Geraet bzw.
# gegen github.com. In der Projekt-Sandbox wurde nur der Offline-Pfad (--jar)
# tatsaechlich ausgefuehrt; die Download-URL-Konstruktion ist trocken verifiziert.
set -euo pipefail

# ==============================================================================
# Konfiguration (per Env ueberschreibbar - u.a. fuer die Trocken-Tests)
# ==============================================================================

# Installationswurzel des Terminals (setup.sh: /opt/elwasys). Fuer lokale Tests
# auf ein Temp-Verzeichnis setzbar.
ELWA_ROOT="${ELWA_ROOT:-/opt/elwasys}"

# GitHub-Repo/Host wie in setup.sh install_elwasys.
ELWA_GITHUB_REPO="${ELWA_GITHUB_REPO:-kabieror/elwasys}"

# Kommando, das den laufenden java-Prozess (die JVM unter dem run.sh-Supervisor)
# beendet - so relauncht die Loop das neu verlinkte Jar. Fuer Trocken-Tests ohne
# echtes sudo/java ueberschreibbar.
ELWA_RESTART_CMD="${ELWA_RESTART_CMD:-sudo killall java}"

# Kommando, das prueft, ob (unter dem Supervisor) ueberhaupt eine JVM laeuft.
# Exit 0 == java laeuft. Fuer Trocken-Tests ueberschreibbar.
ELWA_JAVA_PGREP="${ELWA_JAVA_PGREP:-pgrep -x java}"

LATEST_LINK="raspi-client.latest.jar"
PREVIOUS_LINK="raspi-client.previous.jar"

# ==============================================================================
# Hilfsfunktionen
# ==============================================================================

log_state() {
    local cyan='\033[0;36m' reset='\033[0m'
    echo -e "\n${cyan}> $*${reset}"
}

usage() {
    cat >&2 <<EOF
Aufruf:
  update.sh --version <tag>       neues raspi-client-<tag>.jar von GitHub laden
  update.sh --jar <lokaler Pfad>  bereits vorliegendes Jar verwenden (offline)

Aktualisiert ein bereits provisioniertes Terminal auf ein neues Client-Jar, ohne
setup.sh erneut zu fahren. Haengt den Symlink ${LATEST_LINK} auf das neue Jar um
(das bisherige Ziel wird zu ${PREVIOUS_LINK}) und stoesst den Neustart an.

Env-Overrides: ELWA_ROOT (Default /opt/elwasys), ELWA_RESTART_CMD
(Default 'sudo killall java'), ELWA_JAVA_PGREP (Default 'pgrep -x java'),
ELWA_GITHUB_REPO (Default kabieror/elwasys).

Hinweis: Braucht das neue Jar ein hoeheres Java als das installierte JRE, zuerst
deploy/terminal/upgrade-jre.sh ausfuehren (Java 21).
EOF
    exit 1
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

# ==============================================================================
# Argumente parsen
# ==============================================================================

MODE=""
VERSION=""
SRC_JAR=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --version)
            [[ $# -ge 2 ]] || { echo "FEHLER: --version braucht einen <tag>." >&2; usage; }
            MODE="version"; VERSION="$2"; shift 2 ;;
        --jar)
            [[ $# -ge 2 ]] || { echo "FEHLER: --jar braucht einen <Pfad>." >&2; usage; }
            MODE="jar"; SRC_JAR="$2"; shift 2 ;;
        -h|--help)
            usage ;;
        *)
            echo "FEHLER: unbekanntes Argument: $1" >&2; usage ;;
    esac
done

[[ -n "${MODE}" ]] || { echo "FEHLER: entweder --version <tag> oder --jar <Pfad> angeben." >&2; usage; }

# ==============================================================================
# ELWA_ROOT betreten
# ==============================================================================

[[ -d "${ELWA_ROOT}" ]] || { echo "FEHLER: ELWA_ROOT '${ELWA_ROOT}' existiert nicht." >&2; exit 1; }
cd "${ELWA_ROOT}"

# ==============================================================================
# Neues Jar bereitstellen -> Zielname raspi-client-<version>.jar
# ==============================================================================

if [[ "${MODE}" == "version" ]]; then
    # GitHub-Release-Download-URL, gleiches Muster/Host wie setup.sh install_elwasys:
    #   https://github.com/<repo>/releases/download/<tag>/raspi-client-<tag>.jar
    new_jar="raspi-client-${VERSION}.jar"
    url="https://github.com/${ELWA_GITHUB_REPO}/releases/download/${VERSION}/raspi-client-${VERSION}.jar"
    if [[ -f "${new_jar}" ]]; then
        log_state "Jar ${new_jar} liegt bereits vor - kein erneuter Download."
    else
        log_state "Lade ${new_jar} von ${url} ..."
        # Robust: erst nach .part laden, dann atomar an den Zielnamen ruecken -
        # ein abgebrochener Download hinterlaesst so kein halbes Ziel-Jar.
        wget -O "${new_jar}.part" "${url}"
        mv "${new_jar}.part" "${new_jar}"
    fi
else
    # --jar: lokalen Pfad uebernehmen. Zielname aus dem Basename ableiten; ein
    # bereits korrekt benanntes raspi-client-<version>.jar wird beibehalten.
    [[ -f "${SRC_JAR}" ]] || { echo "FEHLER: Jar '${SRC_JAR}' nicht gefunden." >&2; exit 1; }
    new_jar="$(basename "${SRC_JAR}")"
    src_abs="$(cd "$(dirname "${SRC_JAR}")" && pwd)/$(basename "${SRC_JAR}")"
    if [[ "${src_abs}" == "${ELWA_ROOT}/${new_jar}" ]]; then
        log_state "Jar ${new_jar} liegt bereits in ELWA_ROOT."
    else
        log_state "Kopiere ${SRC_JAR} -> ${ELWA_ROOT}/${new_jar} ..."
        cp "${src_abs}" "./${new_jar}.part"
        mv "./${new_jar}.part" "./${new_jar}"
    fi
fi

[[ -f "${new_jar}" ]] || { echo "FEHLER: Ziel-Jar '${new_jar}' fehlt nach dem Bereitstellen." >&2; exit 1; }

# ==============================================================================
# Symlinks umhaengen (latest/previous)
# ==============================================================================

current_latest="$(link_target "${LATEST_LINK}")"

# Idempotenz: Zeigt latest schon auf das neue Jar, ist nichts zu tun (previous
# bleibt unangetastet - kein versehentliches Ueberschreiben des Rollback-Ziels).
if [[ "${current_latest}" == "${new_jar}" ]]; then
    log_state "${LATEST_LINK} zeigt bereits auf ${new_jar} - kein Umhaengen (idempotent)."
else
    # 1) bisheriges latest-Ziel als previous merken (Rollback-Ziel fuer AP5).
    #    Nur wenn es existiert und nicht identisch zum neuen Jar ist.
    if [[ -n "${current_latest}" ]]; then
        if [[ -e "${current_latest}" ]]; then
            log_state "Merke bisherige Version als ${PREVIOUS_LINK} -> ${current_latest}"
            ln -sfn "${current_latest}" "${PREVIOUS_LINK}"
        else
            echo "WARNUNG: bisheriges ${LATEST_LINK}-Ziel '${current_latest}' fehlt - ${PREVIOUS_LINK} unveraendert." >&2
        fi
    else
        echo "Hinweis: kein bestehender ${LATEST_LINK} - lege ihn neu an, ${PREVIOUS_LINK} bleibt leer." >&2
    fi

    # 2) latest atomar auf das neue Jar zeigen (relativer Basename, ln -sfn).
    log_state "Setze ${LATEST_LINK} -> ${new_jar}"
    ln -sfn "${new_jar}" "${LATEST_LINK}"
fi

# ==============================================================================
# Neustart ausloesen (Supervisor-Vertrag)
# ==============================================================================

# Laeuft eine JVM unter dem Supervisor? Dann beenden - die run.sh-Loop liest das
# Symlink-Ziel neu und startet das neue Jar. Laeuft keine (Terminal aus/Display
# aus), gibt es nichts zu beenden: sauberer Hinweis statt Fehler.
if ${ELWA_JAVA_PGREP} > /dev/null 2>&1; then
    log_state "Beende laufenden java-Prozess (Supervisor relauncht das neue Jar) ..."
    # Fehlschlag hier nicht fatal (Prozess koennte inzwischen weg sein).
    ${ELWA_RESTART_CMD} || true
    echo "Neustart angestossen: die run.sh-Loop startet in Kuerze ${new_jar}."
else
    log_state "Kein laufender java-Prozess gefunden (Terminal evtl. aus)."
    echo "Kein Neustart noetig - der Supervisor startet beim naechsten Lauf automatisch"
    echo "das jetzt verlinkte ${new_jar}."
fi

log_state "Update abgeschlossen."
echo "  ${LATEST_LINK}   -> $(link_target "${LATEST_LINK}")"
echo "  ${PREVIOUS_LINK} -> $(link_target "${PREVIOUS_LINK}")"
