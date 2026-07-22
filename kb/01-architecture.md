# 01 – Architektur

> **Zielarchitektur (Stand Phase 5, siehe kb/05-migration-plan.md)**: Root-Reactor mit 2
> Modulen (Client-Raspi [Terminal, enthält seit dem Phase-5-Nachtrag auch die 6 ehemaligen
> Common-Klassen], backend [REST-API/WebSocket für Terminals + eingebettetes
> Vaadin-Flow-Admin-Portal + Benachrichtigungsdienst]). Das
> ursprüngliche Alt-Portal-Modul (`Portal/`, Vaadin 7) ist seit Phase 3 AP6 als E2E-/
> Produktivziel abgelöst und seit **Phase 5 AP1 vollständig aus dem Repository entfernt**; die
> Terminals haben seit Phase 4 AP4/AP5 **keinen Direkt-DB-Zugriff mehr** (nur noch REST-API +
> ausgehender WebSocket, beide standort-token-authentifiziert). Dieses Dokument beschreibt den
> aktuellen (End-)Zustand; historische Zwischenstände (Alt-Portal-Modul, Alt-TCP-
> Maintenance-Protokoll, Terminal-Direkt-DB-Zugriff) sind unten explizit als „entfernt"/
> „abgelöst" markiert, mit Verweis auf das jeweilige Arbeitspaket.

## Modulübersicht

| Modul | artifactId | Version | Packaging | Java | Kernaufgabe |
|-------|------------|---------|-----------|------|-------------|
| Client-Raspi | `raspi-client` | `0.0.0-local-development` | jar (mit `jar-with-dependencies`) | 21 (seit Phase 1) | Terminal-UI (JavaFX), Gerätesteuerung; seit Phase 4 AP4/AP5 ausschließlich über REST-API + ausgehenden WebSocket mit dem Backend verbunden, kein Direkt-DB-Zugriff mehr. Enthält seit dem Phase-5-Nachtrag auch die 6 ehemaligen Common-Klassen (Package `org.kabieror.elwasys.common`, physisch unter `Client-Raspi/src/main/org/kabieror/elwasys/common/`) |
| backend | `backend` | `0.0.0-local-development` | jar (Spring-Boot-Repackage) | 21 | Zentrales Backend (Spring Boot): REST-API/WebSocket für Terminals, eingebettetes Vaadin-Flow-Admin-Portal (seit Phase 3), Benachrichtigungsdienst, Flyway-verwaltetes Schema |
| ~~Common~~ | ~~`common`~~ | – | – | – | **Aufgelöst (Phase-5-Nachtrag)** – die kleine gemeinsame Bibliothek (Enum-Typ, Format-/Konfigurationshilfen) wurde als eigenständiges Modul entfernt; ihre 6 verbliebenen Klassen liegen unverändert im Package `org.kabieror.elwasys.common` jetzt im Client-Raspi-Modul. Nur das Terminal nutzt sie zur Laufzeit; das Backend hatte nie eine Produktiv-Abhängigkeit auf `common` |
| ~~Portal~~ | ~~`webportal`~~ | – | – | – | **Entfernt (Phase 5 AP1)** – ehemalige eigenständige Vaadin-7-Admin-Weboberfläche, fachlich abgelöst durch das ins Backend eingebettete Portal-UI seit Phase 3 AP6 (volle Feature-Parität, siehe kb/06-ui-tests.md) |

Ein Aggregator-Parent-POM (seit Phase 1) bindet die zwei verbliebenen Module zusammen; Bau
über den Root-Reactor (`mvn install` bzw. `mvn -N install -DskipTests` für die reine
Parent-POM, siehe kb/04-build-and-run.md).

## Namespaces (Java-Packages)

- `org.kabieror.elwasys.common` – kleine gemeinsame Bibliothek (seit Phase 5 AP1 auf 6 Klassen
  geschrumpft, siehe kb/03-modules.md); das eigenständige Common-Modul ist im Phase-5-Nachtrag
  **aufgelöst**, die 6 Klassen liegen seither unverändert im Client-Raspi-Modul
  (`Client-Raspi/src/main/org/kabieror/elwasys/common/`); ~~`org.kabieror.elwasys.common.maintenance`~~
  (Alt-TCP-Fernwartungsprotokoll) ist mit dem restlichen Alt-Bestand **in Phase 5 AP1
  entfernt**
- `org.kabieror.elwasys.raspiclient.*` – Raspberry-Pi-Client
- `org.kabieror.elwasys.backend.*` – zentrales Spring-Boot-Backend (seit Phase 2 AP1), inkl.
  `backend/.../ui/` für das Vaadin-Flow-Admin-Portal (seit Phase 3 AP1)
- ~~`org.kabieror.elwasys.webportal.*`~~ – Alt-Portal, **entfernt (Phase 5 AP1)**

## Technologie-Stack (Ist-Zustand)

### Common (Modul aufgelöst, Phase-5-Nachtrag)
Das eigenständige Common-Modul existiert nicht mehr. Seine 6 verbliebenen Klassen (Package
`org.kabieror.elwasys.common`, siehe kb/03-modules.md) liegen unverändert im Client-Raspi-Modul
(`Client-Raspi/src/main/org/kabieror/elwasys/common/`) und werden mit dem Client auf Sprachlevel
21 gebaut. Die von ihnen benötigten Bibliotheken sind seither direkte Client-Raspi-Dependencies
(u. a. Commons Lang3 als direkte Abhängigkeit; der PostgreSQL-JDBC-Treiber nur noch
**test-scope**, da die Terminal-Produktion seit Phase 4 nicht mehr auf die DB zugreift und nur
die E2E-Harness per JDBC seedet).

### Client-Raspi
- **JavaFX 23.0.2** (`javafx-controls`, `javafx-fxml`, `javafx-web`) – UI über FXML
  *(Phase 4 AP2, 2026-07-21: 20 → 23.0.2, höchste stabile Version, die noch auf dem
  festgelegten Java-21-Client läuft – siehe kb/05-migration-plan.md)*
- **pi4j 1.0** – GPIO-Zugriff auf dem Raspberry Pi
- **Spring Boot Starter WebSocket 3.1.0** – für die ausgehende Terminal-WebSocket-Verbindung
  zum Backend (`ws/TerminalWebSocketClient`, seit Phase 4 AP5 – Fernwartung: Status/Log/
  Restart, siehe unten) und den deCONZ-WS-Client (`StandardWebSocketClient`), beide über
  dasselbe Muster (`StandardWebSocketClient`/`TextWebSocketHandler`)
- deCONZ-REST/Event-API: `java.net.http` (JDK, kein Fremd-Client) – unirest/HttpComponents
  wurden hierfür entgegen einer älteren Annahme nie gebraucht, siehe kb/03-modules.md
- Gson `2.10.1` – JSON für die REST-API v1 (`api/ApiClient`, seit Phase 4 AP4 der primäre
  Datenzugriffspfad, siehe kb/03-modules.md)
- ~~Pushover-Client, Commons Email~~ – entfernt (Phase 4 AP4): Benachrichtigungsversand
  läuft jetzt zentral über das Backend, siehe kb/05-migration-plan.md
- Logback `1.5.38` / SLF4J `2.0.18` *(Phase 4 AP2: 1.2.9/1.7.12 → 1.5.38/2.0.18)*
- Test: JUnit 5 (Jupiter) + TestFX/Xvfb *(seit Phase 1; JUnit4/TestNG-Mischbetrieb aufgelöst)*
- Build: `maven-assembly-plugin` → fat-jar (`jar-with-dependencies`), Main-Class
  `org.kabieror.elwasys.raspiclient.application.Main`

### ~~Portal~~ (entfernt, Phase 5 AP1)
Ehemals Vaadin 7.6.8 + GWT 2.7.0, WAR/Jetty, siehe kb/05-migration-plan.md (Änderungslog
„Phase 5 AP1") für den letzten Stand vor der Entfernung. Fachlich vollständig abgelöst durch
das Vaadin-Flow-Portal-UI im Backend (siehe unten und kb/03-modules.md, „Portal-UI (Vaadin
Flow)").

### Backend *(seit Phase 2 AP1, inkl. Vaadin-Flow-Portal-UI seit Phase 3 AP1)*
- **Spring Boot 3.5.16** (BOM-Import, siehe kb/03-modules.md), Java 21
- `spring-boot-starter-web`/`-actuator`/`-jdbc`/`-validation`/`-data-jpa`/`-security`/
  `-websocket` (Terminal-WebSocket-Endpunkt, seit Phase 2 AP4)
- **Vaadin Flow** (`vaadin-spring-boot-starter` 24.10.8, `hilla`+`collaboration-engine`
  ausgeschlossen, seit Phase 3 AP1) – das eingebettete Admin-Portal-UI, siehe
  kb/03-modules.md „Portal-UI (Vaadin Flow)"
- **Flyway** (`flyway-core` + `flyway-database-postgresql`) für Migrationen – alleiniger
  Schema-Verwaltungsweg, aktuell V1–V10 (siehe kb/02-data-model.md)
- **springdoc-openapi-starter-webmvc-ui `2.8.6`** (seit Phase 2 AP4) – generiert die OpenAPI-
  Beschreibung + Swagger-UI für `/api/v1/**` aus den Controllern (`/v3/api-docs`,
  `/swagger-ui.html`)
- PostgreSQL-Treiber `42.7.11`, Logback `1.5.34` (mitgeliefert von Spring Boot)
- Test: JUnit 5, Testcontainers (Default) mit lokalem PostgreSQL-Override für Docker-lose
  Umgebungen (siehe kb/04-build-and-run.md); **keine** Abhängigkeit auf `common` mehr – die 3
  Auth-Parity-Tests reproduzieren das Alt-SHA1-Format seit dem Phase-5-Nachtrag lokal über den
  Test-Helfer `LegacySha1` (`backend/src/test/.../auth/LegacySha1.java`)
- Build: `spring-boot-maven-plugin` (repackage) → lauffähiges Jar; Modul-Property
  `maven.compiler.parameters=true` (seit Phase 2 AP4, nötig für `@RequestParam`/
  `@PathVariable` ohne expliziten Namen); `-Pproduction`-Profil für den Vaadin-Produktions-
  build (siehe kb/04-build-and-run.md)

## Client-Architektur (Raspberry Pi)

Einstieg: `application/Main.java` (JavaFX `Application`). CLI-Flags:
`-f` (Vollbild), `-adafruitDisplay`, `-dry` (PowerManager ohne Funktion),
`-xsDisplay` (TOUCH_SMALL 320×240), `-mdDisplay` (TOUCH_MEDIUM 800×480).
Ohne Flag wird die Displaygröße automatisch anhand der Bildschirmbreite erkannt.

Zentrale Komponenten:

- **`ElwaManager`** (Singleton) – zentraler Manager, hält Verweise auf Configuration,
  `ApiClient` (seit Phase 4 AP4 der primäre und – seit Phase 4 AP5 – EINZIGE
  Datenzugriffspfad, siehe kb/03-modules.md), `ws/TerminalWebSocketClient` (seit Phase 4
  AP5: die ausgehende Fernwartungs-Verbindung zum Backend, ersetzt den ehemaligen
  `DataManager`/`LocationManager`/`MaintenanceServerManager`-Pfad – kein Direkt-DB-Zugriff
  mehr im Terminal), ExecutionManager, MainFormController, Location; koordiniert Start/Stop.
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
- **`configuration/`**: `WashguardConfiguration` – liest `elwasys.properties`. (Das ehemalige
  `LocationManager` – Direkt-DB-Registrierung des Standorts – ist seit Phase 4 AP5 entfernt,
  siehe unten.)
- **`ws/TerminalWebSocketClient`** *(neu, Phase 4 AP5)* – die ausgehende WebSocket-Verbindung
  zum Backend (`/api/v1/terminal-ws`, dasselbe Standort-Token wie `ApiClient`); bedient
  HELLO/PING sowie die portal-initiierten Fernwartungs-Anfragen STATUS_REQUEST/LOG_REQUEST/
  RESTART_REQUEST. Ersetzt das ehemalige `application/MaintenanceServerManager` (serverseitige
  Maintenance-Verbindung, Gegenstelle zum Portal über das Alt-TCP-Protokoll) – die Richtung
  ist umgedreht, siehe „Kommunikationswege“ unten und kb/03-modules.md.

## Portal-Architektur – ⚠️ Alt-Portal (Vaadin 7) entfernt, Phase 5 AP1

Die eigenständige Vaadin-7-Admin-Weboberfläche (`WaschportalUI`, Layouts `PublicLayout`/
`UserLayout`/`AdministratorLayout`, `views/`/`components/` mit den CRUD-Fenstern,
`WashportalManager`/`SessionManager`/`MaintenanceConnectionManager`, eigene Konfiguration
`/etc/elwaportal/elwaportal.properties`) ist seit Phase 3 AP6 fachlich abgelöst und seit
**Phase 5 AP1 vollständig aus dem Repository entfernt**. Ihr Nachfolger ist das in `backend`
eingebettete **Portal-UI (Vaadin Flow)**, Package `backend/.../ui/` – gleiche Grundstruktur
(Login, Admin-/Nutzer-Layout, Stammdaten-Grids, CRUD-Dialoge, Guthaben, Passwort-Verwaltung,
Log-Viewer/Fernwartung), aber mit UX-Verbesserungen und echten Cross-Session-Live-Updates
(`UiBroadcaster`) statt der nie aktivierten Alt-Portal-Push-Mechanik. Volle Feature-Parität
ist über die portierte Playwright-Suite `backend/e2e/` (P1–P20) nachgewiesen. Details/
Komponentenliste: kb/03-modules.md, Abschnitt „Portal-UI (Vaadin Flow)".

## Maintenance-Protokoll – ⚠️ Alt-TCP-Protokoll abgelöst (Phase 4 AP5) und entfernt (Phase 5 AP1)

Das ehemalige Package `common.maintenance` (Nachrichtenbasiertes Alt-TCP-Protokoll:
`MaintenanceMessage`/`MaintenanceRequest`/`MaintenanceResponse`, `GetStatusRequest/Response`,
`GetLogRequest/Response`, `RestartAppRequest`, `MaintenanceServer`/`MaintenanceClient`) ist mit
dem restlichen Alt-Bestand in **Phase 5 AP1 vollständig aus `Common` entfernt**.

Bis Phase 4 AP5 startete der Client einen `MaintenanceServer` (Port aus `maintenance.port`,
Default 3591), das Alt-Portal verband sich als `MaintenanceClient`; der Client registrierte in
der DB-Tabelle `locations` seine `client_ip`, `client_port`, `client_uid`, `client_last_seen`,
damit das Portal wusste, wie es ihn erreicht.

**Seit Phase 4 AP5 spricht der Client-Raspi dieses Protokoll nicht mehr** (`application/
MaintenanceServerManager` und `configuration/LocationManager` sind aus `Client-Raspi/src/main`
entfernt) – die Fernwartung läuft seither über die vom Terminal ausgehende
Backend-WebSocket-Verbindung, siehe `ws/TerminalWebSocketClient` oben und „Kommunikationswege“
unten. Die Spalten `client_ip`/`client_port`/`client_uid`/`client_last_seen` in `locations`
wurden zunächst nur funktional ungenutzt belassen und sind seit **Phase 5 AP3**
(`V9__drop_obsolete_location_client_columns.sql`) per Migration entfernt (siehe
kb/02-data-model.md).

## Kommunikationswege (Zusammenfassung)

**Endzustand seit Phase 5** (siehe kb/05-migration-plan.md, Zielarchitektur): der
Client-Raspi hat **keinen Direkt-DB-Zugriff mehr** und spricht ausschließlich mit dem Backend
(Wege 1/2 unten); das Alt-Portal-Modul, das Alt-TCP-Maintenance-Protokoll und die
`elwaapi`-App-Reste sind vollständig aus Repo/Schema entfernt (Phase 5 AP1/AP4). Die früher
hier als eigene Wege aufgeführten Alt-Code-Kommunikationspfade (Client⇄DB, Portal⇄DB,
Portal⇄Client-Maintenance, Client→SMTP/Pushover, `elwaapi`-DB-User) existieren dadurch nicht
mehr – die Terminal-Kommunikation läuft vollständig über die Backend-Wege 1/2.

1. **Terminal ⇄ Backend REST** (`/api/v1/**`, Standort-Token im `Authorization: Bearer`-
   Header) – Kartenlogin, Geräte-/Programmliste, Execution-Lebenszyklus, Guthaben. Seit
   Phase 4 AP4 der primäre und – seit Phase 4 AP5 – einzige fachliche Datenzugriffspfad des
   Client-Raspi (`api/ApiClient` in `Client-Raspi/.../api/`, siehe kb/03-modules.md).
2. **Terminal ⇄ Backend WebSocket** (`/api/v1/terminal-ws`, dasselbe Standort-Token beim
   Handshake, dauerhaft vom Terminal ausgehend gehalten) – Ereignis-Push-Fundament UND, seit
   Phase 4 AP5, der Fernwartungskanal (Status/Log/Restart – fachlicher Nachfolger des
   entfernten Alt-TCP-Protokolls): der Client (`ws/TerminalWebSocketClient`) bedient
   portal-initiierte STATUS_REQUEST/LOG_REQUEST/RESTART_REQUEST-Nachrichten, die das Backend
   (`TerminalMaintenanceService`) auf Anfrage der Portal-Admin-UI über GENAU diese Verbindung
   verschickt.
3. **Backend ⇄ DB** (PostgreSQL, User `elwaportal`) – der Backend-Prozess ist seit Phase 5 AP2
   der einzige Anwendungs-DB-User (die Alt-Rollen `elwaclient1`/`elwaapi` + Gruppe
   `elwaclients` sind per `V6__harden_db_roles.sql` entfernt).
4. **Client ⇄ deCONZ** (REST + WebSocket-Events, für Schalten & Leistungsmessung) bzw.
   **Client ⇄ fhem** (Telnet) – unverändert, siehe „Client-Architektur“ oben.
5. **Backend → SMTP/Pushover** (`NotificationService`, seit Phase 2 AP5 implementiert, seit
   Phase 4 AP3 an `finish`/`abort` angebunden, hinter `elwasys.notifications.enabled`
   [Default AUS, Scharfschaltung ein operativer Schritt der Phase-6-Produktivumschaltung]) –
   der Client verschickt seit Phase 4 AP4 keine eigenen Benachrichtigungen mehr.

Historische Zwischenstände (Alt-Portal⇄DB, Alt-TCP-Maintenance, Client-Direkt-DB, `elwaapi`)
sind in kb/05-migration-plan.md (Änderungslog, Phasen 2–5) im Detail nachvollziehbar.

Vollständige Endpunkt-/Nachrichtenreferenz: kb/03-modules.md (Abschnitt Backend, AP4/AP5).
