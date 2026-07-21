# elwasys – Hinweise für Claude-Sessions

elwasys verwaltet und rechnet Waschmaschinen in Gemeinschafts-Waschküchen ab:
Raspberry-Pi-Terminals (JavaFX, Touch, RFID) + Vaadin-Admin-Portal + gemeinsame
PostgreSQL-DB. Details: [kb/00-overview.md](kb/00-overview.md).

## Zuerst lesen

Die **Knowledge Base [`kb/`](kb/README.md) ist die zentrale Wahrheit** dieses
Modernisierungsprojekts. Vor jeder Arbeit lesen:

1. [kb/05-migration-plan.md](kb/05-migration-plan.md) – **der Modernisierungsplan**
   (Rahmenbedingungen, Zielarchitektur, Roadmap, Fortschritt, Entscheidungen des
   Auftraggebers). Hier steht, welche Phase als Nächstes dran ist.
2. [kb/README.md](kb/README.md) – Inhaltsverzeichnis + Status-Log.
3. Je nach Aufgabe: 01 (Architektur), 02 (Datenmodell), 03 (Module),
   04 (Build/Run), 06 (UI-Tests), 07 (Remote-Umgebung), 08 (Testplan).

**Aktueller Stand (2026-07-21):** Phase 0 (Sicherheitsnetz: Build + E2E-Tests) und
Phase 1 (Fundament: Parent-POM, Java 21, JUnit 5, ElwaManager-DI) sind abgeschlossen,
alle Grundsatzentscheidungen sind gefallen. **Phase 2 (Backend-Gerüst: AP1–AP6 – Flyway-
Baseline, JPA-Entities/Geschäftslogik, Auth, REST-API/Standort-Token/WebSocket,
Benachrichtigungsdienst hinter Konfig-Flag, Deployment mit Docker/Helm) ist abgeschlossen**
(QA-Review ohne Befunde). **Phase 3 (Portal-Neubau: Vaadin-Flow-UI im Backend, AP1–AP6) ist
abgeschlossen**: das neue Portal hat volle Feature-Parität zum Alt-Portal, nachgewiesen durch
die portierte Playwright-Suite `backend/e2e/` (P1–P20, 20/20 grün, mehrfach reproduziert).
Das Alt-Portal-Modul (`Portal/`) ist als E2E-Ziel stillgelegt (Code bleibt bis Phase 5 im
Repo, CI baut es weiterhin), die Cross-Component-Wartungsverbindung (P21/P22) läuft
unverändert weiter. **Nächster Schritt: Phase 4** (Terminal-Modernisierung) – siehe Roadmap
in kb/05.

## Arbeitsregeln

- **Verhalten bewahren**: Nutzer-sichtbares Verhalten darf sich nicht ändern
  (Rahmenbedingung des Auftraggebers). Die E2E-Suiten sind der Maßstab.
- **Tests vor und nach jedem Umbau grün** (Kommandos unten). Kleine, einzeln
  baubare Commits.
- **KB fortschreiben**: Nach jedem Arbeitspaket Fortschritt/Änderungslog in
  kb/05 und Status-Log in kb/README.md aktualisieren; betroffene KB-Dokumente
  (01–04, 06–08) mitpflegen. Entscheidungen des Auftraggebers in kb/05 unter
  „Entscheidungen“ dokumentieren.

## Build & Tests (Remote-Umgebung ist vorbereitet, siehe kb/07)

```bash
# Build (seit Phase 1 gibt es ein Aggregator-Parent-POM, siehe kb/04/kb/05).
# WICHTIG: "mvn -f Common/pom.xml install" allein installiert die Parent-POM
# NICHT mit ins lokale Repo – Client-Raspi/Portal scheitern dann beim
# Auflösen von "common". Immer über den Root-Reactor bauen:
mvn -f pom.xml install -pl Common -am -DskipTests
mvn -f Client-Raspi/pom.xml package
mvn -f Portal/pom.xml package
# oder komplett: mvn install (von der Repo-Wurzel)

# Client: UI-/E2E-Tests headless (startet PG, seedet DB, Xvfb)
Client-Raspi/run-ui-tests.sh              # alle
Client-Raspi/run-ui-tests.sh <TestClass>  # einzelne Klasse
Client-Raspi/run-client-e2e.sh
Client-Raspi/run-cross-component-e2e.sh   # Wartungsverbindung Alt-Portal⇄Client (bis Phase 4)

# Backend: JUnit-Suite (Testcontainers bzw. lokales PostgreSQL, siehe kb/04/kb/07)
backend/run-backend-tests.sh

# Backend-Portal: Playwright-E2E (P1–P20, Setup/DB/Jar siehe backend/e2e/ u. kb/06)
# – fachlicher Nachfolger von Portal/e2e (Vaadin 7), das als E2E-Ziel STILLGELEGT ist
# (Code bleibt bis Phase 5 im Repo, siehe kb/03-modules.md).
cd backend/e2e && npm test
```

Der SessionStart-Hook installiert Common und wärmt die Client-Dependencies vor.
CI: `.github/workflows/ci.yml` baut/testet Common, Client-Raspi (inkl. Cross-Component),
Backend (JUnit + Playwright-E2E) bei jedem PR; das Alt-Portal-Modul wird nur noch gebaut
(kein Playwright-E2E mehr dagegen, siehe kb/06-ui-tests.md).
