#!/bin/bash
# Shared helper for Client-Raspi/run-ui-tests.sh and run-client-e2e.sh (Phase 4 AP4, see
# kb/06-ui-tests.md "Testharness"): builds the backend jar, starts ONE instance for the whole
# test run (Flyway migrates the same test database that database-init.sql initialized -
# widens users.password, adds terminal_tokens/terminal_idempotency_keys, see
# kb/02-data-model.md), seeds a single terminal token for the "Default" location via
# token-cli, and exports ELWASYS_TEST_BACKEND_URL/ELWASYS_TEST_BACKEND_TOKEN for the E2E
# tests to read (see Client-Raspi/src/test/.../application/TestBackend.java).
#
# Intended to be `source`d, not executed directly, so the exported env vars and the EXIT
# trap (which stops the backend) apply to the calling script's shell.
#
# Requires: the elwasys database already seeded (database-init.sql), Common installed into
# the local Maven repo (see callers).

TEST_BACKEND_PORT="${TEST_BACKEND_PORT:-8099}"
export ELWASYS_TEST_BACKEND_URL="http://localhost:${TEST_BACKEND_PORT}/"

start_test_backend() {
  local repo_root
  repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
  local backend_jar="$repo_root/backend/target/elwasys-backend.jar"
  local backend_log
  backend_log="$(mktemp -t elwasys-test-backend-log.XXXXXX)"

  echo "[start-test-backend] building backend jar"
  mvn -q -B -f "$repo_root/backend/pom.xml" package -DskipTests

  echo "[start-test-backend] starting backend on port ${TEST_BACKEND_PORT}"
  ELWASYS_DB_URL="jdbc:postgresql://localhost:5432/elwasys" \
  ELWASYS_DB_USER="postgres" ELWASYS_DB_PASSWORD="postgres" \
    java -jar "$backend_jar" --server.port="$TEST_BACKEND_PORT" \
    > "$backend_log" 2>&1 &
  local backend_pid=$!

  # Stop the backend (and clean up the log) whenever this script exits, whether
  # normally, on error, or via Ctrl-C - a leaked background JVM would otherwise
  # occupy the port on the next run.
  trap 'kill "'"$backend_pid"'" 2>/dev/null || true; rm -f "'"$backend_log"'"' EXIT

  echo "[start-test-backend] waiting for the backend to become healthy"
  local ready=0
  for _ in $(seq 1 60); do
    if curl -fsS "http://localhost:${TEST_BACKEND_PORT}/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
      ready=1
      break
    fi
    if ! kill -0 "$backend_pid" 2>/dev/null; then
      echo "[start-test-backend] backend process exited early - log follows:"
      cat "$backend_log"
      exit 1
    fi
    sleep 2
  done
  if [ "$ready" -ne 1 ]; then
    echo "[start-test-backend] backend did not become healthy in time - log follows:"
    cat "$backend_log"
    exit 1
  fi

  echo "[start-test-backend] seeding a terminal token for location 'Default'"
  local token_output
  token_output="$(ELWASYS_DB_URL="jdbc:postgresql://localhost:5432/elwasys" \
    ELWASYS_DB_USER="postgres" ELWASYS_DB_PASSWORD="postgres" \
    java -jar "$backend_jar" --spring.profiles.active=token-cli \
    --location=Default --label=e2e-test-harness 2>&1)"
  export ELWASYS_TEST_BACKEND_TOKEN
  ELWASYS_TEST_BACKEND_TOKEN="$(echo "$token_output" | sed -n 's/^ *Token: *//p' | head -1)"
  if [ -z "$ELWASYS_TEST_BACKEND_TOKEN" ]; then
    echo "[start-test-backend] could not extract a token from token-cli output:"
    echo "$token_output"
    exit 1
  fi
  echo "[start-test-backend] backend ready at ${ELWASYS_TEST_BACKEND_URL}, token seeded"
}
