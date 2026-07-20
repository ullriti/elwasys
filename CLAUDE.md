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

**Aktueller Stand (2026-07-20):** Phase 0 (Sicherheitsnetz: Build + E2E-Tests) und
Phase 1 (Fundament: Parent-POM, Java 21, JUnit 5, ElwaManager-DI) sind abgeschlossen,
alle Grundsatzentscheidungen sind gefallen. Phase 2 ist gestartet: AP1 (Backend-Gerüst +
Flyway-Baseline), AP2 (JPA-Entities/Repositories + Geschäftslogik-Portierung:
Abrechnung, Berechtigungen, Preisberechnung, Execution-Lebenszyklus), AP3 (Auth:
Argon2id-Hashing + SHA1-Migrationspfad, Login-/Session-Handling), AP4 (REST-API v1 +
Standort-Token-Auth + WebSocket-Endpunkt für Terminals), AP5 (Benachrichtigungsdienst
SMTP/Pushover, implementiert/getestet hinter Konfig-Flag, Default AUS – Scharfschaltung
mit echten Ereignissen folgt Phase 4) und AP6 (Deployment: Dockerfile + docker-compose,
Helm Chart für Kubernetes, TLS-Konzept) sind abgeschlossen – die Phase-2-Roadmap ist damit
vollständig abgearbeitet. Phase 2 selbst gilt erst nach einer abschließenden QA-Review als
abgeschlossen; **nächster Schritt: QA-Review von AP6, danach Entscheidung über Phase 3**
(Portal-Neubau) – siehe Roadmap in kb/05.

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
Client-Raspi/run-cross-component-e2e.sh   # Wartungsverbindung Portal⇄Client

# Portal: Playwright-E2E (Setup/DB/Jetty siehe Portal/e2e/README.md u. kb/06)
cd Portal/e2e && npm test
```

Der SessionStart-Hook installiert Common und wärmt die Client-Dependencies vor.
CI: `.github/workflows/ci.yml` baut/testet alle drei Module bei jedem PR.
