# Worklog

Chronologisches Arbeitsjournal des elwasys-Modernisierungsprojekts. Ein Eintrag je
Session bzw. Arbeitspaket (bei zusammenhängenden Sessions je Phase gebündelt), neueste
Zusammenfassung zuletzt angehängt (append-only).

Zweck: das Wissen über *was* wann *warum* getan wurde gehört ins Repo – nicht in den
lokalen Speicher einer einzelnen Session. Dieses Journal ergänzt die Knowledge Base
([`../kb/`](../kb/README.md)), die den *aktuellen Sollzustand* beschreibt: die KB sagt,
wie es ist; das Worklog, wie es dazu kam. Feinkörnige technische Details bleiben im
Änderungslog in [`../kb/05-migration-plan.md`](../kb/05-migration-plan.md); hier steht die
verdichtete Journal-Sicht.

## Template

```markdown
# YYYY-MM-DD — <Kurztitel>

**Ziel:** <ein bis zwei Sätze, was in dieser Session/diesem Arbeitspaket erreicht werden sollte>

## Erledigt
- <Punkt> (Bezug: Commit/PR/Datei, falls in der Quelle genannt)

## Entscheidungen
- <getroffene Entscheidung + kurze Begründung>   (Abschnitt weglassen, wenn keine)

## Offen / nächster Schritt
- <was als Nächstes ansteht bzw. bewusst offen bleibt>

## Referenzen
- docs/kb/05-migration-plan.md, docs/kb/03-modules.md, …
```

## Index

| Datum | Eintrag | Kurz |
|-------|---------|------|
| 2026-07-19 | [setup-und-sicherheitsnetz](2026-07-19-setup-und-sicherheitsnetz.md) | KB-Aufbau, Remote-Build, UI-/E2E-Test-Harness, erste C*/P*-Testfälle |
| 2026-07-20 | [phase-0-und-1-fundament](2026-07-20-phase-0-und-1-fundament.md) | Phase 0 Sicherheitsnetz fertig; Phase 1 Parent-POM, Java 21, JUnit 5, ElwaManager-DI |
| 2026-07-20 | [phase-2-backend-geruest](2026-07-20-phase-2-backend-geruest.md) | AP1–AP6: Flyway, JPA/Geschäftslogik, Auth, REST-API/Token/WebSocket, Benachrichtigung, Deployment |
| 2026-07-20 | [phase-3-portal-neubau](2026-07-20-phase-3-portal-neubau.md) | Vaadin-Flow-Portal im Backend, AP1–AP6, Feature-Parität, Playwright P1–P20 |
| 2026-07-21 | [phase-4-terminal-modernisierung](2026-07-21-phase-4-terminal-modernisierung.md) | AP1–AP6: JavaFX 23, REST-Cutover, Fernwartung umgedreht, Offline-Robustheit |
| 2026-07-21 | [phase-5-aufraeumen](2026-07-21-phase-5-aufraeumen.md) | Alt-Portal/DataManager entfernt, DB-Rollen gehärtet, Schema bereinigt, Doku-Endstand |
| 2026-07-22 | [phase-5-nachtrag-common-und-schema](2026-07-22-phase-5-nachtrag-common-und-schema.md) | common-Modul aufgelöst (Root-Reactor 3→2), Alt-Schema auf eine Quelle konsolidiert |
| 2026-07-22 | [phase-6-produktivumschaltung](2026-07-22-phase-6-produktivumschaltung.md) | Cutover-Werkzeuge, Terminal-Update/Auto-Update+Rollback, Post-Deploy-Smoke, Runbook |
| 2026-07-22 | [final-review-before-launch](2026-07-22-final-review-before-launch.md) | Finale Pre-Launch-Review: tiefe Review (Migrationsänderungen + übernommene Konzepte), 49 Findings als Issues #16–#64, gebündelt in Epic #66 (AP1–AP7) |
| 2026-07-22 | [phase-5-6-qa-review](2026-07-22-phase-5-6-qa-review.md) | QA-Review Phase 5/6: Watchdog-BLOCKER/MAJOR + MINOR/NITPICKs behoben, Backend 200/200 |
| 2026-07-22 | [agentic-baseline-setup](2026-07-22-agentic-baseline-setup.md) | agentic-baseline übernommen: AGENTS.md, docs/-Wissenssystem, .claude/-Agenten, KB entwirrt (Worklog/CHANGELOG/ADRs) |
| 2026-07-22 | [portal-design](2026-07-22-portal-design.md) | Portal-Design wiederhergestellt (Laufzeit-Inline-CSS statt @Theme), Palette an Terminal angeglichen, Dashboard-Karten responsiv+volle Breite |
| 2026-07-22 | [demo-daten-ui-checks](2026-07-22-demo-daten-ui-checks.md) | Demo-Modus (Profil `demo`): DemoDataSeeder + run-demo.sh, wiederverwendbarer Beispielbestand fürs visuelle UI-Prüfen, DemoDataSeederTest (5) |
| 2026-07-22 | [ap1-offline-replay-kern](2026-07-22-ap1-offline-replay-kern.md) | Pre-Launch AP1 (#16/#17/#18/#54/#59): privilegierter Replay-Pfad, Client-Robustheit (Dead-Letter/Paar-Reihenfolge/NPE/clear-Race), Zeitstempel-Invariante+Preis-Deckel, Uhren-Plausibilität, ADR 0016 |
| 2026-07-22 | [ap2-terminal-stabilitaet](2026-07-22-ap2-terminal-stabilitaet.md) | Pre-Launch AP2 (#19/#27/#28/#51/#52/#53/#55/#56/#57/#58/#61): deCONZ-Reconnect, Restart-Leak (WS-Client/Listener), Concurrency+Retry-Guard, Watchdog-continue, FX-Thread, kaputte-2xx, Journal-DSYNC, RFID-Log-Maskierung, Resume-Null-Check, CSPRNG-Passwort, SMTP-Deadcode |
| 2026-07-22 | [ap3-abrechnungs-integritaet](2026-07-22-ap3-abrechnungs-integritaet.md) | Pre-Launch AP3 (#20/#22/#29/#36/#41): pessimistisches User-Row-Locking + Advisory-Lock je Gerät, frisch gesperrter Finish, Idempotenz-Härtung (Key-Länge/Race/operation/Replay-nach-Löschung), Betragsvalidierung, AFTER_COMMIT-Benachrichtigung, ADR 0017 |
| 2026-07-22 | [ap4-fragen-klaren](2026-07-22-ap4-fragen-klaren.md) | Pre-Launch AP4 (Auth & Security): drei 🧩-Auftraggeberfragen geklärt — Reset-Enumeration neutralisieren (#24), Passwort-Mindestlänge ≥ 8 (#44), Standort-Token nur Doku/Runbook+ADR (#43); ADR 0018 neu, ADR 0008 korrigiert (kein Token-Admin-UI) |
| 2026-07-22 | [ap4-umsetzung](2026-07-22-ap4-umsetzung.md) | Pre-Launch AP4 umgesetzt (#21/#23/#24/#25/#26/#42/#44/#45/#46/#47/#48): regex-freier Kartenlogin, Username-Guard, In-Memory-Brute-Force-Limit + Reset-Rate-Limit (geteilter `RateLimiter`), Fernwartungs-Standortprüfung, neutrale Reset-Meldung, Passwort-Mindestlänge, uuid-Validierung, Token-`last_used`-Throttle; Review-Gate (Enumeration-MAJOR behoben: neutrale Login-Sperre); 243 Backend-Tests grün |
| 2026-07-23 | [ap5-portal-performance-crud](2026-07-23-ap5-portal-performance-crud.md) | Pre-Launch AP5 (#30/#37/#38/#39/#40/#49/#50): Dashboard-Historie lazy-paginiert + Guthaben-Spalte gebündelt (`getCredits`), V11-Indizes, Demo-Seeder-Produktiv-Wächter, Feldlängen/Soft-Delete-Kürzung, Testdeterminismus, Geräte-Lösch-Wächter + Doppelklick-Schutz + Lösch-Bestätigung, RouteAccess-Classpath-Scan + neue E2E; #60 → AP6 (Steckdosen bleiben an); 250 Backend-Tests + 24 E2E grün |
| 2026-07-23 | [ap6-deployment-betrieb-cutover](2026-07-23-ap6-deployment-betrieb-cutover.md) | Pre-Launch AP6 (#31/#32/#33/#34/#35/#60/#62/#63/#64): Zeitzone `Europe/Berlin`, Dauerbetrieb (Purge-Job + Betriebs-Health-Indicators → `/operational`, Backup/Alerting/Retention-Runbook), TLS-Pflicht (nur `127.0.0.1`-Bind), Terminal-Auto-Update mit Rollback-Guard, Jar-SHA256, Repo-Vereinheitlichung `ullriti/elwasys`, Vaadin-Lizenz-Restrisiko (ADR 0019); 259 Backend-Tests grün |
| 2026-07-23 | [ap7-kb-ueberarbeitung](2026-07-23-ap7-kb-ueberarbeitung.md) | Pre-Launch AP7 (KB-Überarbeitung): KB nach AP1–AP6 auf Ist-Stand gebracht — `03-modules.md` (12 Faktenkorrekturen + Ergänzungen, code-verifiziert), Health-Pfade/Compose-Härtung in `04-build-and-run.md`, Änderungslog AP4–AP6 in `05-migration-plan.md`, Worklog-Index + README-„Aktueller Stand" nachgezogen; keine neuen ADRs nötig |
| 2026-07-23 | [kb-ist-zustand-refactor](2026-07-23-kb-ist-zustand-refactor.md) | KB-Informationsarchitektur-Refactor (ADR 0020): KB-Artikel von „Historie im Fließtext" (Durchstreichen, „seit Phase X", per-AP-Tags) auf „Body = Ist-Zustand + `## Historie`-Pointer-Footer" umgebaut — `00`–`04`/`06`/`07`/`08` (`03-modules` 1738→1044 Zeilen), Ist-Fakten code-verifiziert erhalten |
