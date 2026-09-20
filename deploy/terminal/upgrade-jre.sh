#!/bin/bash
# JRE-Upgrade fuer BESTEHENDE Raspi-Terminals (Phase 6 AP3, siehe
# docs/kb/05-migration-plan.md "Phase 6 - Produktivumschaltung" + Risikotabelle).
#
# Warum: Aeltere Geraete tragen aus einem frueheren setup.sh-Lauf noch ein Java 8
# oder 11. Das Client-fat-jar baut mit Sprachlevel 17 (Bytecode-Major 61); eine
# aeltere JRE bricht es beim Start mit UnsupportedClassVersionError ab. Dieses
# Skript hebt ein Bestandsgeraet auf die Zielversion an - ZWINGENDER erster
# Schritt VOR dem Rollout eines Release-Jars.
#
# ACHTUNG, Aenderung nach ADR 0026 (Cutover 2026-09-20): Die Zielversion ist
# jetzt 17, NICHT mehr 21. Fuer 32-bit-ARM gibt es kein JavaFX 21 mit
# GTK-Oberflaeche; ein mit Java 21 gestartetes Terminal bleibt dunkel. Siehe die
# ausfuehrliche Begruendung bei ELWA_JAVA_MAJOR weiter unten.
#
# Idempotent: Ist bereits eine ausreichende Java-Version aktiv, wird nur
# bestaetigt (apt-Aufrufe sind ohnehin idempotent). Robuste Verifikation am Ende
# (Major-Version aus "java -version" geparst; klarer Fehlschlag darunter).
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

# Ziel-Java-Version des Terminals.
#
# 17, NICHT 21 (ADR 0026, Befund vom Cutover 2026-09-20): Fuer 32-bit-ARM veroeffentlicht
# OpenJFX kein JavaFX 21 mit GTK-Oberflaeche - nur "linux-arm32-monocle". Die
# BellSoft-arm32-Runtime bringt dementsprechend kein libglassgtk3.so mit; ihr libglass.so
# referenziert GTK 2, erzwingt zur Laufzeit aber GTK >= 3.8. Ein mit Java 21 gestartetes
# Terminal blieb deshalb dunkel:
#   UnsupportedOperationException: Minimum GTK version required is 3.8.0. System has 2.24.31
# (und zwar bei installiertem GTK 3.22 - die Meldung ist irrefuehrend). Der Client wird
# seither mit Sprachlevel 17 gebaut; Java 17 hat auf allen hier genutzten Architekturen ein
# funktionierendes JavaFX.
#
# Ueberschreibbar, falls ein Geraet spaeter auf einem 64-bit-OS laeuft - dort gibt es
# JavaFX 21 mit GTK3 regulaer:
#   ELWA_JAVA_MAJOR=21 ELWA_JAVA_PACKAGE=bellsoft-java21-runtime-full ./upgrade-jre.sh
ELWA_JAVA_MAJOR="${ELWA_JAVA_MAJOR:-17}"
ELWA_JAVA_PACKAGE="${ELWA_JAVA_PACKAGE:-bellsoft-java17-runtime-full}"

main() {
    log_state "Aktuelle Java-Version pruefen ..."
    # T2 (QA-Review): nur EIN Aufruf von require_java_at_least (statt zweimal
    # "java -version" auszufuehren) - die OK-Zeile wird aus dem ersten Aufruf
    # aufgehoben und danach ausgegeben.
    local ok_msg
    if ok_msg="$(require_java_at_least "${ELWA_JAVA_MAJOR}" 2>/dev/null)"; then
        echo "Java ${ELWA_JAVA_MAJOR}+ ist bereits aktiv - nichts zu tun (idempotent)."
        echo "${ok_msg}"
        exit 0
    fi
    echo "Java < ${ELWA_JAVA_MAJOR} (oder nicht ermittelbar) - installiere ${ELWA_JAVA_PACKAGE} ..."

    # Gleiche apt-Quelle/Schluessel wie Client-Raspi/setup.sh (install_java).
    log_state "BellSoft-Paketquelle einrichten ..."
    wget -q -O - https://download.bell-sw.com/pki/GPG-KEY-bellsoft | sudo apt-key add -
    echo "deb [arch=armhf] https://apt.bell-sw.com/ stable main" | sudo tee /etc/apt/sources.list.d/bellsoft.list
    sudo apt-get update
    sudo apt-get install -y "${ELWA_JAVA_PACKAGE}"

    log_state "Verifiziere, dass jetzt Java ${ELWA_JAVA_MAJOR}+ aktiv ist ..."
    if ! require_java_at_least "${ELWA_JAVA_MAJOR}"; then
        echo "FEHLER: Nach der Installation ist immer noch kein Java ${ELWA_JAVA_MAJOR}+ aktiv." >&2
        echo "        Ggf. Alt-JRE per update-alternatives umstellen und erneut pruefen." >&2
        exit 1
    fi

    log_state "Java-21-Upgrade abgeschlossen."
    echo "Das Terminal kann jetzt ein mit Sprachlevel 21 gebautes Release-Jar ausfuehren."
    echo "Naechster Schritt: das neue Client-Jar ausrollen (update.sh, folgt in Phase 6 AP4)"
    echo "bzw. bei Ersteinrichtung das vollstaendige Client-Raspi/setup.sh."
}

main "$@"
