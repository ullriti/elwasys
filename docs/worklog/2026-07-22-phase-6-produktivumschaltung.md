# 2026-07-22 — Phase 6: Produktivumschaltung (AP1–AP7)

**Ziel:** Das bestehende Produktiv-Setup nach Phase 5 auf die Zielarchitektur umschalten:
Migrations- und Rollback-Werkzeuge für die DB, Terminal-Neuaufsetzung inkl. Java-21-Upgrade,
Terminal-Update mit optionalem Auto-Update+Rollback, Post-Deploy-Smoke-Test als Rollout-Gate
für Portal/Backend und ein orchestrierendes Cutover-Runbook.

## Erledigt
- **AP1 – Produktiv-Migrationswerkzeuge**: neues `deploy/cutover/` mit `01-preflight-check.sh`
  (rein lesend: Flyway-Status, Alt-Artefakte, Dateninventar), `02-issue-terminal-tokens.sh`/
  `03-set-admin-password.sh` (Wrapper um `token-cli`/`admin-cli`), `04-review-obsolete-
  locations.sql` (read-only) und `verify-cutover-migration.sh` (Kern-Verifikation: Testkopie
  des Alt-Schemas, Bestandsdaten einfügen, Backend-Jar dagegen starten, 21 Asserts über
  Flyway-Historie/Datenerhalt/Schema-Härtung) + `README.md`. Lokal ausgeführt: **21/21 PASS**.
- **AP2 – Rollback-/Rückbau-Skript**: `rollback-cutover.sql` (idempotentes Reverse-DDL, macht
  V3..V10 rückgängig, V2 bewusst unangetastet) + `rollback-cutover.sh` (braucht CREATEROLE für
  die V6-Umkehrung) + `verify-rollback.sh`. Lokal: **29/29 PASS (1. Lauf), 29/29 (Idempotenz),
  4/4 (Re-Cutover-Beweis)**. README-Abschnitt „Rollback" (Backup-Restore vs. Reverse-DDL).
- **AP3 – Terminal-Neuaufsetzung + Java-21-Upgrade-Pfad + run.sh-Supervisor**: `setup.sh`
  generiert `run.sh` jetzt als Supervising-Loop (kein systemd) – Endlosschleife startet das
  per Symlink `raspi-client.latest.jar` verlinkte Jar, relauncht nach JVM-Ende. Supervisor-
  Vertrag (Grundlage AP4/AP5): externer Neustart == `java`-Prozess beenden, Loop relauncht;
  ein Update hängt nur den Symlink um. Neu `deploy/terminal/upgrade-jre.sh` (Nachrüst-Pfad für
  Java-17-Altgeräte auf `bellsoft-java21-runtime-full`, idempotent, verifiziert `java >= 21`)
  + README. **Löst das Phase-1-Java-17-Restrisiko auf.** Trockentests PASS, UI 49/49.
- **AP4 – Terminal-Update-Skript `update.sh`**: hebt ein provisioniertes Terminal auf ein
  neues fat-jar OHNE das interaktive `setup.sh` (Konfig unangetastet). Bezug `--version <tag>`
  (GitHub-Release) oder `--jar <Pfad>` (offline). Jar-Layout-Konvention (Grundlage AP5-
  Rollback): `raspi-client.latest.jar` → aktuell, `raspi-client.previous.jar` → Rollback-Ziel;
  Neustart per Supervisor-Vertrag. Idempotent, `ELWA_ROOT`-Override. Trockentests PASS.
- **AP5 – Auto-Update-Watchdog mit Rollback + Readiness-Marker**: bewusst schlanke Shell-/
  Cron-Variante (Auftraggeber-Entscheidung „kein Overkill", kein systemd). Einziger Client-
  Java-Zusatz: `TerminalReadinessMarker` – schreibt beim Wechsel nach `SELECT_DEVICE`
  (medium+small) eine Marker-Datei mit frischem `mtime` (robust, nie in die UI). Neu
  `deploy/terminal/auto-update-watchdog.sh`: pollt Ziel-Version, ruft `update.sh --version`,
  verifiziert den Start über `mtime > restart_epoch` binnen Deadline; Fehlschlag → ROLLBACK
  (`latest` zurück auf `previous` + Konfig-Snapshot + Recovery + FAILURE-Log). Lockfile gegen
  parallele Cron-Läufe. UI **53/53** (4 neue Marker-Tests), Watchdog-Trockentests **16/16 PASS**.
- **AP6 – Post-Deploy-Smoke-Test (Rollout-Gate)**: Portal/Backend bekommen bewusst KEIN
  eigenes Upgrade-/Rollback-Skript – Rollout/Rollback laufen über die Plattform (docker-
  compose-Redeploy bzw. Helm-Rollback). Stattdessen `deploy/smoke/post-deploy-smoke.sh`
  (Schritt 1 `GET /actuator/health` → `UP` mit Retries; Schritt 2 schlanke Playwright-
  Teilmenge via `npm run smoke`; Exit 0 nur bei beidem grün) + `playwright.smoke.config.ts` +
  `tests-smoke/smoke.spec.ts` (strikt read-only, 4 Checks). Rein additiv. Verifiziert:
  **Health UP + Smoke 4/4 grün**, Gate PASS.
- **AP7 – Cutover-Runbook**: reines Doku-AP. Neu `deploy/CUTOVER-RUNBOOK.md` – das
  orchestrierende Runbook, das AP1–AP6 zu einer sequenzierten Anleitung zusammenführt.
  Strangler-Reihenfolge mit Gate/Rollback je Schritt: Portal/Backend gegen die bestehende
  Produktiv-DB (Flyway baseline-on-migrate) → Smoke-Gate → Rückweg; Tokens + Admin-Passwort;
  Terminals chargenweise (JRE-21 zuerst → Konfig/Jar → `SELECT_DEVICE` verifizieren);
  Benachrichtigungen (`ELWASYS_NOTIFICATIONS_ENABLED=true`) bewusst separat/zuletzt. Plus
  Rollback-Entscheidungsbaum, Post-Cutover-Checkliste, Wartungsfenster-Timeline. DB-/Backend-
  Verkettung real geprobt: verify-cutover 21/21 → Smoke Health UP + 4/4.

## Entscheidungen
- Portal/Backend brauchen kein eigenes Upgrade-Skript (Docker/Kubernetes/Helm); nur ein
  automatisiertes Post-Deploy-Smoke-Gate.
- Auto-Update bewusst als schlanke Shell-/Cron-Lösung mit Rollback statt systemd/großem
  Client-Umbau (Auftraggeber-Entscheidung „kein Overkill").
- Notification-Scharfschaltung ist ein separater, letzter operativer Cutover-Schritt.

## Offen / nächster Schritt
- Alle Phase-6-Arbeitspakete AP1–AP7 umgesetzt; formale QA-Review der Phase durch den
  Koordinator steht noch aus. Feld-/Docker-/Helm-Schritte in der Sandbox nur dokumentiert
  (kein Docker-Daemon, keine armhf-Hardware).

## Referenzen
- docs/kb/05-migration-plan.md (Änderungslog), docs/kb/04-build-and-run.md,
  docs/kb/03-modules.md, docs/kb/06-ui-tests.md, deploy/CUTOVER-RUNBOOK.md
