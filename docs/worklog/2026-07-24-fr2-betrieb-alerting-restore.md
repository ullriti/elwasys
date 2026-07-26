# 2026-07-24 — FR-2: Betrieb (Alerting, Restore, Cutover-Preflight, Deployment-Parität)

**Ziel:** Das DevOps-Arbeitspaket FR-2 aus der [SYNTHESE.md](../reviews/final/SYNTHESE.md) der
finalen Review – die vier Betriebs-/Konfigurations-Hoch-Findings H4–H7 (Issues #83–#86) plus die
Betriebs-Mittelfunde (#89) – so beheben, dass jede Lücke als **mitgeliefertes, getestetes**
Artefakt schließt (nicht nur als Runbook-Empfehlung).

## Erledigt

- **H4 Alerting-Kanal verdrahtet (#83)** – neues [`deploy/monitoring/`](../../deploy/monitoring/):
  `elwasys-health-alert.sh` pollt `/actuator/health/operational` (Nichterreichbarkeit =
  Backend/DB-down zählt als Alarm), dazu Zertifikats-Ablauf und Plattenplatz (#89); Zustellung
  per Pushover und/oder Mail, zustandswechsel-basiertes Anti-Spam mit periodischer Wiederholung
  und Recovery-Meldung. systemd-Service+Timer (5 min) und Cron-Beispiel, `*.env.example`, README
  mit Einrichtung als **Pflicht-Gate** und Alarm-Probe. `elwasys-health-alert-selftest.sh` prüft
  die volle Alarm-/Anti-Spam-/Recovery-Logik **offline** über Sonden-/Zustell-Hooks (8 Szenarien).
- **H5 Restore ausgearbeitet + geskriptet (#84)** – neues [`deploy/backup/`](../../deploy/backup/):
  `restore-db.sh` (Owner-Rolle → leere DB → `gunzip|psql` → Stichprobe; destruktiv, mit
  Bestätigung/`--yes`, `--dry-run`), `backup-db.sh` (geskripteter `pg_dump` direkt/`docker exec`
  + Retention), README mit RPO/RTO und dem Backup-Scope über die DB hinaus (Secrets,
  Terminal-`elwasys.properties`, SD-Karten-Journal als benanntes Nicht-Backup, Offsite).
  `restore-db-selftest.sh` prüft Argument-Validierung + `--dry-run`-Plan beider Skripte offline.
- **H6 Cutover-Preflight entstaubt (#85)** – `verify-cutover-migration.sh` leitet die erwartete
  Flyway-Historie jetzt aus dem Migrationsordner **ab** (V1 = Baseline, V2..V<neueste> = SQL)
  statt hart bis V10; `verify-rollback.sh` teilt dieselbe Quelle über den neuen
  `--print-expected-history`-Haken. `verify-cutover-migration-selftest.sh` gleicht Skript ↔
  Migrationsordner ab (fängt genau das stille Veralten, das V11 sichtbar machte).
- **H7 `ELWASYS_PORTAL_BASE_URL` durchgereicht (#86)** – Compose (`environment:`-Block mit
  `:?`-Guard) + `.env.example`; Helm `passwordReset.{enabled,baseUrl}` mit `required`-Guard in der
  ConfigMap (analog `secret.password`). Ohne Wert bricht der Deploy ab, statt Reset-Mails still auf
  `http://localhost:8080` zu verlinken.
- **#89 Betriebs-Mittelfunde (devops-Anteil):** Compose zieht per Default das GHCR-Release-Image
  (`ELWASYS_BACKEND_IMAGE`, lokaler Build als `docker-compose.build.yml`-Overlay); Helm-`imageTag`-
  Guard fängt den `0.0.0-local-development`-Sentinel ab und der Release-Workflow hebt
  `Chart.yaml` `appVersion`; NTP-Sync in `setup.sh` (systemd-timesyncd aktivieren + verifizieren)
  und ein wiederkehrender Sync-Check im `auto-update-watchdog.sh`; Cert-Ablauf ist Teil des
  H4-Alerting-Skripts.
- **CI:** neuer Job `cutover-scripts` (offline: `bash -n` + die drei neuen Selbsttests H4/H5/H6);
  `helm`-Job um `--set image.tag`/`--set passwordReset.baseUrl` ergänzt (Guards rendern grün);
  Release-Workflow-YAML-Fix (appVersion-sed in Block-Skalar).
- **Runbook/Spec:** CUTOVER-RUNBOOK Kap. 7a (Restore-Weg + Backup-Scope + RPO/RTO), Kap. 7b
  (verdrahteter Alarmkanal statt „empfohlen"), Preflight-Gates für Alarm + Portal-URL; Spec 0001
  Generalprobe-Punkte 3/5 zeigen auf die neuen Artefakte.

## Entscheidungen

- **Direkt bearbeitet statt delegiert:** FR-2 ist thematisch eng um `deploy/`+`docs/` gekoppelt
  (viele kleine, über Runbook ↔ Skripte ↔ Compose ↔ Helm konsistent zu haltende Änderungen) –
  ein Kontext hält diese Naht robuster als eine Zerlegung. Bewusst am AP-Anfang so entschieden.
- **Skripte offline testbar gemacht:** die drei neuen Selbsttests laufen ohne PostgreSQL/Docker/
  Netz (Sonden-/Zustell-/Ausführungs-Hooks, `--dry-run`) und hängen im CI-Gate – der *echte*
  Restore-/Alarm-/Cutover-Lauf bleibt bewusst ein Generalprobe-Schritt des Auftraggebers (kein
  Docker-Daemon/keine Produktiv-DB in der Sandbox).
- **Guards statt stiller Defaults:** Portal-URL (Compose `:?`, Helm `required`) und Helm-Image-Tag
  brechen bei fehlendem Wert laut ab – konsistent mit dem bestehenden `POSTGRES_PASSWORD`-/
  `secret.password`-Muster.

## Nachtrag (Auftraggeber-Entscheidung, 2026-07-26)

Der ursprüngliche Zuschnitt dieses AP ließ zwei Punkte bewusst offen; der Auftraggeber hat beide
anders entschieden – umgesetzt und hier nachgetragen:

- **Externer Uptime-Monitor ist PFLICHT, nicht Ergänzung** (H4/#83). Das lokale Poll-Skript hat
  eine bauartbedingte blinde Stelle: fällt der ganze Host aus, läuft auch der Poller nicht mehr –
  ausgerechnet im schwersten Fehlerfall käme kein Alarm. `deploy/monitoring/README.md` beschreibt
  daher jetzt **zwei Pflicht-Stufen** (lokales Skript + externer Monitor, wahlweise
  Endpoint-Check oder Dead-Man's-Switch per healthchecks.io-Ping), und die Alarm-Probe muss für
  **beide** Wege durchlaufen.
- **#89 Dead-Letter-Sichtbarkeit doch in diesem AP** (statt als Folge-AP): siehe
  [ADR 0022](../architecture/0022-dead-letter-sichtbarkeit.md). Ein Dead-Letter ist eine verlorene
  Offline-Buchung (Geld) und stand bisher nur im lokalen Pi-Log. Jetzt: das Terminal meldet
  Vorfälle über den bestehenden Wartungs-WebSocket (`OFFLINE_INCIDENT`, persistente Outbox mit
  Quittung – ein Vorfall entsteht typischerweise genau dann, wenn die Verbindung weg ist),
  das Backend persistiert sie (Migration V12) und hält über einen Health-Indicator den
  Betriebsalarm, bis ein Admin den Vorfall im Portal **quittiert**. Damit schließt sich der Kreis
  vom stillen Log-Eintrag bis zum Menschen.

## Offen / nächster Schritt

- FR-3 (Tests: deCONZ-Reconnect, DYNAMIC-E2E, Determinismus) und die Generalprobe nach
  Spec 0001 (echter Restore-/Alarm-/Cutover-Lauf, jetzt inkl. Alarm-Probe für beide Stufen).
- **Testinfrastruktur-Schwachpunkt (neu entdeckt, [#97](https://github.com/ullriti/elwasys/issues/97)):**
  beide Test-Harnesse (`start-test-backend.sh`, `backend/e2e/global-setup.ts`) gateten auf das
  aggregierte `/actuator/health`. Da dort die betrieblichen Indicators hängen und beim Start nie
  ein Terminal verbunden ist, blockierte jeder Standort-mit-Gerät den Lauf dauerhaft – und der
  Fehlschlag maskierte sich selbst (Exit-Code 0 ohne einen einzigen ausgeführten Test). Mit dem
  Offline-Vorfalls-Indicator wäre es noch schärfer geworden: der setzt sich nur durch manuelle
  Quittierung zurück, ein Rückstand hätte die Suite dauerhaft blockiert. **In diesem AP behoben**
  (beide Gates auf `/actuator/health/readiness`); der verbleibende Rest – der maskierende
  Exit-Code – ist in #97 festgehalten.

## Referenzen

- Issues #83/#84/#85/#86/#89 (Epic #94), [SYNTHESE.md](../reviews/final/SYNTHESE.md) FR-2,
  [R5-betrieb.md](../reviews/final/R5-betrieb.md)
- `deploy/monitoring/`, `deploy/backup/`, `deploy/cutover/verify-cutover-migration.sh`,
  `deploy/compose/`, `deploy/helm/elwasys-backend/`, `Client-Raspi/setup.sh`,
  `deploy/terminal/auto-update-watchdog.sh`, `.github/workflows/{ci,maven-publish}.yml`,
  `deploy/CUTOVER-RUNBOOK.md`, `docs/specs/0001-finale-review.md`
