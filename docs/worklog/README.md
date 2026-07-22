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
| 2026-07-22 | [phase-5-6-qa-review](2026-07-22-phase-5-6-qa-review.md) | QA-Review Phase 5/6: Watchdog-BLOCKER/MAJOR + MINOR/NITPICKs behoben, Backend 200/200 |
| 2026-07-22 | [agentic-baseline-setup](2026-07-22-agentic-baseline-setup.md) | agentic-baseline übernommen: AGENTS.md, docs/-Wissenssystem, .claude/-Agenten, KB entwirrt (Worklog/CHANGELOG/ADRs) |
| 2026-07-22 | [portal-design](2026-07-22-portal-design.md) | Portal-Design wiederhergestellt (Laufzeit-Inline-CSS statt @Theme), Palette an Terminal angeglichen, Dashboard-Karten responsiv+volle Breite |
| 2026-07-22 | [demo-daten-ui-checks](2026-07-22-demo-daten-ui-checks.md) | Demo-Modus (Profil `demo`): DemoDataSeeder + run-demo.sh, wiederverwendbarer Beispielbestand fürs visuelle UI-Prüfen, DemoDataSeederTest (5) |
