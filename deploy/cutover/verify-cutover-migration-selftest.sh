#!/bin/bash
# Offline-Selbsttest für die aus dem Migrationsordner ABGELEITETE Flyway-Historie-Erwartung
# (H6/#85). Kein PostgreSQL/Maven/Backend nötig - nur der --print-expected-history-Haken von
# verify-cutover-migration.sh. Zweck: sicherstellen, dass die Erwartung des Cutover-
# Verifikationsskripts NIE wieder still gegenüber dem Migrationsordner veraltet (genau der
# Fehler, den V11 sichtbar gemacht hat: das Skript erwartete nur bis V10). Läuft in der CI
# (siehe .github/workflows/ci.yml, Job "cutover-scripts").
set -euo pipefail

cd "$(dirname "$0")/../.."   # Repo-Wurzel

MIGRATION_DIR="backend/src/main/resources/db/migration"
SCRIPT="deploy/cutover/verify-cutover-migration.sh"

FAIL=0
check() {
  local desc="$1" expected="$2" actual="$3"
  if [[ "${actual}" == "${expected}" ]]; then
    echo "PASS: ${desc}"
  else
    echo "FAIL: ${desc}"
    echo "      erwartet: '${expected}'"
    echo "      erhalten: '${actual}'"
    FAIL=$((FAIL + 1))
  fi
}

# Die vom Skript abgeleitete Erwartung ...
DERIVED="$(bash "${SCRIPT}" --print-expected-history)"

# ... unabhängig aus dem Ordner nachgerechnet: sortierte Versionsnummern der V<n>__*.sql.
VERSIONS="$(ls "${MIGRATION_DIR}"/V*__*.sql | sed -E 's#.*/V([0-9]+)__.*#\1#' | sort -n)"
COUNT="$(echo "${VERSIONS}" | wc -l | tr -d ' ')"
MAXV="$(echo "${VERSIONS}" | tail -n1)"

echo "== Selbsttest: abgeleitete Flyway-Historie vs. Migrationsordner (${COUNT} Migrationen, V1..V${MAXV}) =="

# 1) Genau eine Zeile je Migrationsdatei.
check "Zeilenzahl == Anzahl V<n>__-Migrationen" "${COUNT}" "$(echo "${DERIVED}" | wc -l | tr -d ' ')"

# 2) V1 ist die Baseline, alle anderen SQL.
check "erste Zeile ist die Baseline (V1)" "1|BASELINE|true" "$(echo "${DERIVED}" | head -n1)"
check "V1 taucht NICHT als SQL auf (nur BASELINE)" "0" "$(echo "${DERIVED}" | grep -c '^1|SQL|' || true)"

# 3) Die neueste Migration ist als SQL vertreten (der konkrete Regressionsschutz: als V11 kam,
#    fehlte sie in der handgepflegten Liste).
check "neueste Migration V${MAXV} ist als SQL erwartet" "${MAXV}|SQL|true" "$(echo "${DERIVED}" | tail -n1)"

# 4) Jede V<n>__-Datei hat genau eine passende Erwartungszeile (V1 BASELINE, sonst SQL).
for v in ${VERSIONS}; do
  if [[ "${v}" == "1" ]]; then
    want="1|BASELINE|true"
  else
    want="${v}|SQL|true"
  fi
  check "Migration V${v} erwartet als '${want}'" "1" "$(echo "${DERIVED}" | grep -c "^${want}$" || true)"
done

echo
if [[ "${FAIL}" == "0" ]]; then
  echo "ALLE SELBSTTEST-CHECKS PASS."
  exit 0
else
  echo "${FAIL} SELBSTTEST-CHECK(S) FEHLGESCHLAGEN."
  exit 1
fi
