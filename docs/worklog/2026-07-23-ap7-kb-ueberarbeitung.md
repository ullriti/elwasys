# 2026-07-23 — Pre-Launch AP7: KB-Überarbeitung

**Ziel:** Das letzte Arbeitspaket der Pre-Launch-Review (Epic #66) umsetzen – nach Abschluss
der Code-Arbeitspakete AP1–AP6 die Knowledge Base wieder auf den tatsächlichen Ist-Stand
bringen. Reine Doku-Arbeit, kein Code. Leitgedanke: „tief einlesen, damit nichts übersehen
wird" – erst systematisch alle AP1–AP6-Änderungen gegen die bestehende Doku prüfen, dann
schreiben; wo Doku und Code auseinanderliefen, wurde am Code verifiziert.

## Vorgehen
Vier parallele Gap-Map-Analysen (Backend/Datenmodell, Terminal/Offline, Auth/Security/Portal,
Deployment/Doku-Meta), jede mit stichprobenartiger Verifikation direkt am Code (nicht nur am
Worklog). Ergebnis: eine konsolidierte AP7-Arbeitsliste. Die drei KB-Inhaltsdateien danach
delegiert (je eine Datei, disjunkt), die verbindenden Meta-Dateien und das Review-Gate im
Hauptkontext. Zentrales Ergebnis der Analyse: `docs/kb/03-modules.md` stand komplett auf dem
Vor-#66-Stand und enthielt mehrere **sachlich falsche** Aussagen; `README.md` „Aktueller Stand",
CHANGELOG und ADRs waren dagegen bereits nachgeführt.

## Erledigt
- **`docs/kb/03-modules.md`** (Hauptlast): 12 sachliche Korrekturen + Ergänzungen, alle neuen
  Stellen als „Pre-Launch AP<n> (Epic #66)" gekennzeichnet (Namenskollision mit den bestehenden
  Phase-3-Portal-„AP"-Überschriften vermieden). Korrigiert u. a.: Replay bricht **nicht** bei
  jedem Fehler ab (Dead-Letter/Paar-Reihenfolge, #17), `DeviceService.delete` **mit** Wächter
  (`EntityInUseException`, #49), Dashboard-Historie **lazy-paginiert** statt vollständig (#30),
  Kartenlogin **regex-frei** (#21), `RouteAccessAnnotationsTest` per Classpath-Scan (#50),
  finish löst Benachrichtigung per `AFTER_COMMIT`-Event aus statt direkt (#36), Idempotenz-Race
  per Advisory-Lock geschlossen (#29/#41), Java 16 → 21. Ergänzt: Backend-Locking (#20),
  Zeitstempel-Invariante+Preis-Deckel (#18), Auth-Härtung (RateLimiter/Brute-Force/Username-Guard,
  Passwort-Mindestlänge, Reset-Neutralisierung, AP4), Betriebs-Health-Indicators +
  `IdempotencyKeyRetentionScheduler` (AP6), Terminal-Robustheit (DSYNC, Uhren-Plausibilität,
  deCONZ-Reconnect, RFID-Maskierung u. a., AP1/AP2).
- **`docs/kb/04-build-and-run.md`**: drei veraltete Health-Endpoint-Pfade auf `/actuator/health/
  liveness` bzw. `/readiness` korrigiert (Dockerfile-`HEALTHCHECK`, Helm-Probes, Post-Deploy-
  Smoke), Compose-Härtung ergänzt (nur `127.0.0.1`-Bind, `TZ=Europe/Berlin`, Docker-Log-Limits),
  neuer Abschnitt „Dauerbetrieb" mit Pointer auf `deploy/CUTOVER-RUNBOOK.md` Kap. 7.
- **`docs/kb/05-migration-plan.md`**: Änderungslog um die drei fehlenden Zeilen Pre-Launch AP4,
  AP5, AP6 ergänzt (endete zuvor bei AP3), im bestehenden Zweispalten-Format.
- **`docs/worklog/README.md`**: fehlende AP6-Index-Zeile nachgetragen (war der `check-ai-docs.sh`-
  Rotmacher) + diese AP7-Zeile.
- **`docs/kb/README.md`**: „Aktueller Stand" um AP7 ergänzt und „Nächster Schritt" auf
  Live-Gang/Betrieb umgeschrieben.

## Entscheidungen
- **Keine neuen ADRs nötig:** Alle im AP7-Checklistenpunkt genannten Entscheidungspunkte sind
  bereits durch bestehende ADRs abgedeckt (Replay-Semantik 0016, Locking 0017, Reset-Enumeration/
  Passwort-Policy 0018, Standort-Token 0008 [korrigiert], Vaadin-Lizenz 0019). AP7 verweist nur
  darauf, legt keinen neuen an.
- **Kein CHANGELOG-Eintrag für AP7 selbst:** reine KB-Nachführung ist nicht nutzer-sichtbar;
  `[Unreleased]` war für AP1–AP6 bereits vollständig.

## Offen / nächster Schritt
- Damit ist die Pre-Launch-Review (Epic #66, AP1–AP7) vollständig abgeschlossen.
- Nächster Schritt: Live-Gang / Betrieb auf der Zielarchitektur (Cutover nach
  `deploy/CUTOVER-RUNBOOK.md`).

## Referenzen
- Epic: https://github.com/ullriti/elwasys/issues/66 (AP7: KB-Überarbeitung)
- docs/kb/03-modules.md, docs/kb/04-build-and-run.md, docs/kb/05-migration-plan.md, docs/kb/README.md
- ADRs 0008/0016/0017/0018/0019
- Branch: `claude/ap7-issue-66-deep-dive-ew0ynk`
