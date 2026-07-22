#!/bin/bash
set -e

# Check if the script is run as root
if [[ $EUID -eq 0 ]]
then
  echo "This script should not be run as root."
  exit 1
fi

function log_state() {
  local cyan='\033[0;36m'
  local reset='\033[0m'
  echo -e "\n${cyan}> $@${reset}"
}
function log_success() {
  local green='\033[0;32m'
  local reset='\033[0m'
  echo -e "\n${green}> $@${reset}"
}
function generate_password() {
  password=$(date +%s | sha256sum | base64 | head -c 32 ; echo)
  echo "$password"
}

function collect_data() {
    echo
    echo
    echo === Backend Connection ===
    echo
    echo "Since Phase 4 the terminal talks to the elwasys backend exclusively (REST API v1"
    echo "for login/devices/programs/executions, plus an outgoing WebSocket connection for"
    echo "remote maintenance - status/log/restart). Enter its base URL and this terminal's"
    echo "location token (issued via the backend's token-cli, see kb/04-build-and-run.md)."
    echo "No database access is needed on the terminal any more."
    echo
    read -p "Enter backend base URL (e.g., https://backend-host:8080/): " BACKEND_URL
    echo
    read -s -p "Enter this terminal's backend token: " BACKEND_TOKEN
    echo

    echo
    echo
    echo === elwasys Configuration ===
    echo
    read -p "Enter client location: " LOCATION
    echo
    read -p "Enter portal URL: " PORTAL_URL
    echo
    echo
    echo
}

function install_dependencies() {
    sudo apt-get update
    sudo apt-get install -y curl
}

# # # # # # #
function install_java() {
    log_state Installing Java Runtime Environment...
    wget -q -O - https://download.bell-sw.com/pki/GPG-KEY-bellsoft | sudo apt-key add -
    echo "deb [arch=armhf] https://apt.bell-sw.com/ stable main" | sudo tee /etc/apt/sources.list.d/bellsoft.list
    sudo apt-get update
    sudo apt-get install -y bellsoft-java21-runtime-full
}


# # # # # # #
function setup_firewall() {
    log_state Installing Firewall...

    # Install UFW
    sudo apt-get update
    sudo apt-get install -y ufw

    # Enable UFW
    sudo ufw enable

    sudo ufw default deny incoming
    sudo ufw allow 22
    sudo ufw --force enable
    sudo ufw status
}


# # # # # # #
function install_deconz() {
    log_state Installing deCONZ Zigbee Gateway...

    # Validations
    [ "$DECONZ_PASSWORD" ] || { echo "DECONZ_PASSWORD must be set"; exit 1; }

    sudo gpasswd -a $USER dialout
    wget -O - http://phoscon.de/apt/deconz.pub.key | \
            sudo apt-key add -
    sudo sh -c "echo 'deb [arch=armhf] http://phoscon.de/apt/deconz \
                $(lsb_release -cs) main' > \
                /etc/apt/sources.list.d/deconz.list"

    sudo apt-get update
    sudo apt-get install -y deconz
    sudo systemctl enable deconz

    echo "Setting new password"
    dc_url="http://localhost"
    dc_user="delight"
    dc_ott="elwasysinstalleronetimetoken"
    old_credentials=`echo -n "$dc_user:$dc_user" | base64`
    new_credentials=`echo -n "$dc_user:$DECONZ_PASSWORD" | base64`

    curl -sf -XPOST \
        -H "Authorization: Basic $old_credentials" \
        -H "Content-type: application/json" \
        -d "{ \"username\": \"$dc_ott\", \"devicetype\": \"installer\" }" \
        $dc_url/api > /dev/null || { echo "ERROR: Failed to log in to deCONZ using the default credentials."; }
    curl -sf -XPUT \
        -H "Content-type: application/json" \
        -d "{ \"username\": \"$dc_user\", \"oldhash\": \"$old_credentials\", \"newhash\": \"$new_credentials\" }" \
        $dc_url/api/$dc_ott/config/password > /dev/null || { echo "ERROR: Failed to change the deCONZ password."; }
}


# # # # # # #
function install_elwasys() {
    log_state Installing elwasys...
    sudo mkdir -p $ELWA_ROOT
    sudo chown "$USER:$USER" $ELWA_ROOT

    cd $ELWA_ROOT

    version=$(curl --silent -qI https://github.com/kabieror/elwasys/releases/latest | awk -F '/' '/^location/ {print  substr($NF, 1, length($NF)-1)}')
    jar_file=./raspi-client-${version}.jar
    if [ ! -f "$jar_file" ]
    then
        wget https://github.com/kabieror/elwasys/releases/download/$version/raspi-client-${version}.jar -O $jar_file
        ln -s ./raspi-client-${version}.jar ./raspi-client.latest.jar
    else
        echo "Skipping downloading raspi-client JAR. File already exists: $jar_file"
    fi
}


# # # # # # #
function config_elwasys() {
    log_state Configuring elwasys
    # Populate the Config file
    config_file="./elwasys.properties"
    tee "$config_file" > /dev/null <<EOT
# Base URL of the elwasys backend (REST API v1 + outgoing WebSocket maintenance
# connection). Since Phase 4 AP5 this is the terminal's ONLY data access path
# (login, devices, programs, executions, credit, remote status/log/restart); see
# kb/05-migration-plan.md "Client-Cutover"/"Arbeitspakete Phase 4" AP4/AP5. No
# direct database access remains on the terminal.
backend.url: $BACKEND_URL

# This terminal's location token for the backend API v1 (issued via token-cli).
# Used for both the REST API and the outgoing maintenance WebSocket connection.
backend.token: $BACKEND_TOKEN

# Location of this client (display name only, e.g. shown in error messages -
# the actual access scope is determined by backend.token above)
#   Only devices at this location will be available.
# e.g. Laundry
location: $LOCATION

# Time in seconds after which the display is turned off
# If the value is negative, the display will not be turned off.
displayTimeout: 10

# Time in seconds for which the startup procedure is to be delayed
startupDelay: 0

# Time in seconds after which a logged in user is to be logged off.
sessionTimeout: 60

# The URL of the elwasys web portal
portalUrl: $PORTAL_URL

# Address of the Zigbee gateway
deconz.server: http://localhost
deconz.user: delight
deconz.password: $DECONZ_PASSWORD

# Notifications (email/push) are sent centrally by the backend now
# (elwasys.notifications.enabled in the backend configuration). This client no
# longer sends any notifications itself, so smtp.*/pushover.* settings are gone.

# Remote maintenance (status/log/restart) is requested by the portal over the same
# outgoing WebSocket connection the terminal already holds for the REST API (see
# backend.url/backend.token above) - the terminal no longer listens on a port itself,
# so maintenance.server/maintenance.port no longer exist here either.
EOT
    sudo chmod 600 "$config_file"

    # configure logging
    logback_config="./logback.xml"
    tee "$logback_config" > /dev/null <<EOT
<configuration scan="true" debug="true">
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <filter class="ch.qos.logback.classic.filter.ThresholdFilter">
            <level>ERROR</level>
        </filter>
        <encoder>
            <pattern>%d %level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    <appender name="FILE-DEBUG" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <!-- daily rollover -->
            <fileNamePattern>log/elwasys.%d{yyyy-MM-dd}.debug.log</fileNamePattern>

            <!-- keep 30 days' worth of history -->
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d %level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <filter class="ch.qos.logback.classic.filter.ThresholdFilter">
            <level>INFO</level>
        </filter>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <!-- daily rollover -->
            <fileNamePattern>log/elwasys.%d{yyyy-MM-dd}.log</fileNamePattern>

            <!-- keep 300 days' worth of history -->
            <maxHistory>300</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d %level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="debug">
        <appender-ref ref="STDOUT"/>
        <appender-ref ref="FILE"/>
        <appender-ref ref="FILE-DEBUG"/>
    </root>
</configuration>
EOT

    # run.sh script (Supervising-Loop, Phase 6 AP3)
    #
    # Startet die JavaFX-Touch-App in einer Schleife im Vordergrund. Der
    # Bedienfluss/das UI sind identisch zum frueheren Einmalstart - es kommt nur
    # Respawn (Ausfallsicherheit + Update-Uebernahme) hinzu, kein systemd noetig.
    #
    # Supervisor-Vertrag (fuer Watchdog/Update, siehe deploy/terminal/README.md):
    #   Ein externer Neustart == den laufenden java-Prozess beenden (z.B.
    #   "sudo killall java" oder "pkill -f raspi-client"). Die run.sh-Loop faengt
    #   das ab, wartet kurz und relauncht den AKTUELL per Symlink verlinkten Jar.
    #   Ein Update haengt also nur den Symlink raspi-client.latest.jar um und
    #   beendet den java-Prozess - die naechste Iteration liest das Symlink-Ziel
    #   NEU und startet automatisch das neue Jar. "killall java" trifft die JVM,
    #   nicht diesen bash-Supervisor - die Schleife laeuft weiter.
    run_script="./run.sh"
    tee "$run_script" > /dev/null <<EOT
#!/bin/bash
# elwasys Terminal-Supervisor - siehe deploy/terminal/README.md (Supervisor-Vertrag).
# Erzeugt von Client-Raspi/setup.sh (config_elwasys).

cd $ELWA_ROOT

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
    java -Djavafx.platform=gtk -Dlogback.configurationFile=$ELWA_ROOT/logback.xml \
            -jar raspi-client.latest.jar -verbose >> log/stdout 2>> log/errout

    # JVM hat sich beendet (Crash oder gezielt von aussen fuer Update/Watchdog).
    # Kurz warten (Fehler-Schleifen entzerren), dann den dann aktuell
    # verlinkten Jar erneut starten.
    sleep 2
done
EOT
    chmod +x "$run_script"

    # Create log output folder
    mkdir -p ./log

    # Auto-Start
    cat > ~/.xsession <<EOT
cd $ELWA_ROOT
./run.sh
EOT
}

##################################
######## Main Entrypoint #########
##################################

collect_data

ELWA_ROOT=/opt/elwasys
DECONZ_PASSWORD=$(generate_password)

log_state Starting Installation...
install_dependencies

setup_firewall

install_java

install_deconz

install_elwasys

config_elwasys

echo
echo
log_success Installation completed!
echo Please reboot now to complete installation.
echo
echo "  $ sudo reboot"
echo
echo Please be sure to change the default password of the user $USER.
echo Run this command to change the password:
echo
echo "  $ passwd"
echo
echo
