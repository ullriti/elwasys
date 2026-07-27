#!/bin/bash
# Client-Jar-Update fuer BESTEHENDE Raspi-Terminals (Phase 6 AP4, siehe
# docs/kb/05-migration-plan.md "Phase 6 - Produktivumschaltung").
#
# Warum: Ein bereits provisioniertes Terminal (aus setup.sh) soll auf ein neues
# Client-fat-jar gehoben werden, OHNE das interaktive setup.sh erneut zu fahren -
# elwasys.properties, logback.xml und ~/.xsession bleiben unangetastet (sie tragen
# lokalen Zustand). Dieses Skript legt das neue Jar ab, haengt die Symlinks um,
# bringt bei Bedarf den Supervisor-run.sh auf den aktuellen Vertrag und stoesst den
# Neustart an.
#
# run.sh ist seit Issue #101 KEINE Ausnahme mehr: sie traegt keinen lokalen Zustand,
# sondern den Supervisor-Vertrag und den Startbefehl der JVM, und wird vollstaendig
# aus deploy/terminal/run-sh.lib.sh erzeugt - derselben Quelle, die setup.sh nutzt.
# Solange update.sh sie ausliess, erreichte jede Aenderung am Startbefehl (JVM-Flags,
# Heap, Logback-Pfad, Supervisor-Schleife) ausschliesslich frisch aufgesetzte Geraete.
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

# GitHub-Repo/Host wie in setup.sh install_elwasys (kanonisch: ullriti/elwasys, Issue #64).
ELWA_GITHUB_REPO="${ELWA_GITHUB_REPO:-ullriti/elwasys}"

# Kommando, das den laufenden java-Prozess (die JVM unter dem run.sh-Supervisor)
# beendet - so relauncht die Loop das neu verlinkte Jar. Fuer Trocken-Tests ohne
# echtes sudo/java ueberschreibbar.
ELWA_RESTART_CMD="${ELWA_RESTART_CMD:-sudo killall java}"

# Kommando, das prueft, ob (unter dem Supervisor) ueberhaupt eine JVM laeuft.
# Exit 0 == java laeuft. Fuer Trocken-Tests ueberschreibbar.
ELWA_JAVA_PGREP="${ELWA_JAVA_PGREP:-pgrep -x java}"

LATEST_LINK="raspi-client.latest.jar"
PREVIOUS_LINK="raspi-client.previous.jar"

# Gemeinsame Quelle des Supervisor-run.sh (Issue #101) - liegt neben diesem Skript
# und MUSS beim Ausrollen mit aufs Geraet kopiert werden (siehe README, Schritt 0
# des Cutover-Runbooks). Pfad vor dem `cd` nach ELWA_ROOT aufloesen.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ELWA_RUN_SH_LIB="${ELWA_RUN_SH_LIB:-${SCRIPT_DIR}/run-sh.lib.sh}"
if [[ ! -r "${ELWA_RUN_SH_LIB}" ]]; then
    echo "FEHLER: '${ELWA_RUN_SH_LIB}' nicht lesbar - update.sh braucht die Datei, um den" >&2
    echo "Supervisor-run.sh zu erneuern. run-sh.lib.sh neben update.sh ablegen (beide aus" >&2
    echo "deploy/terminal/) oder ELWA_RUN_SH_LIB setzen." >&2
    exit 1
fi
# shellcheck source=run-sh.lib.sh
source "${ELWA_RUN_SH_LIB}"

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

Optionen:
  --force-download   ein lokal bereits vorliegendes raspi-client-<tag>.jar vor dem
                     Download verwerfen (für unter demselben Tag repariertes Asset).

Aktualisiert ein bereits provisioniertes Terminal auf ein neues Client-Jar, ohne
setup.sh erneut zu fahren. Bei --version wird der Download per SHA-256-Prüfsumme +
Zip-Struktur verifiziert (Integrität), erst dann ausgerollt. Haengt den Symlink
${LATEST_LINK} auf das neue Jar um (das bisherige Ziel wird zu ${PREVIOUS_LINK}),
bringt den Supervisor-run.sh auf den aktuellen Vertrag (Issue #101) und stoesst den
Neustart an.

Exit-Codes:
  0  Update ausgerollt (Neustart angestossen bzw. nicht noetig)
  1  Fehler vor dem Ausrollen (Argumente, Download, Integritaet) - nichts geaendert
  3  Symlink umgehaengt, aber Neustart scheiterte an Rechten (sudoers)
  4  Symlink umgehaengt und run.sh erneuert, Neustart bewusst NICHT ausgeloest:
     das Geraet faehrt noch den Altbestand-Startbefehl ohne Supervisor-Schleife
     und braucht einmalig einen Session-Neustart von Hand

Env-Overrides: ELWA_ROOT (Default /opt/elwasys), ELWA_RESTART_CMD
(Default 'sudo killall java'), ELWA_JAVA_PGREP (Default 'pgrep -x java'),
ELWA_GITHUB_REPO (Default ullriti/elwasys), ELWA_RUN_SH_LIB (Default
run-sh.lib.sh neben diesem Skript).

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

# Prüft die Integrität eines heruntergeladenen Jars (Issue #62): SHA-256-Prüfsumme
# (sha256sum -c) + Zip/Jar-Struktur (fängt abgeschnittene Downloads / getarnte HTML-Fehlerseiten
# ab). $1 = zu prüfende Datei, $2 = zugehörige .sha256 (beide relative Namen im ELWA_ROOT).
# Rückgabe != 0 bei jedem Fehlschlag -> der Aufrufer verwirft den Download, es wird NICHTS
# verlinkt (der Watchdog behandelt das als B1-Fetch-Fehlschlag: kein Rollback/Kill).
verify_jar_integrity() {
    local file="$1" sumfile="$2"
    if [[ ! -s "${sumfile}" ]]; then
        echo "FEHLER: Prüfsummendatei '${sumfile}' fehlt/leer - verwerfe Download." >&2
        return 1
    fi
    # .sha256 trägt den Asset-Namen; für "sha256sum -c" gegen die (noch .part-)Datei den
    # Dateinamen in der Prüfzeile auf ${file} umschreiben.
    if ! sed "s|  .*|  ${file}|" "${sumfile}" | sha256sum -c - ; then
        echo "FEHLER: SHA-256-Prüfsumme stimmt nicht - verwerfe Download." >&2
        return 1
    fi
    if command -v unzip >/dev/null 2>&1; then
        if ! unzip -t -qq "${file}" >/dev/null 2>&1; then
            echo "FEHLER: '${file}' ist kein gültiges Jar/Zip-Archiv - verwerfe Download." >&2
            return 1
        fi
    else
        # Fallback ohne unzip: Zip/Jar beginnt mit der Magic "PK".
        if [[ "$(head -c2 "${file}" 2>/dev/null)" != "PK" ]]; then
            echo "FEHLER: '${file}' beginnt nicht mit der Zip/Jar-Magic 'PK' - verwerfe Download." >&2
            return 1
        fi
    fi
    return 0
}

# Führt den Neustart-Trigger (ELWA_RESTART_CMD) aus und unterscheidet einen echten Rechte-/
# sudo-Fehler (der Kill konnte NICHT ausgeführt werden) von einem harmlosen "kein Prozess mehr"
# (killall-Exit != 0 ohne Rechtefehler). Rückgabe 3 nur beim Rechte-/sudo-Fehler, sonst 0.
# So löst ein am fehlenden sudoers-Recht gescheiterter Kill KEINEN grundlosen Rollback und
# damit keine Endlosschleife aus (Issue #63/#34).
run_restart_cmd() {
    local out rc
    out="$(${ELWA_RESTART_CMD} 2>&1)"; rc=$?
    if (( rc != 0 )); then
        if printf '%s' "${out}" | grep -qiE 'sudo|passwo|not allowed|permission|operation not permitted'; then
            echo "FEHLER: Neustart-Trigger '${ELWA_RESTART_CMD}' scheiterte an Rechten (sudoers?): ${out}" >&2
            return 3
        fi
        # killall meldet auch dann != 0, wenn gar kein Prozess (mehr) lief - harmlos, der
        # Supervisor startet ohnehin das aktuell verlinkte Jar.
        echo "Hinweis: '${ELWA_RESTART_CMD}' meldete Exit ${rc} (vermutlich kein Prozess mehr) - unkritisch."
    fi
    return 0
}

# ==============================================================================
# Argumente parsen
# ==============================================================================

MODE=""
VERSION=""
SRC_JAR=""
# --force-download (Issue #34/#62): ein bereits lokal vorliegendes raspi-client-<version>.jar
# vor dem Neu-Download verwerfen. Nötig, wenn ein Release-Asset unter DEMSELBEN Tag repariert
# wurde (z.B. nach einem fehlgeschlagenen Update) - sonst würde update.sh das alte, kaputte Jar
# beibehalten ("liegt bereits vor - kein erneuter Download").
FORCE_DOWNLOAD=0

while [[ $# -gt 0 ]]; do
    case "$1" in
        --version)
            [[ $# -ge 2 ]] || { echo "FEHLER: --version braucht einen <tag>." >&2; usage; }
            MODE="version"; VERSION="$2"; shift 2 ;;
        --jar)
            [[ $# -ge 2 ]] || { echo "FEHLER: --jar braucht einen <Pfad>." >&2; usage; }
            MODE="jar"; SRC_JAR="$2"; shift 2 ;;
        --force-download)
            FORCE_DOWNLOAD=1; shift ;;
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
    base_url="https://github.com/${ELWA_GITHUB_REPO}/releases/download/${VERSION}"
    url="${base_url}/raspi-client-${VERSION}.jar"
    # --force-download: ein evtl. lokal liegendes (womöglich kaputtes) Jar vorher entfernen,
    # damit ein unter demselben Tag repariertes Asset wirklich neu geladen wird (Issue #34).
    if [[ "${FORCE_DOWNLOAD}" == "1" && -f "${new_jar}" ]]; then
        log_state "--force-download: entferne lokal vorhandenes ${new_jar} vor dem Neu-Download."
        rm -f "${new_jar}"
    fi
    if [[ -f "${new_jar}" ]]; then
        log_state "Jar ${new_jar} liegt bereits vor - kein erneuter Download."
    else
        log_state "Lade ${new_jar} von ${url} ..."
        # Robust: erst nach .part laden, Integrität prüfen (Issue #62: SHA-256 + Zip-Struktur),
        # dann atomar an den Zielnamen ruecken - ein abgebrochener/kaputter Download hinterlaesst
        # so kein halbes/ungeprüftes Ziel-Jar und wird verworfen (kein Deploy).
        # Bekannte Einschränkung (#62): Ein Release MUSS ein raspi-client-<tag>.jar.sha256-Asset
        # mitliefern (release.yml erzeugt es für jedes Release automatisch). Fehlt es - z.B. bei einem
        # Alt-Release von vor AP6 - schlaegt der zweite wget unter `set -e` fehl; das ist bewusst
        # (kein ungeprüftes Jar), macht solche Alt-Releases aber nicht per Auto-Update ausrollbar.
        wget -O "${new_jar}.part" "${url}"
        wget -O "${new_jar}.sha256" "${base_url}/raspi-client-${VERSION}.jar.sha256"
        if ! verify_jar_integrity "${new_jar}.part" "${new_jar}.sha256"; then
            rm -f "${new_jar}.part" "${new_jar}.sha256"
            echo "FEHLER: Download von ${new_jar} fehlgeschlagen (Integrität) - nichts ausgerollt." >&2
            exit 1
        fi
        rm -f "${new_jar}.sha256"
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
# Supervisor-run.sh auf den aktuellen Vertrag bringen (Issue #101)
# ==============================================================================

# Reihenfolge: NACH dem Symlink-Wechsel, VOR dem Neustart. Der neue Startbefehl soll
# schon stehen, wenn der Supervisor das naechste Mal anlaeuft.
run_sh="run.sh"
run_sh_version="$(elwa_run_sh_contract_version "${run_sh}")"

# Hat die run.sh, die JETZT laeuft, ueberhaupt die Supervisor-Schleife? Diese Frage
# entscheidet ueber den Neustart weiter unten - und sie muss VOR dem Erneuern
# beantwortet werden, weil danach jede run.sh eine Schleife hat.
#
# Eine FEHLENDE run.sh zaehlt hier bewusst NICHT als Altbestand: dann ist auch nichts
# aus ihr gestartet, was ein Kill dunkel zuruecklassen koennte. Wir legen sie an und
# fahren den normalen Weg. Nur eine vorhandene run.sh OHNE Schleife ist der
# gefaehrliche Fall (Einmalstart aus dem urspruenglichen setup.sh).
supervisor_running=1
if [[ -f "${run_sh}" ]]; then
    elwa_run_sh_has_supervisor "${run_sh}" || supervisor_running=0
fi

if (( run_sh_version < ELWA_RUN_SH_CONTRACT )); then
    log_state "Erneuere ${run_sh} (Vertrag v${run_sh_version} -> v${ELWA_RUN_SH_CONTRACT}) ..."

    # Truststore-Flags eines Altbestand-Geraets mitnehmen: der urspruengliche setup.sh
    # richtete einen eigenen Truststore mit generiertem Passwort ein, der heutige nicht
    # mehr. Wuerden die Flags beim Erneuern wegfallen, verloere ein Geraet mit privater
    # CA still das Vertrauen zum Backend (backend.url ist https-Pflicht, Issue #35).
    carried_opts="$(elwa_run_sh_extract_java_opts "${run_sh}")"
    if [[ -n "${carried_opts}" ]]; then
        echo "Uebernehme vorhandene Truststore-Flags: ${carried_opts}"
    fi

    if [[ -f "${run_sh}" ]]; then
        backup="${run_sh}.v${run_sh_version}.bak"
        cp -p "${run_sh}" "${backup}"
        echo "Bisherige Fassung gesichert als ${backup}."
    fi

    # elwa_generate_run_sh schreibt ueber Temp-Datei + Rename. Das ist hier wesentlich,
    # nicht kosmetisch: laeuft die alte run.sh gerade, haelt bash einen Datei-Offset auf
    # ihr Inode - ein In-place-Ueberschreiben liesse den laufenden Supervisor an einer
    # verschobenen Byte-Position weiterlesen.
    elwa_generate_run_sh "${run_sh}" "${ELWA_ROOT}" "${carried_opts}"

    if (( supervisor_running == 0 )); then
        # Altbestand ohne Schleife: die laufende run.sh startet die JVM genau einmal und
        # laeuft danach aus. Ein Kill wuerde ~/.xsession beenden und das Terminal dunkel
        # zuruecklassen - es gibt niemanden, der relauncht. Der Marker haelt diesen
        # Zustand fest, bis die NEUE run.sh tatsaechlich angelaufen ist (sie loescht ihn
        # als erste Amtshandlung).
        : > "${ELWA_RUN_SH_PENDING_FILE}"
    fi
else
    log_state "${run_sh} ist auf Vertrag v${run_sh_version} - keine Erneuerung noetig."
fi

# ==============================================================================
# Neustart ausloesen (Supervisor-Vertrag)
# ==============================================================================

# Liegt der Pending-Marker, ist der LAUFENDE Prozess noch der alte, schleifenlose
# Startbefehl - egal, was inzwischen in run.sh steht. Erst der Start der neuen run.sh
# raeumt den Marker weg. Bis dahin: kein Kill.
if [[ -e "${ELWA_RUN_SH_PENDING_FILE}" ]]; then
    log_state "Neustart NICHT ausgeloest - dieses Terminal faehrt noch den Altbestand-Startbefehl."
    cat >&2 <<EOF

Das neue Jar ist ausgerollt (${LATEST_LINK} -> ${new_jar}) und die erneuerte run.sh
liegt bereit, ABER der gerade laufende Startbefehl ist der Einmalstart aus dem
urspruenglichen setup.sh - ohne Supervisor-Schleife. Wuerde jetzt 'killall java'
laufen, bliebe das Terminal dunkel, bis jemand vor Ort ist.

EINMALIG von Hand nachziehen (danach laeuft jedes weitere Update wie gewohnt):

    sudo systemctl restart lightdm     # bzw. das Display-Manager-Unit des Geraets
    # oder, wenn unklar:
    sudo reboot

Nach dem Start verschwindet ${ELWA_ROOT}/${ELWA_RUN_SH_PENDING_FILE} von selbst -
das ist die Quittung, dass der neue Supervisor laeuft.
EOF
    exit 4
fi

# Laeuft eine JVM unter dem Supervisor? Dann beenden - die run.sh-Loop liest das
# Symlink-Ziel neu und startet das neue Jar. Laeuft keine (Terminal aus/Display
# aus), gibt es nichts zu beenden: sauberer Hinweis statt Fehler.
if ${ELWA_JAVA_PGREP} > /dev/null 2>&1; then
    log_state "Beende laufenden java-Prozess (Supervisor relauncht das neue Jar) ..."
    # Kill-Exit-Code NICHT blind verschlucken (Issue #63): scheitert der Neustart-Trigger an
    # fehlenden Rechten (sudoers), ist der Neustart NICHT erfolgt - das mit Exit 3 melden, damit
    # der Watchdog das vom Deploy-Erfolg unterscheiden und einen grundlosen Rollback (und die
    # daraus folgende Endlosschleife, Issue #34) vermeiden kann. Ein harmloses "kein Prozess
    # mehr" behandelt run_restart_cmd als Erfolg.
    if ! run_restart_cmd; then
        echo "FEHLER: Neustart nach dem Symlink-Wechsel nicht ausgelöst (Rechte?). ${LATEST_LINK} zeigt jetzt auf ${new_jar}, aber der alte Prozess läuft weiter." >&2
        exit 3
    fi
    echo "Neustart angestossen: die run.sh-Loop startet in Kuerze ${new_jar}."
else
    log_state "Kein laufender java-Prozess gefunden (Terminal evtl. aus)."
    echo "Kein Neustart noetig - der Supervisor startet beim naechsten Lauf automatisch"
    echo "das jetzt verlinkte ${new_jar}."
fi

log_state "Update abgeschlossen."
echo "  ${LATEST_LINK}   -> $(link_target "${LATEST_LINK}")"
echo "  ${PREVIOUS_LINK} -> $(link_target "${PREVIOUS_LINK}")"
