# elwasys – Knowledge Base (KB)

Diese Knowledge Base ist der zentrale Ablageort für die Überarbeitung / Modernisierung
des elwasys-Projekts. Hier werden Recherche, Architektur, Datenmodell, Migrationsplan,
Teststrategie und die Remote-/Cloud-Init-Umgebung dokumentiert und fortlaufend gepflegt.

> Jede Arbeits-Session nutzt ihren eigenen Branch (`claude/...`); die KB auf dem
> jeweils aktuellsten Stand (master + gemergte PRs) ist die zentrale Wahrheit.
> Einstieg für neue Sessions: [/CLAUDE.md](../CLAUDE.md) → [05-migration-plan.md](05-migration-plan.md).

## Inhalt

| Dokument | Beschreibung |
|----------|--------------|
| [00-overview.md](00-overview.md) | Was ist elwasys? Zweck, Features, High-Level-Bild |
| [01-architecture.md](01-architecture.md) | Module, Komponenten, Kommunikationswege, Technologie-Stack |
| [02-data-model.md](02-data-model.md) | Datenbankschema (PostgreSQL), Entitäten, Beziehungen |
| [03-modules.md](03-modules.md) | Detaillierte Beschreibung der Module (Common, Client-Raspi, Portal) |
| [04-build-and-run.md](04-build-and-run.md) | Build, Ausführung, Konfiguration, Deployment, CI |
| [05-migration-plan.md](05-migration-plan.md) | Modernisierungsplan: Rahmenbedingungen, Komponenten-Inventur, Zielarchitektur, Roadmap (lebendes Dokument) |
| [06-ui-tests.md](06-ui-tests.md) | UI-Test-Strategie für Client (JavaFX) und Portal (Vaadin) |
| [07-cloud-init.md](07-cloud-init.md) | Remote-/Cloud-Init-Umgebung zum Ausführen von Build & Tests |
| [08-test-plan.md](08-test-plan.md) | Testplan: Vertiefung der Frontend-Tests (Client & Portal) |

## Status-Log

| Datum | Ereignis |
|-------|----------|
| 2026-07-19 | KB angelegt, Projekt-Recherche & Übersicht erstellt |
| 2026-07-19 | Build in Remote-Umgebung verifiziert (Common install, Client compile/test-compile) |
| 2026-07-19 | SessionStart-Hook + portable cloud-config für Remote-Build/Tests erstellt & validiert |
| 2026-07-19 | Client-UI-Test-Harness (TestFX/Xvfb + JUnit5) aufgebaut; 2 headless Tests grün |
| 2026-07-19 | Portal-Build repariert (Vaadin/GWT/War/JDBC/API-Drift); `jetty:run` liefert Login-Seite |
| 2026-07-19 | Portal-E2E (Playwright, Node/TS) aufgebaut; Login-Smoke-Test grün (2/2) |
| 2026-07-19 | Client-E2E: echte App headless hochgefahren (fhem-Sim + DB) → SELECT_DEVICE; getDeconzServer-Bug gefixt |
| 2026-07-19 | Client-E2E vertieft (C2–C5): Geräteliste, Karten-Login, Gerät buchen, Programmstart; Suite 7/7 grün |
| 2026-07-19 | Portal-E2E vertieft (P3–P5): falsches Passwort, Logout, Navigation aller Admin-Views; Suite 5/5 grün |
| 2026-07-19 | Client-Login-Varianten (C6–C8) grün; Portal-CRUD (P6 Benutzer, P9 Gruppe) grün; Portal-Suite 7/7 |
| 2026-07-19 | Client C9 (zu wenig Guthaben) grün; Portal P10 (Gerät anlegen) grün; Portal-Suite 8/8 |
| 2026-07-19 | CI-Flakiness behoben (geteilte DB); Client C10 (Auto-Logout) + Portal P12 (Programm) grün; Client 12 / Portal 9 |
| 2026-07-20 | Client C12 (Abbruch) + Portal P8 (Guthaben) grün; Seeding auf postgres (FK-Cleanup inkl. credit_accounting); Client 13 / Portal 10 |
| 2026-07-20 | Client C11 (Auto-Ende) + Portal P7 (Benutzer sperren) grün; Client 14 / Portal 11 |
| 2026-07-20 | Client C16 (standortfremdes Gerät) + Portal P13 (Gruppe löschen) grün; Client 15 / Portal 12 |
| 2026-07-20 | Portal P15+P18 (Nicht-Admin-Frontend & Berechtigungen) grün; Portal 13 |
| 2026-07-20 | Client C14 (DYNAMIC-Preisanzeige) grün; Client 16. Verbleibende Fälle in 08-test-plan.md dokumentiert |
| 2026-07-20 | Client C13 (Resume) + C15 (DB-Ausfall→ERROR) grün; Client 18/18 |
| 2026-07-20 | Portal P14 (Standort), P16 (Passwort ändern), P17 (Einstellungen), P19 (Passwort vergessen), P20 (Gerätestatus) grün; Portal 18/18. Nur P21 (Cross-Component) offen |
| 2026-07-20 | Cross-Component P21/P22 (Wartungsverbindung Portal⇄Client: Log holen, Neustart, Status) grün; Client 21/21. Alle geplanten Tests umgesetzt |
| 2026-07-20 | **Phase 0 abgeschlossen** (Sicherheitsnetz steht); isolierte State-Machine-Charakterisierung + ElwaManager-DI nach Phase 1 verschoben (05-migration-plan.md) |
| 2026-07-20 | **Modernisierungsplan überarbeitet** (05-migration-plan.md): Rahmenbedingungen fixiert (Java-Backend, Postgres, Raspi-Terminals bleiben; Nutzerverhalten unverändert), vollständige Komponenten-Inventur, Zielarchitektur (zentrales Spring-Boot-Backend, Portal integriert, Terminal über API), Roadmap Phasen 1–5 |
| 2026-07-20 | **Phase 1 (Testframeworks)**: einzige TestNG-Testklasse `InactivitySchedulerTest` nach JUnit 5 migriert + von `src/main` nach `src/test` verschoben (lief zuvor nicht unter Surefire); TestNG- und ungenutzte JUnit-4-Dependency aus Client-Raspi/pom.xml entfernt |
