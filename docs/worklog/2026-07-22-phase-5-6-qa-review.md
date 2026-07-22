# 2026-07-22 — Phase-5- und Phase-6-QA-Review (Befunde behoben)

**Ziel:** Formale QA-Review der Phasen 5 und 6 durch den Koordinator (Review-Subagent,
Korrekturen durch separate Entwickler-Agenten); gefundene Befunde beheben, ohne
Nutzer-sichtbares Verhalten zu ändern.

## Erledigt
- **Phase-5-QA-Review – ohne blockierende Befunde.** Diff-Review der Phase-5-Commits
  (`184133b..afaf6e5` + Common-Nachtrag `f920843..95b256d`): V6–V10 idempotent/additiv,
  V1–V5-Checksummen byte-identisch, `elwaportal` einziger App-DB-User, Common-Auflösung
  ohne Wiederkopplung. **2 MINOR + 1 NITPICK behoben**: tote `getDatabase*`-Getter +
  `database.*`-Properties (`elwaclient1`) aus 16 E2E-Fixtures entfernt, hängenden
  `{@link LegacyDataManagerFactory}`-Javadoc bereinigt, Schrittnummerierung in
  `backend/e2e/scripts/start-backend.sh` korrigiert.
- **Phase-6-QA-Review** – DB-Migrations-/Rollback-Korrektheit ohne Befund. **1 BLOCKER +
  1 MAJOR im `deploy/terminal/auto-update-watchdog.sh` behoben**: (B1) ein reiner
  Fetch-Fehler löste denselben Rollback + `java`-Kill aus wie ein Fehl-Deploy → jetzt
  Vergleich des `latest`-Symlink-Ziels, unverändert ⇒ nur Warnung + Exit; (M1) kein
  Leerlauf-Check vor dem Kill → fail-safe Leerlauf-Gate (`devices/overview` `occupied` +
  Marker-Frische; bei Unsicherheit verschieben) + README-Doku. **4 MINOR/2 NITPICK**:
  `run.sh` hängt Logs an statt zu truncaten (+ Rotation), stale Doku-Refs
  (`verify-schema-baseline.sh`/`database-init.sql`) bereinigt, Feldtrenner-Bug in
  `deploy/cutover/02-issue-terminal-tokens.sh`, `deploy/terminal/upgrade-jre.sh`
  vereinfacht.
- Verifikation: Backend **200/200**, Client-UI **53/53**; Watchdog-Fixes per 31
  Dry-Run-Checks belegt, `bash -n` grün. Verhalten unverändert.

## Offen / nächster Schritt
- Alle Phasen 0–6 sind umgesetzt und QA-reviewt. Nächster Schritt ist der operative
  Feld-Cutover (Auftraggeber), siehe `deploy/CUTOVER-RUNBOOK.md`.

## Referenzen
- master #12 (`8dbf91f`), docs/kb/05-migration-plan.md (Änderungslog),
  deploy/terminal/README.md, deploy/terminal/auto-update-watchdog.sh
