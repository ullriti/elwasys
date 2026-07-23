# 2026-07-23 — Pre-Launch AP6: Deployment, Betrieb & Cutover

**Ziel:** Das Arbeitspaket AP6 der Pre-Launch-Review (Epic #66) umsetzen – Produktionsreife
von Deployment/Betrieb: Zeitzone, TLS-Pflicht, Dauerbetrieb (Backup/Alerting/Retention),
sicheres Terminal-Update, Deployment-Konsistenz – plus die offene Vaadin-Lizenz-🧩.

## Vorgehen
Mehrkomponentig, daher zerlegt: Backend-Java-Anteil (#32) an den `backend`-Agenten,
Deploy-/Betriebs-Anteil (#31/#32-Deploy/#34/#35/#60/#62/#63/#64) an den `devops`-Agenten
(parallel, disjunkte Pfade); übergreifende Doku, ADR, das Repo-Tracking-Issue und das
Review-Gate im Hauptkontext. Drei 🧩-Auftraggeberentscheidungen vorab geklärt.

## Erledigt
- **#31 Zeitzone:** Backend-`Dockerfile`, Compose und Helm fest auf `Europe/Berlin`
  (an Terminal-TZ angeglichen); Preflight-Gate + Runbook-Pflichtprüfpunkt „Terminal- und
  Backend-TZ müssen übereinstimmen". Verhindert, dass jede Nachmeldung in den
  Ersetzungszweig von `ClientTimestampPolicy` fällt und DYNAMIC-Preise über die DST-Nacht
  falsch werden.
- **#32 Dauerbetrieb:** Purge-Job (`IdempotencyKeyRetentionScheduler`, täglich, Default 30 Tage,
  konfigurierbar) gegen unbegrenztes Wachstum von `terminal_idempotency_keys`; zwei betriebliche
  Custom-Health-Indicators (`TerminalConnectivityHealthIndicator`,
  `ExpiredExecutionsHealthIndicator`) → `/actuator/health` = `OUT_OF_SERVICE`/503 als
  Alerting-Grundlage (Details nur `when-authorized`, Actuator-Security unverändert). Runbook-Kapitel
  „Dauerbetrieb" (pg_dump-Cron + Retention, Health-Alerting, Log-Rotation); Compose-`logging`-Limits.
- **#60 Terminal-Totalausfall:** Ins Runbook aufgenommen (hängende unabgerechnete Execution →
  Guthaben reserviert → ExpiredExecutions-View/Health-Alert) inkl. der Auftraggeber-Info, dass
  die Steckdose eingeschaltet bleibt (Maschine läuft unbeaufsichtigt weiter). Vom Backend über den
  Expired-Health-Indicator sichtbar gemacht.
- **#34 Watchdog-Endlosschleife:** Fehlgeschlagene Zielversion wird in `.update-failed` gemerkt
  und nicht erneut versucht (bis eine andere Version erscheint); gecachtes kaputtes Jar wird beim
  Rollback gelöscht (`update.sh --force-download`); `.update-target` auch im Fehlerfall konsumiert.
- **#35 TLS-Pflicht:** Compose bindet 8080 nur auf `127.0.0.1` (TLS-Proxy davor); Runbook macht
  `https://` zum Gate (Voraussetzungen + Schritt 3a/3d); Helm-Ingress-Hinweis (cert-manager).
- **#62 Jar-Integrität:** Release-Workflow veröffentlicht `raspi-client-<tag>.jar.sha256`;
  `update.sh`/`setup.sh` erzwingen `sha256sum -c` + `unzip -t`, verwerfen `.part` bei Fehlschlag.
- **#63 setup.sh:** idempotenter Download (`.part`+`mv`, `ln -sfn`); enge sudoers-Regel
  (`NOPASSWD: /usr/bin/killall java`); Kill-Exit-Code geprüft → kein grundloser Rollback bei
  fehlenden Rechten (schließt eine Quelle der Watchdog-Schleife).
- **#64 Deployment-Konsistenz:** alle Repo-/GHCR-Referenzen auf **`ullriti/elwasys`**
  vereinheitlicht (Kopf-Variable `ELWA_GITHUB_REPO`); Preflight leitet erwartete Migrationsversion
  aus `db/migration/` ab (nicht mehr hart „10"); Helm-`required`-Guard gegen leeres DB-Passwort +
  CI-`helm lint`/`template`-Job; `--no-deps backend` dokumentiert; realistisches Cron-Beispiel +
  „Skripte aufs Gerät kopieren"-Schritt.
- **#33 Vaadin-Lizenz (🧩):** Restrisiko bewusst akzeptiert, in [ADR 0019](../architecture/0019-ap6-vaadin-lizenz-restrisiko.md)
  eingefroren; Restrisiko-Tabelle in [05-migration-plan.md](../kb/05-migration-plan.md) ergänzt.
- **Tracking-Issue #75** angelegt: Checkliste zum Umstellen aller Referenzen, falls das Projekt
  später nach `kabieror/elwasys` umzieht (Java-Namespace `org.kabieror.*` bleibt unverändert).

## Entscheidungen (Auftraggeber, 2026-07-23)
- **Vaadin-Lizenz (#33):** Restrisiko akzeptieren + ADR (kein Subscription-Abschluss, kein
  Versionswechsel vor Launch) → ADR 0019.
- **Kanonisches Repo (#64.1):** `ullriti/elwasys`; ein Umzug zurück nach `kabieror` wird über
  Issue #75 nachgehalten.
- **TLS (#35):** TLS ist Pflicht; kein Klartext-HTTP-Default nach außen.

## Tests
- Backend: **259 JUnit-Tests grün** (8 neu: Purge-Job, beide Health-Indicators, Expired-Query).
- Deploy: `deploy/terminal/auto-update-selftest.sh` real **12/12 PASS** (Kern-Regression:
  „zweiter Cron-Lauf nach FAILURE versucht dasselbe kaputte Update nicht erneut"; Checksummen-
  Ablehnung). `bash -n` auf allen Shell-Skripten, `docker compose config` (Basis + Proxy) valide.
- `helm lint`/`template` lokal nicht ausführbar (kein helm-Binary/Netz in der Sandbox) → im neu
  ergänzten CI-`helm`-Job abgesichert.
- Blockierendes `code-reviewer`-Gate gelaufen.

## Offen / nächster Schritt
- **AP7 (KB-Überarbeitung)** als letztes Arbeitspaket der Pre-Launch-Review.
- Danach Live-Gang / Betrieb auf der Zielarchitektur.

## Referenzen
- Epic: https://github.com/ullriti/elwasys/issues/66 (AP6: #31/#32/#33/#34/#35/#60/#62/#63/#64)
- Neues Issue: #75 (Repo-Umzug-Checkliste)
- ADR: [0019](../architecture/0019-ap6-vaadin-lizenz-restrisiko.md)
- Branch: `claude/ap6-issue-66-yzxjy4`
