#!/bin/bash
# Run the Client-Raspi JavaFX UI tests headlessly via Xvfb.
# Usage:
#   ./run-ui-tests.sh                 # run all UI tests
#   ./run-ui-tests.sh HeadlessFxSmokeTest   # run a single test class
#
# Requires: JDK, Maven, Xvfb (installed by the SessionStart hook / cloud-init).
# See kb/06-ui-tests.md and kb/07-cloud-init.md.
set -euo pipefail

cd "$(dirname "$0")"

# Ensure the Common library (and its parent POM) are available in the local
# Maven repo. Building via the root reactor (-pl Common -am) also installs
# the aggregator parent POM, which "mvn -f Common/pom.xml install" alone does
# not — and Client-Raspi's dependency resolution needs it on the local repo.
mvn -q -B -f ../pom.xml install -pl Common -am -DskipTests

# The E2E tests seed fixtures via JDBC as the postgres superuser (they need to
# clean up credit_accounting, which the elwaportal role may not delete). Give
# postgres a password the driver can use over TCP. Best-effort: skipped when
# there is no local PostgreSQL / sudo (e.g. a pure unit-test run).
sudo -u postgres psql -q -c "ALTER USER postgres WITH PASSWORD 'postgres';" 2>/dev/null || true

TEST_ARG=()
if [ "$#" -gt 0 ]; then
  TEST_ARG=(-Dtest="$1")
fi

exec xvfb-run -a --server-args="-screen 0 1024x768x24" \
  mvn -B test "${TEST_ARG[@]}"
