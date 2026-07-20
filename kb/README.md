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
| 2026-07-20 | **Phase 1 (Build-Teil)**: Aggregator-Parent-POM angelegt, Common auf Java 21 gehoben, Portal-Sprachlevel (1.8) bewusst eingefroren, CI/Release-Skripte auf den Parent-POM-Reactor umgestellt (`mvn -f pom.xml install -pl Common -am`), Release-Workflow nutzt jetzt `versions:set` statt sed-Hack; alle drei Module bauen grün, Details in 04-build-and-run.md + 05-migration-plan.md |
| 2026-07-20 | **Phase 1 (Client-Raspi Java 21 + ElwaManager-DI)**: Client-Raspi auf Java 21 gehoben, minimaler DI-Seam für `ElwaManager` eingeführt, 12 neue isolierte Charakterisierungstests für `MainFormStateManager` (JUnit 5, ohne TestFX/DB); volle Client-Suite grün (~~33/33~~ **37/37**, siehe QA-Review-Korrektur unten) |
| 2026-07-20 | **Phase 1 abgeschlossen (QA-Review)**: Diff-Review aller Phase-1-Commits gegen CLAUDE.md/Roadmap, DI-Seam und neue Tests verifiziert (isoliert, Produktionsverhalten unverändert). Volle Suiten grün: Client 37/37, Cross-Component 3/3, Portal-E2E 18/18, `mvn package`/`install` für alle drei Module grün (Portal-Bytecode als Java 8 verifiziert). **Zwei echte Regressionen gefunden und behoben**: CI/Release-Workflows liefen noch mit JDK 17 (inkompatibel mit dem neuen Sprachlevel 21 von Common/Client-Raspi) → auf JDK 21 angehoben; `setup.sh` installierte auf neuen Raspi-Terminals noch ein Java-17-JRE (das das jetzt mit Sprachlevel 21 gebaute fat-jar nicht ausführen könnte) → auf `bellsoft-java21-runtime-full` angehoben. Testzahl-Dokufehler (33/33 statt 37/37) korrigiert. Details in 05-migration-plan.md |
| 2026-07-20 | **Roadmap um Phase 6 „Produktivumschaltung” ergänzt** (Auftraggeber-Wunsch): Umbau des bestehenden Produktiv-Setups nach Phase 5 – Migrationsskripte, Terminal-Neuaufsetzung (Java-17-Thema final gelöst), Upgrade-Skript für Terminals mit optionalem Auto-Update+Rollback; Portal/Backend brauchen dank Docker/Kubernetes/Helm kein eigenes Upgrade-Skript, nur automatisierte Post-Deploy-Smoke-Tests. Details in 05-migration-plan.md |
| 2026-07-20 | **Phase 2 AP1 (Backend-Gerüst + Flyway-Baseline)**: neues Modul `backend` (Spring Boot 3.5.16 per BOM-Import, Java 21, Actuator-Health, JDBC+Flyway, kein JPA) im Root-Reactor; Flyway-Baseline `V1__baseline_schema_0_4_0.sql` aus `database-init.sql` erzeugt (Rollenanlage idempotent gefasst, Spaltentypo bewusst erhalten); `config.db.version`-Mechanismus untersucht (wird von keinem Java-Code gelesen) und offiziell stillgelegt – künftige Schemaänderungen laufen nur noch über Flyway. Schema-Äquivalenz (Alt-Weg vs. Flyway-Baseline) und `baselineOnMigrate` gegen eine Bestands-DB mit `backend/verify-schema-baseline.sh` verifiziert (schema-identisch bis auf Flywayss eigene Historientabelle). Tests: Testcontainers als Default, lokaler PostgreSQL-Override (`ELWASYS_TEST_JDBC_URL`) für die Docker-lose Sandbox-Umgebung, über `backend/run-backend-tests.sh` grün (2/2). Vierter CI-Job „Backend” (Testcontainers, Docker in GitHub Actions vorhanden). Root-Reactor-Build (alle vier Module) sowie isolierter Common/Client-Raspi/Portal-Build weiterhin grün, keine Quelländerung an den drei Altmodulen. Details in 05-migration-plan.md, 02-data-model.md, 04-build-and-run.md |
| 2026-07-20 | **Phase 2 AP2 (JPA-Entities/Repositories + Geschäftslogik-Portierung)**: `spring-boot-starter-data-jpa` ergänzt; 8 Entities (`UserGroupEntity`, `UserEntity`, `LocationEntity`, `DeviceEntity`, `ProgramEntity`, `ExecutionEntity`, `CreditAccountingEntryEntity`, `ConfigEntity`) 1:1 aufs Bestandsschema gemappt (n:m-Tabellen als `@ManyToMany`, Postgres-native Enums über `@JdbcTypeCode(NAMED_ENUM)`, alle fachlich genutzten Assoziationen EAGER – analog zum durchgängig eager ladenden Alt-`DataManager`); 6 Repositories; 4 Services (`PricingService`, `CreditService`, `PermissionService`, `ExecutionService`), jeweils 1:1-Portierung mit Alt-Code-Quellenverweis (Preisberechnung, Guthaben/Abrechnung, Berechtigungs-Matrix, Execution-Lebenszyklus). **Alt-vs-Neu-Vergleichstests** umgesetzt: `common` als test-scope-Dependency, `LegacyDataManagerFactory` baut eine echte Alt-`DataManager` gegen dieselbe Test-DB, `PricingServiceParityTest`/`CreditServiceParityTest` vergleichen Alt- und Neu-Berechnung bitgenau (Wert + `BigDecimal`-Skala) – deckte u. a. die `new BigDecimal(double)`-Fließkomma-Eigenheit und die skalasensitive `price.equals(BigDecimal.ZERO)`-Prüfung im Alt-Code auf, beides bewusst 1:1 nachgebildet (Details/weitere Beobachtungen in 05-migration-plan.md). 27/27 Backend-Tests grün (`backend/run-backend-tests.sh`); `run-backend-tests.sh` und der CI-Backend-Job bauen jetzt zuerst `Common`. Root-Reactor-Build sowie isolierter Client-Raspi/Portal-Build weiterhin grün, keine Quelländerung an Common/Client-Raspi/Portal. Details in 05-migration-plan.md, 03-modules.md |
