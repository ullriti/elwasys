#!/usr/bin/env bash
# Post-Deploy-Smoke-Test (Rollout-Gate) - Phase 6 AP6, siehe docs/kb/05-migration-plan.md "Phase 6"
# und deploy/smoke/README.md.
#
# Läuft NACH einem Deployment gegen die frisch deployte, LAUFENDE Portal/Backend-Umgebung und
# bestätigt deren Funktionsfähigkeit. Ein Rollout (docker-compose-Redeploy bzw. Helm-Upgrade)
# gilt erst nach GRÜNEM Smoke-Test als erfolgreich; schlägt das Gate fehl, ist der Rollout NICHT
# erfolgreich und über die Plattform zurückzurollen (compose-Redeploy der alten Images bzw.
# `helm rollback`) - Portal/Backend brauchen dafür KEIN eigenes Rollback-Skript.
#
# Zwei Schritte, beide müssen grün sein:
#   1. Health:     GET $BASE_URL/actuator/health  ->  "status":"UP"  (mit Retries für den Start)
#   2. Playwright: die schlanke, strikt READ-ONLY Smoke-Teilmenge (backend/e2e/tests-smoke)
#                  gegen $BASE_URL (npm run smoke).
#
# Konfiguration per Env:
#   BASE_URL              Basis-URL der deployten Umgebung (Default http://localhost:8080 -
#                         der Produktions-Port aus deploy/compose/docker-compose.yml)
#   SMOKE_ADMIN_USER      Admin-Login für die Playwright-Teilmenge (Default admin)
#   SMOKE_ADMIN_PASSWORD  Admin-Passwort (Default admin)
#   HEALTH_RETRIES        Anzahl Health-Versuche (Default 30)
#   HEALTH_RETRY_DELAY    Sekunden zwischen den Versuchen (Default 2)
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
SMOKE_ADMIN_USER="${SMOKE_ADMIN_USER:-admin}"
SMOKE_ADMIN_PASSWORD="${SMOKE_ADMIN_PASSWORD:-admin}"
HEALTH_RETRIES="${HEALTH_RETRIES:-30}"
HEALTH_RETRY_DELAY="${HEALTH_RETRY_DELAY:-2}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
E2E_DIR="$REPO_ROOT/backend/e2e"

echo "=================================================================="
echo " elwasys Post-Deploy-Smoke-Test (Rollout-Gate)"
echo "   Ziel-Umgebung : $BASE_URL"
echo "   Admin-User    : $SMOKE_ADMIN_USER"
echo "=================================================================="

fail() {
  echo ""
  echo "=================================================================="
  echo " SMOKE-TEST: FAIL - $1"
  echo " -> Rollout NICHT erfolgreich. Über die Plattform zurückrollen"
  echo "    (docker-compose-Redeploy der alten Images bzw. 'helm rollback')."
  echo "=================================================================="
  exit 1
}

# --- Schritt 1: Health/Actuator ------------------------------------------------
echo ""
echo "[1/2] Health-Check: GET $BASE_URL/actuator/health (erwartet status=UP)"
health_ok=0
for i in $(seq 1 "$HEALTH_RETRIES"); do
  body="$(curl -fsS --max-time 10 "$BASE_URL/actuator/health" 2>/dev/null || true)"
  if printf '%s' "$body" | grep -q '"status":"UP"'; then
    echo "      Versuch $i/$HEALTH_RETRIES: UP  ($body)"
    health_ok=1
    break
  fi
  echo "      Versuch $i/$HEALTH_RETRIES: noch nicht UP, warte ${HEALTH_RETRY_DELAY}s ..."
  sleep "$HEALTH_RETRY_DELAY"
done
[ "$health_ok" -eq 1 ] || fail "Health-Endpoint wurde nicht 'UP' innerhalb von $HEALTH_RETRIES Versuchen."
echo "      Health OK."

# --- Schritt 2: Playwright-Smoke-Teilmenge ------------------------------------
echo ""
echo "[2/2] Playwright-Smoke-Teilmenge (read-only) gegen $BASE_URL"

if [ ! -d "$E2E_DIR/node_modules" ]; then
  echo "      node_modules fehlen in backend/e2e - installiere (npm ci) ..."
  ( cd "$E2E_DIR" && npm ci )
fi

if ( cd "$E2E_DIR" \
      && E2E_BASE_URL="$BASE_URL" \
         SMOKE_ADMIN_USER="$SMOKE_ADMIN_USER" \
         SMOKE_ADMIN_PASSWORD="$SMOKE_ADMIN_PASSWORD" \
         npm run smoke ); then
  echo "      Playwright-Smoke-Teilmenge grün."
else
  fail "Playwright-Smoke-Teilmenge fehlgeschlagen (siehe Ausgabe oben)."
fi

echo ""
echo "=================================================================="
echo " SMOKE-TEST: PASS - Health UP UND Playwright-Smoke grün."
echo " -> Rollout als erfolgreich bestätigt."
echo "=================================================================="
exit 0
