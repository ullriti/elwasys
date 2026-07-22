#!/bin/bash
# JRE-Upgrade fuer BESTEHENDE Raspi-Terminals (Phase 6 AP3, siehe
# kb/05-migration-plan.md "Phase 6 - Produktivumschaltung" + Risikotabelle).
#
# Warum: Bereits im Feld provisionierte Terminals tragen aus einem frueheren
# setup.sh-Lauf nur ein Java-17-JRE. Das Client-fat-jar baut seit Phase 1 mit
# Sprachlevel 21 (Bytecode-Major 65); ein Java-17-JRE bricht es beim Start mit
# UnsupportedClassVersionError ab. Dieses Skript hebt ein Bestandsgeraet auf
# Java 21 an - ZWINGENDER erster Schritt VOR dem Rollout eines mit Sprachlevel 21
# gebauten Release-Jars. (Neu-Provisionierung ueber setup.sh installiert Java 21
# ohnehin schon - dieses Skript ist der Nachruest-Pfad fuer Altgeraete.)
#
# Idempotent: Ist bereits Java >= 21 aktiv, wird nur bestaetigt (apt-Aufrufe sind
# ohnehin idempotent). Robuste Verifikation am Ende (Major-Version aus
# "java -version" geparst; klarer Fehlschlag bei < 21).
#
# HINWEIS: Die eigentlichen apt-Schritte laufen NUR auf dem Geraet (armhf,
# Raspberry Pi OS). In der Projekt-Sandbox wurden nur die Version-Parsing-/
# Pruef-Funktionen (java_major_version/require_java_at_least) trocken verifiziert.
set -euo pipefail

# ==============================================================================
# Sourceable/testbare Versionspruefung (keine Seiteneffekte)
# ==============================================================================

# java_major_version [java-binary]
#   Gibt die Major-Feature-Version aus "<java> -version" (stderr) aus, z.B. 17
#   oder 21. Versteht beide ueblichen Formate:
#     openjdk version "21.0.4" 2024-07-16   -> 21
#     java version "1.8.0_202"              -> 8   (Alt-Schema 1.MAJOR)
#   Exit 1, wenn die Version nicht ermittelt werden kann.
java_major_version() {
    local java_bin="${1:-java}"
    local out ver major
    out="$("${java_bin}" -version 2>&1)" || return 1
    # erste Ausgabezeile
    out="${out%%$'\n'*}"
    # erste in "..." eingeschlossene Versionszeichenkette herausloesen
    ver="${out#*\"}"
    ver="${ver%%\"*}"
    [[ "${ver}" != "${out}" ]] || return 1   # keine gequotete Version gefunden
    major="${ver%%.*}"
    if [[ "${major}" == "1" ]]; then
        # Alt-Schema 1.MAJOR (Java <= 8): die Zahl nach dem ersten Punkt
        ver="${ver#*.}"
        major="${ver%%.*}"
    fi
    [[ "${major}" =~ ^[0-9]+$ ]] || return 1
    echo "${major}"
}

# require_java_at_least <min-major> [java-binary]
#   Exit 0 (+ OK-Zeile) wenn die Major-Version >= min ist, sonst Exit 1 (+ Fehler).
require_java_at_least() {
    local min="$1" java_bin="${2:-java}" major
    if ! major="$(java_major_version "${java_bin}")"; then
        echo "FEHLER: konnte die Java-Version aus '${java_bin} -version' nicht ermitteln." >&2
        return 1
    fi
    if (( major >= min )); then
        echo "OK: Java-Major-Version ${major} (>= ${min})."
        return 0
    fi
    echo "FEHLER: Java-Major-Version ${major} < ${min} erforderlich." >&2
    return 1
}

# Nur ge-sourced (z.B. aus einem Test)? Dann hier stoppen - keine Installation.
[[ "${BASH_SOURCE[0]}" == "${0}" ]] || return 0

# ==============================================================================
# Installation (nur bei direktem Aufruf)
# ==============================================================================

log_state() {
    local cyan='\033[0;36m' reset='\033[0m'
    echo -e "\n${cyan}> $*${reset}"
}

if [[ ${EUID} -eq 0 ]]; then
    echo "Dieses Skript nicht als root ausfuehren (nutzt sudo fuer die einzelnen Schritte)." >&2
    exit 1
fi

main() {
    log_state "Aktuelle Java-Version pruefen ..."
    # T2 (QA-Review): nur EIN Aufruf von require_java_at_least (statt zweimal
    # "java -version" auszufuehren) - die OK-Zeile wird aus dem ersten Aufruf
    # aufgehoben und danach ausgegeben.
    local ok_msg
    if ok_msg="$(require_java_at_least 21 2>/dev/null)"; then
        echo "Java 21+ ist bereits aktiv - nichts zu tun (idempotent)."
        echo "${ok_msg}"
        exit 0
    fi
    echo "Java < 21 (oder nicht ermittelbar) - installiere bellsoft-java21-runtime-full ..."

    # Gleiche apt-Quelle/Schluessel wie Client-Raspi/setup.sh (install_java).
    log_state "BellSoft-Paketquelle einrichten ..."
    wget -q -O - https://download.bell-sw.com/pki/GPG-KEY-bellsoft | sudo apt-key add -
    echo "deb [arch=armhf] https://apt.bell-sw.com/ stable main" | sudo tee /etc/apt/sources.list.d/bellsoft.list
    sudo apt-get update
    sudo apt-get install -y bellsoft-java21-runtime-full

    log_state "Verifiziere, dass jetzt Java 21+ aktiv ist ..."
    if ! require_java_at_least 21; then
        echo "FEHLER: Nach der Installation ist immer noch kein Java 21+ aktiv." >&2
        echo "        Ggf. Alt-JRE per update-alternatives auf Java 21 umstellen und erneut pruefen." >&2
        exit 1
    fi

    log_state "Java-21-Upgrade abgeschlossen."
    echo "Das Terminal kann jetzt ein mit Sprachlevel 21 gebautes Release-Jar ausfuehren."
    echo "Naechster Schritt: das neue Client-Jar ausrollen (update.sh, folgt in Phase 6 AP4)"
    echo "bzw. bei Ersteinrichtung das vollstaendige Client-Raspi/setup.sh."
}

main "$@"
