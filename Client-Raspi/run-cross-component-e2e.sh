#!/bin/bash
# Run the CROSS-COMPONENT end-to-end suite (test plan P21/P22 successor, Phase 4 AP5): it
# proves the Fernwartung (remote maintenance: status/log/restart) channel end-to-end over
# the terminal's OUTGOING WebSocket connection to the backend
# (org.kabieror.elwasys.raspiclient.ws.TerminalWebSocketClient), which replaced the Alt-TCP
# protocol (Common.maintenance.*, the terminal used to listen as a server, the portal/backend
# dialed in) that this suite tested before AP5.
#
# The actual test (backend/src/test/java/.../ws/TerminalMaintenanceRealClientE2ETest.java)
# boots the backend's own Spring context (so it can call the real, Spring-managed
# TerminalMaintenanceService bean - the Portal-side vermittlung, same one AdminDashboardView
# uses) and launches a REAL, packaged Client-Raspi process (-dry, no gateway needed) as a
# subprocess pointed at that context's random port + a seeded terminal token - see the
# test's class Javadoc for the full rationale ("kleinster belastbarer Aufbau").
#
# Steps: 1. install the parent POM, 2. build the real client jar, 3. start+seed a throwaway
#        PostgreSQL database (same pattern as backend/run-backend-tests.sh - the backend
#        Spring context is started BY the JUnit test itself, not by this script, since the
#        test needs the TerminalMaintenanceService bean instance directly), 4. run the suite
#        under Xvfb (the real client subprocess needs a display for JavaFX).
# See kb/06-ui-tests.md and kb/08-test-plan.md.
set -euo pipefail

cd "$(dirname "$0")"
REPO_ROOT="$(cd .. && pwd)"
PG_VER=16

# 1. Install the aggregator parent POM into the local Maven repo ("mvn -N install"
#    installs just that parent POM) so the per-module builds below can resolve it.
#    (The former "common" module was dissolved after the migration; its classes now
#    live in Client-Raspi/src/main.)
mvn -q -B -N -f "$REPO_ROOT/pom.xml" install -DskipTests

# 2. Build the real client fat jar - the process this suite launches as "the terminal".
mvn -q -B -f "$REPO_ROOT/Client-Raspi/pom.xml" package -DskipTests
export ELWASYS_TEST_CLIENT_JAR
ELWASYS_TEST_CLIENT_JAR="$(ls "$REPO_ROOT"/Client-Raspi/target/raspi-client-*-jar-with-dependencies.jar | head -1)"
if [ -z "$ELWASYS_TEST_CLIENT_JAR" ] || [ ! -f "$ELWASYS_TEST_CLIENT_JAR" ]; then
  echo "[run-cross-component-e2e] could not find the built client jar under Client-Raspi/target"
  exit 1
fi
echo "[run-cross-component-e2e] using client jar: $ELWASYS_TEST_CLIENT_JAR"

# 2b. Resolve the JavaFX platform module jars from the local Maven repo. A plain
#     "java -jar raspi-client.jar" fails with "JavaFX runtime components are missing" on a
#     stock JDK (unlike on the field-deployed bellsoft-java21-runtime-full, which bundles
#     JavaFX as platform modules, see setup.sh/kb/05-migration-plan.md "Phase 1") - Java's
#     launcher special-cases a jar whose main class extends javafx.application.Application and
#     refuses to start unless javafx.graphics is resolvable as a named module, even though the
#     assembly-plugin fat jar already has the JavaFX classes on its classpath. Passing them
#     again via --module-path/--add-modules (in addition to the classpath -jar launch) closes
#     that gap without touching the production Main-Class/launch mechanism.
JAVAFX_VERSION="$(grep -A3 '<artifactId>javafx-fxml</artifactId>' "$REPO_ROOT/Client-Raspi/pom.xml" \
  | grep -m1 -o '<version>[^<]*' | sed 's/<version>//')"
if [ -z "$JAVAFX_VERSION" ]; then
  echo "[run-cross-component-e2e] could not determine the JavaFX version from Client-Raspi/pom.xml"
  exit 1
fi
M2_REPO="$(cd ~/.m2/repository 2>/dev/null && pwd || true)"
JAVAFX_MODULE_PATH=""
for artifact in javafx-base javafx-graphics javafx-controls javafx-fxml javafx-web javafx-media; do
  jar="$M2_REPO/org/openjfx/$artifact/$JAVAFX_VERSION/$artifact-$JAVAFX_VERSION-linux.jar"
  if [ ! -f "$jar" ]; then
    echo "[run-cross-component-e2e] missing JavaFX module jar: $jar"
    exit 1
  fi
  JAVAFX_MODULE_PATH="${JAVAFX_MODULE_PATH:+$JAVAFX_MODULE_PATH:}$jar"
done
export ELWASYS_TEST_CLIENT_JAVAFX_MODULE_PATH="$JAVAFX_MODULE_PATH"

# 3. PostgreSQL + a fresh, empty database (same pattern as backend/run-backend-tests.sh: the
#    test's own @SpringBootTest context runs Flyway against it - no manual seeding needed,
#    the test creates its own location/token via JPA, like TerminalWebSocketTest does).
sudo pg_ctlcluster "$PG_VER" main start 2>/dev/null || true
for i in $(seq 1 30); do pg_isready -q && break; sleep 1; done
pg_isready || { echo "PostgreSQL not ready"; exit 1; }
sudo -u postgres psql -q -c "ALTER USER postgres WITH PASSWORD 'postgres';"

DB_NAME="elwasys_cross_component_it"
sudo -u postgres psql -q -c "DROP DATABASE IF EXISTS ${DB_NAME};"
sudo -u postgres psql -q -c "CREATE DATABASE ${DB_NAME};"

export ELWASYS_TEST_JDBC_URL="jdbc:postgresql://localhost:5432/${DB_NAME}"
export ELWASYS_TEST_DB_USER="postgres"
export ELWASYS_TEST_DB_PASSWORD="postgres"

# 4. Run the suite. Xvfb is required here (unlike most other backend tests) because this one
#    launches a REAL JavaFX client subprocess, which inherits DISPLAY from this shell.
xvfb-run -a --server-args="-screen 0 1024x768x24" \
  mvn -B -f "$REPO_ROOT/backend/pom.xml" test -Dtest=TerminalMaintenanceRealClientE2ETest
