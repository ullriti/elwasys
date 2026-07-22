# Post-Deploy-Smoke-Test (Rollout-Gate)

Phase 6 AP6. Diese Suite ist das **Rollout-Gate** für Portal/Backend: Sie läuft **nach** einem
Deployment gegen die **frisch deployte, laufende** Umgebung und bestätigt deren
Funktionsfähigkeit. **Ein Rollout gilt erst nach GRÜNEM Smoke-Test als erfolgreich.**

Portal/Backend brauchen **kein eigenes Upgrade-/Rollback-Skript** – Rollout und Rollback laufen
über die Plattform:

- **Rollout**: `docker-compose`-Redeploy (neues Image ziehen, `docker compose up -d`) bzw.
  `helm upgrade`.
- **Rollback**: Redeploy des vorherigen Images per `docker compose` bzw. `helm rollback` – nichts
  Portal-spezifisches. Schlägt das Gate fehl, ist der Rollout **nicht** erfolgreich und über die
  Plattform zurückzurollen.

## Was das Gate prüft

`post-deploy-smoke.sh` führt zwei Schritte aus, **beide** müssen grün sein (Exit 0 nur dann):

1. **Health/Actuator** – `GET $BASE_URL/actuator/health` muss `"status":"UP"` liefern (mit ein
   paar Retries, um den Anlauf nach dem Redeploy zu überbrücken).
2. **Playwright-Smoke-Teilmenge** – eine **schlanke, strikt READ-ONLY** Teilmenge der
   bestehenden Portal-E2E-Suite (`backend/e2e/tests-smoke/smoke.spec.ts`, Config
   `backend/e2e/playwright.smoke.config.ts`, npm-Script `npm run smoke`). Sie macht **keine**
   Mutationen, hängt **nicht** am `global-setup`-DB-Seed und nimmt **nichts** über konkrete
   Produktivdaten an. Geprüft wird nur, dass die tragenden Schichten leben:
   - Login-Seite rendert (Frontend erreichbar),
   - Admin-Login erreicht das Dashboard (Auth-Pfad gegen die DB lebt),
   - Kern-Admin-Sektionen (Benutzer/Geräte/Programme) rendern (nur lesende Navigation),
   - Dashboard rendert (Portal↔DB-Pfad lebt).

## Aufruf nach einem Deployment

```bash
# Nach docker-compose-Redeploy (deploy/compose, Produktions-Port 8080):
BASE_URL=http://<host>:8080 deploy/smoke/post-deploy-smoke.sh

# Nach Helm-Upgrade (gegen die veröffentlichte Portal-URL / den Service):
BASE_URL=https://portal.example.org deploy/smoke/post-deploy-smoke.sh
```

Produktiv-Admin-Credentials setzen (Default `admin`/`admin`):

```bash
BASE_URL=https://portal.example.org \
SMOKE_ADMIN_USER=admin \
SMOKE_ADMIN_PASSWORD='<produktiv-passwort>' \
  deploy/smoke/post-deploy-smoke.sh
```

Voraussetzung: In `backend/e2e` sind die `node_modules` installiert. Fehlen sie, stößt das
Skript `npm ci` selbst an. Der Browser ist das in der Umgebung vorinstallierte Chromium
(`/opt/pw-browsers/chromium`, per `executablePath`) – kein Browser-Download nötig; per
`PLAYWRIGHT_CHROMIUM_PATH` überschreibbar.

### Env-Variablen

| Variable               | Default                  | Bedeutung                                        |
|------------------------|--------------------------|--------------------------------------------------|
| `BASE_URL`             | `http://localhost:8080`  | Basis-URL der deployten Umgebung                 |
| `SMOKE_ADMIN_USER`     | `admin`                  | Admin-Login für die Playwright-Teilmenge         |
| `SMOKE_ADMIN_PASSWORD` | `admin`                  | Admin-Passwort                                   |
| `HEALTH_RETRIES`       | `30`                     | Anzahl Health-Versuche                           |
| `HEALTH_RETRY_DELAY`   | `2`                      | Sekunden zwischen den Health-Versuchen           |
| `SMOKE_RETRIES`        | `0`                      | Playwright-Retries (an Playwright durchgereicht) |

## Ausgang

- **PASS** (Exit 0): Health `UP` **und** Playwright-Smoke grün → Rollout als erfolgreich
  bestätigt.
- **FAIL** (Exit ≠ 0): Rollout **nicht** erfolgreich → über die Plattform zurückrollen
  (`docker compose` Redeploy des alten Images bzw. `helm rollback`).

## Hinweis zur Sandbox-Verifikation

In der Entwicklungs-/CI-Sandbox läuft **kein Docker-Daemon**. Die Suite wurde daher gegen einen
im **Produktionsmodus** per `java -jar` gestarteten Server verifiziert
(`backend/e2e/scripts/start-backend.sh`, Produktionsbuild + `admin/admin`-Seed, Port 8081):

```bash
E2E_BACKEND_PORT=8081 bash backend/e2e/scripts/start-backend.sh   # frisch "deployte" Umgebung
BASE_URL=http://localhost:8081 deploy/smoke/post-deploy-smoke.sh  # Gate
```

Der Produktionsbuild ist derselbe Artefakt-Typ (`elwasys-backend.jar`), den `deploy/compose`
bzw. `deploy/helm` ausrollen; der einzige Unterschied ist die Startmethode (`java -jar` statt
Container). Das Gate selbst ist plattformunabhängig – es spricht nur `BASE_URL` an.
