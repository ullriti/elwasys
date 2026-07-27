#!/bin/bash
# Gemeinsame Quelle des Terminal-Supervisors `run.sh` (Issue #101).
#
# Warum diese Datei existiert: `run.sh` traegt KEINEN lokalen Zustand - anders als
# elwasys.properties oder logback.xml wird sie vollstaendig generiert. Sie ist der
# Supervisor-Vertrag und der Startbefehl der JVM. Solange nur `Client-Raspi/setup.sh`
# sie erzeugte, erreichte jede Aenderung daran ausschliesslich frisch aufgesetzte
# Geraete; Bestandsgeraete liefen unbemerkt mit dem Startbefehl von damals weiter
# (JVM-Flags, Heap-Grenzen, Logback-Pfad, Supervisor-Schleife).
#
# `setup.sh` (Ersteinrichtung) und `deploy/terminal/update.sh` (Bestandsgeraet)
# ziehen den Generator jetzt beide von hier - eine Aenderung am Startbefehl wirkt
# damit auf beiden Wegen.
#
# Zum Sourcen gedacht, nicht zum direkten Ausfuehren.
#
# ==============================================================================
# Vertragsversion
# ==============================================================================
#
# Die generierte run.sh traegt eine Markerzeile mit der Vertragsversion. update.sh
# entscheidet daran, ob es die Datei auf dem Geraet erneuern muss.
#
#   (unmarkiert, mit `while true`-Schleife) = v1, der Supervisor aus Phase 6 AP3
#   (unmarkiert, OHNE Schleife)             = Altbestand aus dem urspruenglichen
#                                             setup.sh - Einmalstart, KEIN Supervisor
#   2                                       = v1 + Marker + Quittung des
#                                             Pending-Restart-Markers
#
# Bei jeder inhaltlichen Aenderung am generierten run.sh HOCHZAEHLEN - sonst
# erneuert update.sh die Datei auf den Bestandsgeraeten nicht.
ELWA_RUN_SH_CONTRACT=2

# Praefix der Markerzeile in der generierten run.sh.
ELWA_RUN_SH_MARKER_PREFIX="# elwasys-run-sh-contract:"

# Marker im ELWA_ROOT, der besagt: run.sh wurde erneuert, aber der LAUFENDE
# Supervisor ist noch der alte. Wird von der generierten run.sh beim Start
# geloescht - genau dann laeuft der neue Supervisor nachweislich. Siehe
# update.sh ("Neustart ausloesen") fuer die Auswertung.
ELWA_RUN_SH_PENDING_FILE=".run-sh-pending-restart"

# ==============================================================================
# Generator
# ==============================================================================

# elwa_generate_run_sh <ziel-datei> <elwa-root> [zusaetzliche-jvm-opts]
#
# Schreibt den Supervisor nach <ziel-datei>. Bewusst ueber Temp-Datei + `mv`
# (atomarer Rename) statt in-place: laeuft die alte run.sh gerade, haelt bash
# einen Datei-Offset auf ihr Inode. Ein In-place-Ueberschreiben liesse bash
# mitten im Skript an einer verschobenen Byte-Position weiterlesen; der Rename
# laesst den laufenden Prozess auf dem alten Inode zu Ende laufen.
#
# <zusaetzliche-jvm-opts> wird unveraendert in die java-Zeile uebernommen (leer
# lassen, wenn nichts noetig ist). update.sh reicht darueber die Truststore-Flags
# eines Altbestand-Geraets weiter, damit ein privates CA-Zertifikat beim Update
# nicht stillschweigend verlorengeht.
elwa_generate_run_sh() {
    local target="$1" root="$2" extra_opts="${3:-}"
    local tmp="${target}.tmp.$$"

    # Nicht-leere Zusatz-Opts brauchen genau ein Leerzeichen als Trenner; leere
    # duerfen keinen doppelten Abstand in die java-Zeile schreiben.
    local extra_inline=""
    [[ -n "${extra_opts}" ]] && extra_inline="${extra_opts} "

    cat > "${tmp}" <<EOT
#!/bin/bash
# elwasys Terminal-Supervisor - siehe deploy/terminal/README.md (Supervisor-Vertrag).
# Erzeugt aus deploy/terminal/run-sh.lib.sh (gemeinsame Quelle von Client-Raspi/setup.sh
# und deploy/terminal/update.sh) - NICHT von Hand editieren, ein Update ueberschreibt
# die Datei.
${ELWA_RUN_SH_MARKER_PREFIX} ${ELWA_RUN_SH_CONTRACT}

cd ${root}

# Quittung an update.sh: ab hier laeuft nachweislich DIESER Supervisor. Solange
# der Marker liegt, weiss update.sh, dass der laufende Prozess noch der alte
# (evtl. schleifenlose) Startbefehl ist, und loest keinen Kill aus - der wuerde
# ein Altbestand-Terminal dunkel zuruecklassen.
rm -f ${root}/${ELWA_RUN_SH_PENDING_FILE}

# Alt-Java-Prozesse EINMALIG vor der Schleife aufraeumen - NICHT im
# Schleifenkoerper (sonst wuerde ein Relaunch sich selbst abschiessen). Trifft
# nur die JVM, nicht diesen bash-Supervisor.
sudo killall java 2> /dev/null

while true; do
    # N3 (QA-Review Phase 6): vor jedem (Re-)Start die bisherigen stdout/errout
    # rotieren, WENN sie eine Grenze ueberschritten haben (einfache Groessen-
    # Schranke statt echtem logrotate - kein externes Tool auf dem Geraet
    # noetig). So bleibt trotz Anhaengen (statt Abschneiden, siehe unten) die
    # Groesse ueber viele Relaunches/Crash-Loops hinweg begrenzt; die zuletzt
    # rotierte Datei (*.1) bleibt als ein zusaetzliches Postmortem-Artefakt
    # erhalten. Schwelle per ELWA_LOG_MAX_BYTES ueberschreibbar.
    ELWA_LOG_MAX_BYTES=\${ELWA_LOG_MAX_BYTES:-5242880}
    for f in log/stdout log/errout; do
        if [ -f "\$f" ]; then
            size=\$(wc -c < "\$f" 2>/dev/null || echo 0)
            if [ "\${size:-0}" -gt "\$ELWA_LOG_MAX_BYTES" ]; then
                mv -f "\$f" "\$f.1"
            fi
        fi
    done

    # Symlink raspi-client.latest.jar pro Iteration NEU aufloesen: ein
    # Symlink-Wechsel (Update) zwischen zwei Iterationen greift damit
    # automatisch beim naechsten Start.
    # N3: anhaengen (>>) statt abschneiden (>) - ein Update-/Crash-Neustart
    # loescht damit nicht mehr das rohe stdout/stderr des vorigen Laufs (das
    # genau das Postmortem-Artefakt ist, das ein fehlgeschlagenes Auto-Update
    # braucht). Anwendungs-Logs laufen ohnehin separat rollierend ueber
    # logback.xml; diese Dateien fangen nur, was direkt auf STDOUT/STDERR
    # landet (z.B. JVM-Absturz vor Logging-Init).
    # Locale fest auf de_DE: Geldbetraege und Zeitangaben am Terminal sollen nicht davon
    # abhaengen, mit welchem LANG das Pi-Image gerade gebootet ist (die Anzeige formatiert
    # ueber FormatUtilities bewusst deutsch - so passen JVM-Default und Anzeige zusammen).
    java -Djavafx.platform=gtk -Duser.language=de -Duser.country=DE ${extra_inline}\\
            -Dlogback.configurationFile=${root}/logback.xml \\
            -jar raspi-client.latest.jar -verbose >> log/stdout 2>> log/errout

    # JVM hat sich beendet (Crash oder gezielt von aussen fuer Update/Watchdog).
    # Kurz warten (Fehler-Schleifen entzerren), dann den dann aktuell
    # verlinkten Jar erneut starten.
    sleep 2
done
EOT

    chmod +x "${tmp}"
    mv -f "${tmp}" "${target}"
}

# ==============================================================================
# Bestandsaufnahme einer vorhandenen run.sh
# ==============================================================================

# Vertragsversion der run.sh unter $1. Gibt 0 aus, wenn die Datei fehlt oder
# keinen Marker traegt (Altbestand oder v1).
elwa_run_sh_contract_version() {
    local file="$1" line
    [[ -r "${file}" ]] || { echo 0; return 0; }
    line="$(grep -m1 -F "${ELWA_RUN_SH_MARKER_PREFIX}" "${file}" 2>/dev/null || true)"
    if [[ "${line}" =~ ${ELWA_RUN_SH_MARKER_PREFIX}[[:space:]]*([0-9]+) ]]; then
        echo "${BASH_REMATCH[1]}"
    else
        echo 0
    fi
}

# Exit 0, wenn die run.sh unter $1 die Supervisor-Schleife enthaelt.
#
# Das ist die Frage, an der ein Update haengt - NICHT die Vertragsversion: der
# Vertrag v1 (Phase 6 AP3) traegt noch keinen Marker, hat die Schleife aber. Nur
# der urspruengliche Altbestand (Einmalstart aus dem ersten setup.sh) hat sie
# nicht, und genau dort darf update.sh die JVM nicht toeten: die Datei laeuft
# nach dem java-Aufruf aus, ~/.xsession endet, das Terminal bleibt dunkel.
elwa_run_sh_has_supervisor() {
    local file="$1"
    [[ -r "${file}" ]] || return 1
    grep -qE '^[[:space:]]*while[[:space:]]+true[[:space:]]*;[[:space:]]*do' "${file}"
}

# Gibt die Truststore-JVM-Flags aus der run.sh unter $1 aus (leer, wenn keine da
# sind). Der urspruengliche setup.sh-Altbestand richtete einen eigenen Truststore
# mit generiertem Passwort ein und reichte ihn per -Djavax.net.ssl.* an die JVM;
# der heutige setup.sh tut das nicht mehr. Beim Erneuern der run.sh muessen diese
# Flags mitwandern - sonst verliert ein Geraet mit privater CA beim Update still
# das Vertrauen zum Backend (backend.url ist seit Issue #35 https-Pflicht).
elwa_run_sh_extract_java_opts() {
    local file="$1" found
    [[ -r "${file}" ]] || return 0
    # Das `|| true` ist nicht kosmetisch: findet grep nichts (der Normalfall auf einem
    # Geraet ohne privaten Truststore), endet es mit 1 - unter `set -o pipefail` im
    # Aufrufer risse das die Zuweisung und damit update.sh mit runter.
    found="$(grep -oE '\-Djavax\.net\.ssl\.trustStore(Password)?=[^ \\]+' "${file}" 2>/dev/null || true)"
    [[ -n "${found}" ]] || return 0
    echo "${found}" | tr '\n' ' ' | sed 's/ *$//'
}
