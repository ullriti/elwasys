# elwasys – Hinweise für Claude-Sessions

elwasys verwaltet und rechnet Waschmaschinen in Gemeinschafts-Waschküchen ab:
Raspberry-Pi-Terminals (JavaFX, Touch, RFID) + Vaadin-Admin-Portal (Teil des zentralen
Backends) + gemeinsame PostgreSQL-DB. Details: [kb/00-overview.md](kb/00-overview.md).

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
**Phase 4 (Terminal-Modernisierung, AP1–AP6) ist abgeschlossen** (AP1 Sicherheitsnetz/
deCONZ-Sim/ui-small-Smoke, AP2 JavaFX 23/`java.net.http`, AP3 Backend-API-Erweiterungen
[Idempotenz/Snapshot], AP4 Client-Cutover auf REST-API+Token [kein Direkt-DB-Zugriff mehr für
Daten], AP5 Fernwartung umgedreht [ausgehende WS-Verbindung des Terminals,
`MaintenanceServerManager`/IP-Registrierung entfernt – letzter Direkt-DB-Zugriff des Clients
entfällt], AP6 Offline-Robustheit [laufende Executions lokal zu Ende führen + Offline-
Buchungen]), QA-Review ohne blockierende Befunde. Die Cross-Component-Wartungsverbindung
(P21/P22) läuft seit AP5 über den neuen Backend-WS-Kanal (Nachfolgesuite im `backend`-Modul,
`run-cross-component-e2e.sh` umgestellt). **Phase 5 (Aufräumen): alle sechs Arbeitspakete
(AP1–AP6) sind umgesetzt**, die formale QA-Review der Phase steht noch aus: AP1 Alt-Portal-
Modul (`Portal/`) + `Common.DataManager`/Maintenance-Altprotokoll komplett aus dem Repo
entfernt (Root-Reactor jetzt **3 Module**: Common, Client-Raspi, backend; Common auf 6
Klassen geschrumpft), AP2 DB-Rollen gehärtet (`elwaclient1`/`elwaapi`/Gruppe `elwaclients`
entfernt, `elwaportal` einziger Anwendungs-DB-User; Default-Admin-Passwort entfernt,
`admin-cli` zum Setzen), AP3 Spaltentypo-Fix + obsolete `locations.client_*`-Spalten entfernt,
AP4 `elwaapi`-App-Reste entfernt (`auth_key`-Trigger/-Spalten, `reservations`/
`foreign_authkeys`), AP5 Release-Pipeline finalisiert, AP6 Doku-Endstand (READMEs/kb/Setup auf
die Zielarchitektur gebracht). Kein Alt-Portal, kein Direkt-DB-Zugriff der Terminals mehr –
siehe Roadmap/Änderungslog in kb/05 für Details. **Nächster Schritt: QA-Review Phase 5**,
danach Phase 6 (Produktivumschaltung), siehe Roadmap in kb/05.

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
# Ein Einzelmodul-Build (z. B. "mvn -f Client-Raspi/pom.xml package") braucht die
# Parent-POM im lokalen Repo; "mvn -N install" installiert genau diese Parent-POM:
mvn -N install -DskipTests
mvn -f Client-Raspi/pom.xml package
mvn -f backend/pom.xml package
# oder komplett: mvn install (von der Repo-Wurzel; Root-Reactor = 2 Module:
# Client-Raspi, backend – das frühere "common"-Modul wurde nach der Migration
# aufgelöst, seine 6 Utility-Klassen liegen jetzt in Client-Raspi/src/main)

# Client: UI-/E2E-Tests headless (startet PG, seedet DB, Xvfb)
Client-Raspi/run-ui-tests.sh              # alle
Client-Raspi/run-ui-tests.sh <TestClass>  # einzelne Klasse
Client-Raspi/run-client-e2e.sh
Client-Raspi/run-cross-component-e2e.sh   # Fernwartung über Backend-WS-Kanal (seit Phase 4 AP5; Nachfolger der Alt-Portal⇄Client-TCP-Verbindung)

# Backend: JUnit-Suite (Testcontainers bzw. lokales PostgreSQL, siehe kb/04/kb/07)
backend/run-backend-tests.sh

# Backend-Portal: Playwright-E2E (P1–P20, Setup/DB/Jar siehe backend/e2e/ u. kb/06)
# – DIE Portal-E2E-Suite; fachlicher Nachfolger von Portal/e2e (Vaadin 7), das mit
# dem gesamten Alt-Portal-Modul in Phase 5 AP1 aus dem Repo entfernt wurde.
cd backend/e2e && npm test
```

Der SessionStart-Hook installiert die Parent-POM und wärmt die Client-Dependencies vor.
CI: `.github/workflows/ci.yml` baut/testet Client-Raspi (inkl. Cross-Component) und
Backend (JUnit + Playwright-E2E) bei jedem PR – kein separates Alt-Portal-Modul mehr
(entfernt in Phase 5 AP1, siehe kb/06-ui-tests.md).
