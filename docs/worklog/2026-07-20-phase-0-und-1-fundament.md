# 2026-07-20 — Phase 0 (Sicherheitsnetz) und Phase 1 (Fundament)

**Ziel:** Das Test-Sicherheitsnetz komplettieren und formal als Phase 0 abschließen, den
Modernisierungsplan auf die Zielarchitektur-Fassung heben und mit Phase 1 das technische
Fundament legen (einheitliche Build-/Testframeworks, Java 21, DI-Seam).

## Erledigt
- Restliche Testfälle grün gezogen: Client C11–C16 (Auto-Ende, Abbruch, Resume,
  DB-Ausfall→ERROR, standortfremdes Gerät, DYNAMIC-Preisanzeige) → **Client 18/18**;
  Portal P7/P8/P13–P20 (Benutzer sperren, Guthaben, Gruppe löschen, Standort, Passwort
  ändern/vergessen, Einstellungen, Gerätestatus) → **Portal 18/18**; Seeding auf
  `postgres` inkl. FK-Cleanup (u. a. `credit_accounting`).
- Cross-Component P21/P22 (Wartungsverbindung Alt-Portal ⇄ Client: Log holen, Neustart,
  Status) grün → **21/21**. Alle geplanten Tests umgesetzt; verbleibende Fälle in
  docs/kb/08-test-plan.md dokumentiert.
- **Phase 0 abgeschlossen**: Build- + UI-/E2E-Sicherheitsnetz steht (Client 21, Portal 18,
  Cross-Component grün); vorgezogene PR-CI aktiv. Isolierte State-Machine-
  Charakterisierung + ElwaManager-DI bewusst nach Phase 1 verschoben.
- **Modernisierungsplan überarbeitet** (docs/kb/05): Rahmenbedingungen fixiert (Java-
  Backend, Postgres, Raspi-Terminals bleiben; Nutzerverhalten unverändert), vollständige
  Komponenten-Inventur, Zielarchitektur (zentrales Spring-Boot-Backend, Portal integriert,
  Terminal über API), Roadmap Phasen 1–5 (Phase 6 später ergänzt).
- **Phase 1 – Testframeworks**: einzige TestNG-Klasse `InactivitySchedulerTest` nach
  JUnit 5 migriert + von `src/main` nach `src/test` verschoben (lief zuvor nicht unter
  Surefire); TestNG- und ungenutzte JUnit-4-Dependency aus `Client-Raspi/pom.xml` entfernt.
- **Phase 1 – Build**: Aggregator-Parent-POM angelegt, Common auf Java 21 gehoben, Alt-
  Portal-Sprachlevel (1.8) bewusst eingefroren; CI-/Release-Skripte auf den Parent-POM-
  Reactor umgestellt, Release-Workflow nutzt `versions:set` statt sed-Hack.
- **Phase 1 – Client-Raspi Java 21 + ElwaManager-DI**: Client-Raspi auf Java 21 gehoben,
  minimaler DI-Seam für `ElwaManager` eingeführt, 12 isolierte Charakterisierungstests für
  `MainFormStateManager` (JUnit 5, ohne TestFX/DB); volle Client-Suite **37/37** grün.
- **Phase 1 abgeschlossen (QA-Review)**: Diff-Review aller Phase-1-Commits gegen
  CLAUDE.md/Roadmap; DI-Seam und neue Tests als isoliert/verhaltenserhaltend verifiziert
  (Alt-Portal-Bytecode als Java 8 bestätigt). **Zwei echte Regressionen gefunden und
  behoben**: CI-/Release-Workflows liefen noch mit JDK 17 (inkompatibel mit Sprachlevel 21)
  → auf JDK 21 angehoben; `setup.sh` installierte auf Terminals noch ein Java-17-JRE (das
  das mit Sprachlevel 21 gebaute fat-jar nicht ausführt) → auf `bellsoft-java21-runtime-full`
  angehoben. Dokufehler Testzahl (33/33 statt 37/37) korrigiert.

## Entscheidungen
- Grundsatzentscheidungen des Auftraggebers eingearbeitet: Vaadin Flow bestätigt;
  `ui/small` bleibt (Display im Einsatz); App-Reste (`elwaapi`) werden entfernt; fhem UND
  deCONZ bleiben beide unterstützt; Betrieb als Docker-Compose/Kubernetes; Portal-Struktur
  bleibt erhalten (UX-Verbesserungen erwünscht).

## Offen / nächster Schritt
- Phase 2 (Backend-Gerüst): Flyway-Baseline, JPA/Geschäftslogik, Auth, REST-API/WebSocket,
  Benachrichtigungsdienst, Deployment.

## Referenzen
- docs/kb/05-migration-plan.md (Änderungslog + Roadmap), docs/kb/04-build-and-run.md,
  docs/kb/08-test-plan.md
