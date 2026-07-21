#!/bin/bash
# Verifies that the Flyway baseline migration (db/migration/V1__baseline_schema_0_4_0.sql) is
# schema-equivalent to the legacy path (Common/resources/database-init.sql +
# database-upgrade/*.sql), and that the backend starts cleanly against an existing legacy
# database via baselineOnMigrate. See kb/02-data-model.md ("Flyway-Baseline") and
# kb/05-migration-plan.md (Phase 2, AP1) for the write-up of the result.
#
# What this script does, against the local PostgreSQL cluster:
#   1. Creates a database via the legacy path (psql -f database-init.sql).
#   2. Builds the backend jar and starts it against a second, brand-new empty database -
#      Flyway runs V1 there (the "fresh database" scenario).
#   3. Dumps both schemas (pg_dump --schema-only) and diffs them. Expected, non-semantic
#      differences: the pg_dump \restrict/\unrestrict tokens (random per dump) and Flyway's own
#      "flyway_schema_history" bookkeeping table (+ its PK/index) - anything beyond that is a
#      real regression.
#   4. Starts the backend a second time, now against the *legacy-path* database from step 1,
#      to prove baselineOnMigrate baselines it (at version 1) without altering data, and that
#      the health endpoint comes up.
#
# NOTE (AP3, 2026-07-20): this script's "fresh path" (step 2) runs Flyway migrate(), which
# applies ALL pending migrations, not just V1 - since V2__widen_users_password_column.sql
# was added in AP3 (see kb/05-migration-plan.md), the fresh-DB schema now legitimately
# diverges from the untouched legacy-path DB (users.password: VARCHAR(255) vs. VARCHAR(50))
# and step 4's single-BASELINE-row assertion no longer holds either (baselineOnMigrate
# baselines at version 1, then ALSO applies V2 to the legacy-path DB - by design, that is
# the whole point of V2 existing). A re-run of this script will therefore currently report
# a "FAIL" at steps 3/4 that is NOT a regression, just this script not yet having been
# updated for migrations beyond the baseline. Left as-is (out of AP3's scope) rather than
# reworked; a future work package that adds more migrations should either retarget this
# script's "fresh path" comparison at flyway.target=1 (true V1-only check) or replace it
# with an explicit, maintained list of expected post-baseline diffs.
#
# UPDATE (Phase 5 AP3, 2026-07-21): the same "fresh path" divergence now also covers V8
# (devices.auto_end_power_threashold -> auto_end_power_threshold) and V9 (drops
# locations.client_uid/client_ip/client_port/client_last_seen) - both are additional,
# expected, non-regression diffs against the untouched legacy-path DB from step 1. Full
# rework of this script (explicit expected-diff list or flyway.target=1) is deferred to
# Phase 5 AP6 as noted above; this script is still not part of any CI/test suite.
#
# UPDATE (Phase 5 AP6, 2026-07-21, doc-only pass): the rework flagged above was intentionally
# NOT done in AP6 (AP6 is a documentation/comment cleanup work package, not a script-logic
# change) - this NOTE block is being made honest instead. By now (V1-V10) this script's
# "fresh path" (step 2) has diverged from the untouched legacy-path DB (step 1) by design in
# even more ways than V8/V9 above: V6 drops the elwaclient1/elwaapi roles and the
# elwaclients group that database-init.sql still creates, V7 removes the seeded default
# admin password (see ADMIN_ROW check below - "admin" now seeds with a NULL/placeholder
# password, not the legacy-path DB's default hash), and V10 drops the elwaapi-app columns/
# tables (auth_key/access_key/app_id, reservations, foreign_authkeys) entirely. In short: this
# script's core premise ("the fresh Flyway path and the legacy path produce the same schema")
# has not held since AP3's V2 and is now further from true with every migration since - it
# is a HISTORICAL tool from Phase 2 AP1 (when V1 was the only migration and baseline-
# equivalence was the actual property being verified), not a maintained regression check. A
# real V1-only baseline-equivalence check would need to run Flyway with `flyway.target=1`
# instead of the current unbounded `migrate()` (step 2) so it only applies V1, matching what
# step 1's legacy path actually represents; nobody has needed that check since, so it has not
# been rebuilt. Re-running this script as-is today will very likely FAIL at steps 3/4 - that
# is expected and NOT a regression signal.
#
# Requires: JDK 21, Maven, local PostgreSQL 16 (pg_ctlcluster) + sudo. Not part of the routine
# test suite (run-backend-tests.sh) - this is the documented one-off verification the AP1
# work order asked for; safe to re-run any time to re-check the V1-era baseline, keeping the
# above caveats in mind.
set -euo pipefail

cd "$(dirname "$0")/.."

OLDWAY_DB="elwasys"
FLYWAY_DB="elwasys_flywaybaseline_verify"
PORT_FRESH=18080
PORT_BASELINE=18081

sudo pg_ctlcluster 16 main start 2>/dev/null || true
for i in $(seq 1 30); do pg_isready -q && break; sleep 1; done
pg_isready
sudo -u postgres psql -q -c "ALTER USER postgres WITH PASSWORD 'postgres';"

echo "== 1) Legacy path: database-init.sql -> ${OLDWAY_DB} =="
sudo -u postgres psql -q -c "DROP DATABASE IF EXISTS ${OLDWAY_DB};"
sudo -u postgres psql -q < Common/resources/database-init.sql

echo "== 2) Fresh path: Flyway baseline migration -> ${FLYWAY_DB} =="
sudo -u postgres psql -q -c "DROP DATABASE IF EXISTS ${FLYWAY_DB};"
sudo -u postgres psql -q -c "CREATE DATABASE ${FLYWAY_DB};"

mvn -B -f pom.xml package -pl backend -DskipTests -q

ELWASYS_DB_URL="jdbc:postgresql://localhost:5432/${FLYWAY_DB}" \
ELWASYS_DB_USER="postgres" \
ELWASYS_DB_PASSWORD="postgres" \
java -jar backend/target/elwasys-backend.jar --server.port=${PORT_FRESH} > /tmp/verify-backend-fresh.log 2>&1 &
FRESH_PID=$!
for i in $(seq 1 30); do curl -sf "http://localhost:${PORT_FRESH}/actuator/health" > /dev/null && break; sleep 1; done
FRESH_HEALTH="$(curl -s "http://localhost:${PORT_FRESH}/actuator/health")"
kill "$FRESH_PID" 2>/dev/null || true
wait "$FRESH_PID" 2>/dev/null || true
echo "Fresh-DB health: ${FRESH_HEALTH}"
[[ "${FRESH_HEALTH}" == *'"status":"UP"'* ]] || { echo "FAIL: fresh-DB backend did not report UP"; exit 1; }

echo "== 3) Schema diff (pg_dump --schema-only) =="
DUMP_DIR="$(mktemp -d)"
sudo -u postgres pg_dump --schema-only --no-owner --no-privileges -d "${OLDWAY_DB}" > "${DUMP_DIR}/oldway.sql"
# -T excludes Flyway's own bookkeeping table (+ its PK/index) cleanly at the pg_dump level -
# it is not part of the application schema, so it's not a meaningful comparison target.
sudo -u postgres pg_dump --schema-only --no-owner --no-privileges -T flyway_schema_history \
  -d "${FLYWAY_DB}" > "${DUMP_DIR}/flyway.sql"

# \restrict / \unrestrict tokens are random per pg_dump invocation (pg_dump >= 16 security
# feature) - strip them, they're not schema content.
sed -E '/^\\(un)?restrict /d' "${DUMP_DIR}/oldway.sql" > "${DUMP_DIR}/oldway.normalized.sql"
sed -E '/^\\(un)?restrict /d' "${DUMP_DIR}/flyway.sql" > "${DUMP_DIR}/flyway.normalized.sql"

if diff -u "${DUMP_DIR}/oldway.normalized.sql" "${DUMP_DIR}/flyway.normalized.sql"; then
  echo "PASS: schema-only dumps are identical after normalization (dumps: ${DUMP_DIR})"
else
  echo "FAIL: schema diff found beyond the expected flyway_schema_history/\\restrict noise (dumps: ${DUMP_DIR})"
  exit 1
fi

echo "== 4) baselineOnMigrate against the legacy-path DB (${OLDWAY_DB}) =="
ELWASYS_DB_URL="jdbc:postgresql://localhost:5432/${OLDWAY_DB}" \
ELWASYS_DB_USER="postgres" \
ELWASYS_DB_PASSWORD="postgres" \
java -jar backend/target/elwasys-backend.jar --server.port=${PORT_BASELINE} > /tmp/verify-backend-baseline.log 2>&1 &
BASELINE_PID=$!
for i in $(seq 1 30); do curl -sf "http://localhost:${PORT_BASELINE}/actuator/health" > /dev/null && break; sleep 1; done
BASELINE_HEALTH="$(curl -s "http://localhost:${PORT_BASELINE}/actuator/health")"
BASELINE_ROW="$(sudo -u postgres psql -d "${OLDWAY_DB}" -tA -c "SELECT version || '|' || type || '|' || success FROM flyway_schema_history;")"
ADMIN_ROW="$(sudo -u postgres psql -d "${OLDWAY_DB}" -tA -c "SELECT username || '|' || is_admin FROM users WHERE username = 'admin';")"
kill "${BASELINE_PID}" 2>/dev/null || true
wait "${BASELINE_PID}" 2>/dev/null || true

echo "Baseline-DB health: ${BASELINE_HEALTH}"
echo "flyway_schema_history row: ${BASELINE_ROW}"
echo "admin user (untouched): ${ADMIN_ROW}"

[[ "${BASELINE_HEALTH}" == *'"status":"UP"'* ]] || { echo "FAIL: legacy-DB backend did not report UP"; exit 1; }
# Note: boolean || text concatenation casts to the SQL "true"/"false" spelling, not psql's
# raw 't'/'f' output format.
[[ "${BASELINE_ROW}" == "1|BASELINE|true" ]] || { echo "FAIL: expected a single BASELINE row at version 1, got: ${BASELINE_ROW}"; exit 1; }
[[ "${ADMIN_ROW}" == "admin|true" ]] || { echo "FAIL: admin seed user missing/changed: ${ADMIN_ROW}"; exit 1; }

echo "== All checks passed =="
