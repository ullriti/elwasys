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

# Ensure the Common library is available in the local Maven repo.
mvn -q -B -f ../Common/pom.xml install -DskipTests

TEST_ARG=()
if [ "$#" -gt 0 ]; then
  TEST_ARG=(-Dtest="$1")
fi

exec xvfb-run -a --server-args="-screen 0 1024x768x24" \
  mvn -B test "${TEST_ARG[@]}"
