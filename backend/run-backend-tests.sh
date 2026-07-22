#!/bin/bash
# Run the backend module's tests against the local PostgreSQL cluster.
#
# This environment has no running Docker daemon, so Testcontainers (the tests' default,
# see BackendApplicationIT / TestPostgres) cannot start a throwaway PostgreSQL container here.
# Instead, this script prepares a dedicated database on the local PostgreSQL cluster (the same
# one Client-Raspi/run-ui-tests.sh and the CI "Start & seed PostgreSQL" step use) and points the
# tests at it via the ELWASYS_TEST_JDBC_URL override. In CI (Docker available), the tests run
# unmodified against Testcontainers instead - this script is only needed/used locally.
#
# Usage:
#   ./run-backend-tests.sh
#
# Requires: JDK 21, Maven, local PostgreSQL 16 (pg_ctlcluster) + sudo, as set up by the
# SessionStart hook / cloud-init. See kb/04-build-and-run.md, kb/07-cloud-init.md.
set -euo pipefail

cd "$(dirname "$0")/.."

# Ensure the aggregator parent POM is available in the local Maven repo; "mvn -N install"
# installs just that parent POM. (The backend never had a runtime dependency on the former
# "common" module; since the migration dissolved it, the auth parity tests reproduce the
# legacy SHA1 format locally via LegacySha1 - see kb/05-migration-plan.md.)
mvn -q -B -N -f pom.xml install -DskipTests

DB_NAME="elwasys_backend_it"

# Start PostgreSQL if it isn't running yet (best-effort, mirrors run-ui-tests.sh / CI).
sudo pg_ctlcluster 16 main start 2>/dev/null || true
for i in $(seq 1 30); do pg_isready -q && break; sleep 1; done
pg_isready

# Tests connect over TCP as the postgres superuser (needed to freely drop/recreate the test
# database and to have full DDL rights for the Flyway baseline migration, incl. role creation).
sudo -u postgres psql -q -c "ALTER USER postgres WITH PASSWORD 'postgres';"

# Fresh, empty database for every run: this is the "Flyway migrates a brand-new database"
# scenario (see kb/02-data-model.md). The "baselineOnMigrate against an existing/legacy
# database" scenario is verified separately, see backend/verify-schema-baseline.sh.
sudo -u postgres psql -q -c "DROP DATABASE IF EXISTS ${DB_NAME};"
sudo -u postgres psql -q -c "CREATE DATABASE ${DB_NAME};"

export ELWASYS_TEST_JDBC_URL="jdbc:postgresql://localhost:5432/${DB_NAME}"
export ELWASYS_TEST_DB_USER="postgres"
export ELWASYS_TEST_DB_PASSWORD="postgres"

exec mvn -B -f pom.xml test -pl backend
