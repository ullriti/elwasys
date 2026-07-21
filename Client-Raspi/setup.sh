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
    echo "Since Phase 4 the terminal talks to the elwasys backend (REST API v1)"
    echo "instead of the database directly. Enter its base URL and this terminal's"
    echo "location token (issued via the backend's token-cli, see kb/04-build-and-run.md)."
    echo
    read -p "Enter backend base URL (e.g., https://backend-host:8080/): " BACKEND_URL
    echo
    read -s -p "Enter this terminal's backend token: " BACKEND_TOKEN
    echo

    echo
    echo
    echo === Database Connection \(TRANSITIONAL\) ===
    echo
    echo "TRANSITIONAL: direct database access is only still used for the maintenance"
    echo "connection registration (LocationManager); it will be replaced by the"
    echo "outgoing WebSocket connection in Phase 4 AP5, after which these questions go"
    echo "away. All other data access goes through the backend above."
    echo
    read -p "Enter database server address (e.g., localhost:5432): " DB_SERVER
    echo
    read -p "Enter database name: " DB_NAME
    echo
    read -p "Enter database username: " DB_USER
    echo
    read -s -p "Enter database password: " DB_PASSWORD
    echo
    echo
    read -p "Should the database connection use SSL? (true/false): " DB_USE_SSL
    echo
    echo "Please enter the CA certificate for verifying the server SSL certificate."
    echo "Provide a file in PEM format."
    echo "When you're done, type #"
    read -d '#' DB_CA_CERT

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
# Base URL of the elwasys backend (REST API v1). Since Phase 4 this is the
# terminal's primary data access path (login, devices, programs, executions,
# credit); see kb/05-migration-plan.md "Client-Cutover".
backend.url: $BACKEND_URL

# This terminal's location token for the backend API v1 (issued via token-cli).
backend.token: $BACKEND_TOKEN

# TRANSITIONAL (Phase 4 AP4, removed in AP5): the direct database connection is
# only still used for the maintenance connection registration (LocationManager);
# all other data access goes through backend.url/backend.token above.
# The address of the postgresql server
# z.B. - databaseserver1:5432
#      - 192.168.0.100:10090,
#      - 10.0.0.5
database.server: $DB_SERVER

# Name of the database
database.name: $DB_NAME

# Username for the database connection
database.user: $DB_USER

# Password for the database connection
database.password: $DB_PASSWORD

# Weather the database connection is to be encrypted
database.useSsl: $DB_USE_SSL

# Location of this client
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

# Port to listen on for incoming maintenance requests
maintenance.server: $DB_SERVER
maintenance.port: 3591
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

    # create ca-db.pem
    ca_db="./ca-db.pem"
    echo -e "$DB_CA_CERT" > $ca_db

    truststore_password=$(generate_password)
    truststore_file="./.truststore"
    # Remove truststore file if it already exists
    [ -f "$truststore_file" ] && rm -f $truststore_file
    sudo keytool -import -trustcacerts -keystore "$truststore_file" -storepass "$truststore_password" -alias ca_cert -file "$ca_db" -noprompt

    # run.sh script
    run_script="./run.sh"
    tee "$run_script" > /dev/null <<EOT
#!/bin/bash

sudo killall java 2> /dev/null

java -Djavafx.platform=gtk -Dlogback.configurationFile=$ELWA_ROOT/logback.xml \
        -Djavax.net.ssl.trustStore=$ELWA_ROOT/.truststore -Djavax.net.ssl.trustStorePassword=$truststore_password \
        -jar raspi-client.latest.jar -verbose > log/stdout 2> log/errout
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
