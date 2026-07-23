# 01 – Architektur

Root-Reactor mit zwei Modulen: **Client-Raspi** (Terminal, JavaFX; enthält auch die 6
Utility-Klassen des Packages `org.kabieror.elwasys.common`) und **backend** (REST-API/WebSocket
für Terminals + eingebettetes Vaadin-Flow-Admin-Portal + Benachrichtigungsdienst). Die Terminals
haben **keinen Direkt-DB-Zugriff**; sie sprechen ausschließlich die Backend-REST-API und eine
ausgehende, standort-token-authentifizierte WebSocket-Verbindung. Details zum Weg dorthin:
[05-migration-plan.md](05-migration-plan.md).

## Modulübersicht

| Modul | artifactId | Version | Packaging | Java | Kernaufgabe |
|-------|------------|---------|-----------|------|-------------|
| Client-Raspi | `raspi-client` | `0.0.0-local-development` | jar (mit `jar-with-dependencies`) | 21 | Terminal-UI (JavaFX), Gerätesteuerung; mit dem Backend ausschließlich über REST-API + ausgehenden WebSocket verbunden, kein Direkt-DB-Zugriff. Enthält auch die 6 Utility-Klassen im Package `org.kabieror.elwasys.common` (physisch unter `Client-Raspi/src/main/org/kabieror/elwasys/common/`) |
| backend | `backend` | `0.0.0-local-development` | jar (Spring-Boot-Repackage) | 21 | Zentrales Backend (Spring Boot): REST-API/WebSocket für Terminals, eingebettetes Vaadin-Flow-Admin-Portal, Benachrichtigungsdienst, Flyway-verwaltetes Schema |

Ein Aggregator-Parent-POM bindet die zwei Module zusammen; Bau über den Root-Reactor
(`mvn install` bzw. `mvn -N install -DskipTests` für die reine Parent-POM, siehe
docs/kb/04-build-and-run.md).

## Namespaces (Java-Packages)

- `org.kabieror.elwasys.common` – 6 Utility-Klassen (Enum-Typ, Format-/Konfigurationshilfen,
  siehe docs/kb/03-modules.md), physisch im Client-Raspi-Modul
  (`Client-Raspi/src/main/org/kabieror/elwasys/common/`)
- `org.kabieror.elwasys.raspiclient.*` – Raspberry-Pi-Client
- `org.kabieror.elwasys.backend.*` – zentrales Spring-Boot-Backend, inkl.
  `backend/.../ui/` für das Vaadin-Flow-Admin-Portal

## Technologie-Stack (Ist-Zustand)

### Client-Raspi
- **JavaFX 23.0.2** (`javafx-controls`, `javafx-fxml`, `javafx-web`) – UI über FXML
- **pi4j 1.0** – GPIO-Zugriff auf dem Raspberry Pi
- **Spring Boot Starter WebSocket 3.1.0** – für die ausgehende Terminal-WebSocket-Verbindung
  zum Backend (`ws/TerminalWebSocketClient` – Fernwartung: Status/Log/Restart, siehe unten) und
  den deCONZ-WS-Client (`StandardWebSocketClient`), beide über dasselbe Muster
  (`StandardWebSocketClient`/`TextWebSocketHandler`)
- deCONZ-REST/Event-API: `java.net.http` (JDK, kein Fremd-Client), siehe docs/kb/03-modules.md
- Gson `2.10.1` – JSON für die REST-API v1 (`api/ApiClient`, der Datenzugriffspfad des Terminals,
  siehe docs/kb/03-modules.md)
- Logback `1.5.38` / SLF4J `2.0.18`
- Die 6 `common`-Utility-Klassen werden mit dem Client auf Sprachlevel 21 gebaut; die von ihnen
  benötigten Bibliotheken sind direkte Client-Raspi-Dependencies (u. a. Commons Lang3). Der
  PostgreSQL-JDBC-Treiber ist nur **test-scope** – die Terminal-Produktion greift nicht auf die
  DB zu, nur die E2E-Harness seedet per JDBC.
- Test: JUnit 5 (Jupiter) + TestFX/Xvfb
- Build: `maven-assembly-plugin` → fat-jar (`jar-with-dependencies`), Main-Class
  `org.kabieror.elwasys.raspiclient.application.Main`

### Backend
- **Spring Boot 3.5.16** (BOM-Import, siehe docs/kb/03-modules.md), Java 21
- `spring-boot-starter-web`/`-actuator`/`-jdbc`/`-validation`/`-data-jpa`/`-security`/
  `-websocket` (Terminal-WebSocket-Endpunkt)
- **Vaadin Flow** (`vaadin-spring-boot-starter` 24.10.8, `hilla`+`collaboration-engine`
  ausgeschlossen) – das eingebettete Admin-Portal-UI, siehe docs/kb/03-modules.md
  „Portal-UI (Vaadin Flow)"
- **Flyway** (`flyway-core` + `flyway-database-postgresql`) für Migrationen – alleiniger
  Schema-Verwaltungsweg, aktuell V1–V11 (siehe docs/kb/02-data-model.md)
- **springdoc-openapi-starter-webmvc-ui `2.8.6`** – generiert die OpenAPI-Beschreibung +
  Swagger-UI für `/api/v1/**` aus den Controllern (`/v3/api-docs`, `/swagger-ui.html`)
- PostgreSQL-Treiber `42.7.11`, Logback `1.5.34` (mitgeliefert von Spring Boot)
- Test: JUnit 5, Testcontainers (Default) mit lokalem PostgreSQL-Override für Docker-lose
  Umgebungen (siehe docs/kb/04-build-and-run.md); **keine** Abhängigkeit auf `common` – die 3
  Auth-Parity-Tests reproduzieren das Alt-SHA1-Format lokal über den Test-Helfer `LegacySha1`
  (`backend/src/test/.../auth/LegacySha1.java`)
- Build: `spring-boot-maven-plugin` (repackage) → lauffähiges Jar; Modul-Property
  `maven.compiler.parameters=true` (nötig für `@RequestParam`/`@PathVariable` ohne expliziten
  Namen); `-Pproduction`-Profil für den Vaadin-Produktionsbuild (siehe docs/kb/04-build-and-run.md)

## Client-Architektur (Raspberry Pi)

Einstieg: `application/Main.java` (JavaFX `Application`). CLI-Flags:
`-f` (Vollbild), `-adafruitDisplay`, `-dry` (PowerManager ohne Funktion),
`-xsDisplay` (TOUCH_SMALL 320×240), `-mdDisplay` (TOUCH_MEDIUM 800×480).
Ohne Flag wird die Displaygröße automatisch anhand der Bildschirmbreite erkannt.

Zentrale Komponenten:

- **`ElwaManager`** (Singleton) – zentraler Manager, hält Verweise auf Configuration,
  `ApiClient` (der einzige fachliche Datenzugriffspfad, siehe docs/kb/03-modules.md),
  `ws/TerminalWebSocketClient` (die ausgehende Fernwartungs-Verbindung zum Backend),
  ExecutionManager, MainFormController, Location; koordiniert Start/Stop.
- **UI** (`ui/`): zwei Varianten, `small` (320×240) und `medium` (800×480). Medium ist die
  Hauptvariante. FXML + Controller pro View.
  - **State-Machine**: `MainFormState` (Enum) + `MainFormStateManager` +
    `MainFormStateTransition`. Zustände u. a. STARTUP, SELECT_DEVICE, SELECT_PROGRAM,
    CONFIRMATION_*, ERROR (siehe `ui/MainFormState.java`).
  - Views (medium): Startup, DeviceList/DeviceView, ProgramList, Confirmation, Abort,
    Error, Toolbar, UserSettings, Wait, CopyrightBar.
  - **Inaktivitäts-Scheduler** (`ui/scheduler/`): Auto-Logout, Display-Backlight-
    Steuerung (`BacklightManager`).
- **`executions/`**: `ExecutionManager`, `ExecutionFinisher` – Verwaltung laufender
  Programm-Ausführungen, Ende-Erkennung, Listener.
- **`devices/`**: Geräteabstraktion.
  - `deconz/`: **DeconzService**, `DeconzApiAdapter`, `DeconzEventListener` (WebSocket),
    `DeconzDevicePowerManager`, `DeconzRegistrationService` + Modelklassen → Zigbee über
    deCONZ.
  - `FhemDevicePowerManager` – Alternative über fhem.
  - Interfaces: `IDevicePowerManager`, `IDeviceRegistrationService`,
    `IDevicePowerMeasurementHandler`, `DevicePowerState`.
- **`io/`**: `CardReader` (RFID über `TelnetClient`), Card-Events.
- **`configuration/`**: `WashguardConfiguration` – liest `elwasys.properties`.
- **`ws/TerminalWebSocketClient`** – die ausgehende WebSocket-Verbindung zum Backend
  (`/api/v1/terminal-ws`, dasselbe Standort-Token wie `ApiClient`); bedient HELLO/PING sowie die
  portal-initiierten Fernwartungs-Anfragen STATUS_REQUEST/LOG_REQUEST/RESTART_REQUEST, siehe
  „Kommunikationswege" unten und docs/kb/03-modules.md.

## Portal-Architektur (Fernwartung)

Das Admin-Portal ist ein in `backend` eingebettetes **Portal-UI (Vaadin Flow)**, Package
`backend/.../ui/` (Login, Admin-/Nutzer-Layout, Stammdaten-Grids, CRUD-Dialoge, Guthaben,
Passwort-Verwaltung, Log-Viewer/Fernwartung) mit echten Cross-Session-Live-Updates
(`UiBroadcaster`). Komponentenliste: docs/kb/03-modules.md, Abschnitt „Portal-UI (Vaadin Flow)".

Die **Fernwartung** (Status/Log/Restart) läuft über die vom Terminal ausgehende
Backend-WebSocket-Verbindung: die Portal-Admin-UI beauftragt den `TerminalMaintenanceService`,
der STATUS_REQUEST/LOG_REQUEST/RESTART_REQUEST über genau diese Verbindung an den Client
(`ws/TerminalWebSocketClient`) schickt. Siehe „Kommunikationswege" unten.

## Kommunikationswege (Zusammenfassung)

1. **Terminal ⇄ Backend REST** (`/api/v1/**`, Standort-Token im `Authorization: Bearer`-
   Header) – Kartenlogin, Geräte-/Programmliste, Execution-Lebenszyklus, Guthaben. Der einzige
   fachliche Datenzugriffspfad des Client-Raspi (`api/ApiClient` in `Client-Raspi/.../api/`,
   siehe docs/kb/03-modules.md).
2. **Terminal ⇄ Backend WebSocket** (`/api/v1/terminal-ws`, dasselbe Standort-Token beim
   Handshake, dauerhaft vom Terminal ausgehend gehalten) – Ereignis-Push-Fundament UND der
   Fernwartungskanal (Status/Log/Restart): der Client (`ws/TerminalWebSocketClient`) bedient
   portal-initiierte STATUS_REQUEST/LOG_REQUEST/RESTART_REQUEST-Nachrichten, die das Backend
   (`TerminalMaintenanceService`) auf Anfrage der Portal-Admin-UI über GENAU diese Verbindung
   verschickt.
3. **Backend ⇄ DB** (PostgreSQL, User `elwaportal`) – der Backend-Prozess ist der einzige
   Anwendungs-DB-User.
4. **Client ⇄ deCONZ** (REST + WebSocket-Events, für Schalten & Leistungsmessung) bzw.
   **Client ⇄ fhem** (Telnet) – siehe „Client-Architektur" oben.
5. **Backend → SMTP/Pushover** (`NotificationService`, an `finish`/`abort` angebunden, hinter
   `elwasys.notifications.enabled` [Default AUS, Scharfschaltung ein operativer Schritt der
   Produktivumschaltung]) – der Client verschickt keine eigenen Benachrichtigungen.

Vollständige Endpunkt-/Nachrichtenreferenz: docs/kb/03-modules.md (Abschnitt Backend).

## Historie

- **2026-07-22** — Common-Modul aufgelöst: die 6 Utility-Klassen (Package
  `org.kabieror.elwasys.common`) liegen seither im Client-Raspi-Modul, mit Commons Lang3 als
  direkter Client-Dependency; die Backend-Auth-Tests reproduzieren das Alt-SHA1-Format über den
  Test-Helfer `LegacySha1` statt über `common`
  ([Worklog Phase-5-Nachtrag](../worklog/2026-07-22-phase-5-nachtrag-common-und-schema.md)).
- **2026-07-21** — Eigenständiges Vaadin-7-Portal-Modul (`Portal/`, `webportal`-Namespace) und
  das Alt-TCP-Maintenance-Protokoll (Package `common.maintenance`,
  `MaintenanceServer`/`MaintenanceClient`, Port 3591) vollständig aus dem Repository entfernt;
  die `elwaapi`/`elwaclient1`-Alt-DB-Rollen abgelöst (`elwaportal` als einziger App-User)
  ([Worklog Phase 5](../worklog/2026-07-21-phase-5-aufraeumen.md) · [Änderungslog](05-migration-plan.md)).
- **2026-07-21** — Terminal-Modernisierung: Direkt-DB-Zugriff der Terminals entfernt
  (`MaintenanceServerManager`/`LocationManager` entfallen), Datenzugriff über `ApiClient`,
  Fernwartung über ausgehenden `TerminalWebSocketClient`; JavaFX 20 → 23.0.2,
  Logback/SLF4J angehoben, Client-Benachrichtigungsversand ins Backend verlagert
  ([Worklog Phase 4](../worklog/2026-07-21-phase-4-terminal-modernisierung.md) · [Änderungslog](05-migration-plan.md)).
- **2026-07-20** — Admin-Portal als in das Backend eingebettetes Vaadin-Flow-UI neu gebaut,
  fachlich äquivalent zum abgelösten Alt-Portal (Feature-Parität über Playwright-Suite
  `backend/e2e/`) ([Worklog Phase 3](../worklog/2026-07-20-phase-3-portal-neubau.md)).
- **2026-07-20** — Spring-Boot-Backend eingeführt (REST-API/WebSocket, springdoc-OpenAPI,
  Flyway) ([Worklog Phase 2](../worklog/2026-07-20-phase-2-backend-geruest.md)).
- **2026-07-20** — Aggregator-Parent-POM und einheitliches Java-21-Sprachlevel eingeführt
  ([Worklog Phase 0/1](../worklog/2026-07-20-phase-0-und-1-fundament.md)).
