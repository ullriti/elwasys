# 01 – Architektur

## Modulübersicht

| Modul | artifactId | Version | Packaging | Java | Kernaufgabe |
|-------|------------|---------|-----------|------|-------------|
| Common | `common` | `0.0.0-local-development` (Client-Kontext) / `0.3.4-SNAPSHOT` (Portal-Kontext) | jar | 1.8 | Datenmodell, DB-Zugriff, Maintenance-Protokoll |
| Client-Raspi | `raspi-client` | `0.0.0-local-development` | jar (mit `jar-with-dependencies`) | 16 | Terminal-UI (JavaFX), Gerätesteuerung |
| Portal | `webportal` | `0.3.4-SNAPSHOT` | war | 1.8 | Admin-Weboberfläche (Vaadin) |
| Backend *(neu, seit Phase 2 AP1)* | `backend` | `0.0.0-local-development` | jar (Spring-Boot-Repackage) | 21 | Neues zentrales Backend (Spring Boot); AP1 liefert Gerüst + Flyway-Baseline, läuft parallel zum Bestand auf derselben DB |

> ⚠️ Inkonsistenz: Common wird von Client mit Version `0.0.0-local-development` referenziert,
> vom Portal aber mit `0.3.4-SNAPSHOT`. Die CI ersetzt `0.0.0-local-development` beim Release
> durch den Tag-Namen (nur für Common + Client, nicht Portal). Kein Parent-POM bindet die
> Module zusammen → jedes Modul wird einzeln gebaut (`mvn install` in Reihenfolge Common →
> Client/Portal).

## Namespaces (Java-Packages)

- `org.kabieror.elwasys.common` – gemeinsames Datenmodell & Infrastruktur
- `org.kabieror.elwasys.common.maintenance` – Fernwartungs-Protokoll (Nachrichten,
  Client/Server, Handler)
- `org.kabieror.elwasys.raspiclient.*` – Raspberry-Pi-Client
- `org.kabieror.elwasys.webportal.*` – Web-Portal
- `org.kabieror.elwasys.backend.*` – neues Spring-Boot-Backend (seit Phase 2 AP1)

## Technologie-Stack (Ist-Zustand)

### Common
- PostgreSQL JDBC (Treiber `42.6.0`)
- SLF4J + Logback (`1.1.3`)
- Apache Commons Email `1.4`, Commons Lang3 `3.4`
- JUnit 4.12

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

### Portal
- **Vaadin 7.6.8** (server, push, client, client-compiled, themes) + **GWT 2.7.0**
- PostgreSQL JDBC `9.3-1103-jdbc41` (alt!)
- Logback `1.1.3`, Commons Email `1.4`
- Servlet-API 3.0.1 (provided)
- Build: WAR, Jetty-Plugin `9.2.3` (`mvn jetty:run`, Port 8080), Vaadin-/GWT-Widgetset-
  Compilation

### Backend *(neu, seit Phase 2 AP1, zuletzt erweitert AP4)*
- **Spring Boot 3.5.16** (BOM-Import, siehe kb/03-modules.md), Java 21
- `spring-boot-starter-web`/`-actuator`/`-jdbc`/`-validation`/`-data-jpa`/`-security`/
  `-websocket` (letzteres seit AP4: Terminal-WebSocket-Endpunkt)
- **Flyway** (`flyway-core` + `flyway-database-postgresql`) für Migrationen
- **springdoc-openapi-starter-webmvc-ui `2.8.6`** (seit AP4) – generiert die OpenAPI-
  Beschreibung + Swagger-UI für `/api/v1/**` aus den Controllern (`/v3/api-docs`,
  `/swagger-ui.html`)
- PostgreSQL-Treiber `42.7.11`, Logback `1.5.34` (mitgeliefert von Spring Boot)
- Test: JUnit 5, Testcontainers (Default) mit lokalem PostgreSQL-Override für Docker-lose
  Umgebungen (siehe kb/04-build-and-run.md)
- Build: `spring-boot-maven-plugin` (repackage) → lauffähiges Jar; Modul-Property
  `maven.compiler.parameters=true` (seit AP4, nötig für `@RequestParam`/`@PathVariable`
  ohne expliziten Namen)

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

## Portal-Architektur (Vaadin)

- **`WaschportalUI`** – Vaadin-UI-Einstiegspunkt.
- **Layouts**: `PublicLayout`, `UserLayout`, `AdministratorLayout`.
- **Views** (`views/`): `AdminDashboardView`, `UsersView`, `UsersDashboardView`,
  `ProgramsView`, `DevicesView`, `UserGroupsView`.
- **Components** (`components/`): modale Fenster/Dialoge für CRUD – UserWindow,
  UserGroupWindow, DeviceWindow, ProgramWindow, LocationWindow, CreditAccountingWindow,
  UserCreditWindow, ChangePassword/ResetPassword/PasswordForgot, LogViewerWindow,
  ExpiredExecutionsWindow, MainMenu, UserSettingsWindow, ConfirmWindow.
- **`WashportalManager`** – zentraler Manager (analog ElwaManager).
- **`SessionManager`** – Session-/Login-Verwaltung.
- **`MaintenanceConnectionManager`** – Client-seitige Maintenance-Verbindung zum Raspi-
  Client (Status, Logs, Restart).
- **`events/`**: Listener-Interfaces für Updates (User/Group/Device/Location/Program).
- **Konfiguration**: `WashportalConfiguration` liest
  `/etc/elwaportal/elwaportal.properties`.

## Maintenance-Protokoll (Common) – ⚠️ STILLGELEGT für den Client (Phase 4 AP5, 2026-07-21)

Package `common.maintenance`: Nachrichtenbasiertes Alt-TCP-Protokoll mit
`MaintenanceMessage`/`MaintenanceRequest`/`MaintenanceResponse` und konkreten Nachrichten:
- Connection: `ConnectionRequest/Response`, `CloseConnectionMessage`,
  `CheckConnectionRequest/Response`
- Status/Log: `GetStatusRequest/Response`, `GetLogRequest/Response`
- Steuerung: `RestartAppRequest`, `ErrorMessage`
- Infrastruktur: `MaintenanceServer` (im Client), `MaintenanceClient` (im Portal),
  `IClientConnection`, `IMaintenanceMessageHandler`
- Datenobjekte (`data/`): `InterfaceStatus`, `BacklightStatus`

Bis Phase 4 AP5 startete der Client einen `MaintenanceServer` (Port aus `maintenance.port`,
Default 3591), das Portal verband sich als `MaintenanceClient`; der Client registrierte in der
DB-Tabelle `locations` seine `client_ip`, `client_port`, `client_uid`, `client_last_seen`,
damit das Portal wusste, wie es ihn erreicht.

**Seit Phase 4 AP5 spricht der Client-Raspi dieses Protokoll nicht mehr** (`application/
MaintenanceServerManager` und `configuration/LocationManager` sind aus `Client-Raspi/src/main`
entfernt) – die Fernwartung läuft jetzt über die vom Terminal ausgehende
Backend-WebSocket-Verbindung, siehe `ws/TerminalWebSocketClient` oben und „Kommunikationswege“
unten. Der Package-Code selbst (`Common.maintenance.*`) bleibt laut Roadmap **bis Phase 5** im
Repo – das Alt-Portal (`Portal/`, bis Phase 5 unverändert im Repo, siehe kb/03-modules.md)
nutzt `MaintenanceClient`/`MaintenanceConnectionManager` weiterhin in seinem eigenen,
unangetasteten Code, spricht damit aber seit AP5 fachlich ins Leere (kein Client hört mehr
zu) – dasselbe Bild wie bereits seit Phase 3 AP6 für das übrige Alt-Portal (stillgelegt als
E2E-/Produktivziel, Code bleibt bestehen). Die Spalten `client_ip`/`client_port`/`client_uid`/
`client_last_seen` in `locations` werden **nicht mehr beschrieben/gelesen**, aber laut Roadmap
**nicht** per Migration entfernt (siehe kb/02-data-model.md, Entfall erst Phase 5).

## Kommunikationswege (Zusammenfassung)

**Stand seit Phase 4 AP5 (2026-07-21, Fernwartung umgedreht)**: der Client-Raspi hat **keinen
Direkt-DB-Zugriff mehr** (Weg 1 unten ist vollständig entfallen, nicht nur geschrumpft) – alle
Terminal-Datenzugriffe (Fachdaten UND Fernwartung) laufen über die Backend-Wege 8/9 (siehe
unten). Weg 3 (`Portal ⇄ Client`-Maintenance-WebSocket, Alt-TCP-Protokoll) ist damit fachlich
tot (kein Client hört mehr zu), der Code (Alt-Portal + `Common.maintenance.*`) bleibt aber laut
Roadmap bis Phase 5 im Repo. Wege 5/6 (Client → SMTP/Pushover) sind bereits mit dem AP4-Cutover
entfallen – der Client verschickt keine Benachrichtigungen mehr selbst (siehe
kb/05-migration-plan.md, Änderungslog „Phase 4 AP4“).

1. ~~**Client ⇄ DB**~~ (PostgreSQL, User `elwaclient1`) – **entfallen seit Phase 4 AP5**: die
   letzte verbliebene Nutzung (Fernwartungs-Registrierung über `LocationManager`) ist mit der
   Umstellung auf Weg 9 (unten) weggefallen; der DB-User `elwaclient1` und die dazugehörigen
   `elwasys.properties`-Schlüssel (`database.*`) werden vom Client nicht mehr gebraucht (siehe
   kb/04-build-and-run.md). `Common.DataManager` ist damit vollständig aus dem
   Client-Raspi-Produktivcode raus (kein `DataManager`/JDBC-Import mehr in `src/main`).
2. **Portal ⇄ DB** (PostgreSQL, User `elwaportal`, volle CRUD-Rechte außer credit_accounting-
   Änderungen)
3. ~~**Portal ⇄ Client** (Maintenance-WebSocket/Alt-TCP-Protokoll)~~ – **fachlich tot seit
   Phase 4 AP5** (siehe „Maintenance-Protokoll (Common)“ oben); Code bleibt bis Phase 5 im
   Repo, wird aber von keinem Client mehr bedient.
4. **Client ⇄ deCONZ** (REST + WebSocket-Events, für Schalten & Leistungsmessung)
5. ~~**Client → SMTP** (E-Mail-Benachrichtigung)~~ – entfallen seit Phase 4 AP4, Backend
   versendet zentral (siehe Weg 8, `NotificationService`)
6. ~~**Client → Pushover** (Push-Benachrichtigung)~~ – entfallen seit Phase 4 AP4, siehe oben
7. **elwaapi**-DB-User (in DB-Schema angelegt) – vermutlich für eine mobile App
   (`app_id`/`access_key`/`auth_key` in `users`, Tabelle `reservations`), App-Code nicht in
   diesem Repo.

## Neues Backend: Terminal-API/WebSocket (seit Phase 2 AP4, vollständig produktiv seit Phase 4 AP5)

Zusätzlich zu den obigen (Alt-Code-)Kommunikationswegen, die bis zum jeweiligen Cutover
(Phase 3 Portal, Phase 4 Terminal) unverändert weiterliefen, bietet das neue Backend seit
Phase 2 AP4 zwei neue, parallele Wege an. **Seit Phase 4 AP4 ist Weg 8 der primäre
Datenzugriffspfad des Terminals**; **seit Phase 4 AP5 ist Weg 9 zusätzlich der einzige
Fernwartungsweg** – zusammen sind das jetzt ALLE Terminal-Datenzugriffe (kein Weg 1 mehr, siehe
oben).

8. **Terminal ⇄ Backend REST** (`/api/v1/**`, Standort-Token im `Authorization: Bearer`-
   Header) – Kartenlogin, Geräte-/Programmliste, Execution-Lebenszyklus, Guthaben. Seit
   Phase 4 AP4 der primäre Datenzugriffspfad des Client-Raspi (`api/ApiClient` in
   `Client-Raspi/.../api/`, siehe kb/03-modules.md).
9. **Terminal ⇄ Backend WebSocket** (`/api/v1/terminal-ws`, dasselbe Standort-Token beim
   Handshake, dauerhaft vom Terminal ausgehend gehalten) – Ereignis-Push-Fundament UND, seit
   Phase 4 AP5, der tatsächliche Fernwartungskanal (Status/Log/Restart – fachlicher Nachfolger
   von `Common.maintenance.*`/Weg 3): der Client (`ws/TerminalWebSocketClient`) bedient
   portal-initiierte STATUS_REQUEST/LOG_REQUEST/RESTART_REQUEST-Nachrichten, die das Backend
   (`TerminalMaintenanceService`) auf Anfrage der Portal-Admin-UI über GENAU diese Verbindung
   verschickt – ersetzt damit vollständig Weg 3 (`Portal ⇄ Client`-Maintenance-WebSocket) samt
   der IP-Registrierung in `locations` (`client_ip`/`-port`/`-uid`/`-last_seen`, jetzt obsolet,
   siehe kb/02-data-model.md).

Vollständige Endpunkt-/Nachrichtenreferenz: kb/03-modules.md (Abschnitt Backend, AP4/AP5).
