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
- **JavaFX 20** (`javafx-controls`, `javafx-fxml`, `javafx-web`) – UI über FXML
- **pi4j 1.0** – GPIO-Zugriff auf dem Raspberry Pi
- **Spring Boot Starter WebSocket 3.1.0** – für Maintenance-Verbindung
- Apache HttpComponents (httpclient `4.5.13`, httpasyncclient, httpmime), `org.json`,
  **unirest-java 1.4.9** – für deCONZ-REST/Event-API
- Gson `2.10.1`
- Pushover-Client `1.0.0` – Push-Benachrichtigungen
- Commons Email `1.5` – E-Mail
- Logback `1.2.9`
- Test: JUnit 4.13.1 **und** TestNG (gemischt)
- Build: `maven-assembly-plugin` → fat-jar (`jar-with-dependencies`), Main-Class
  `org.kabieror.elwasys.raspiclient.application.Main`

### Portal
- **Vaadin 7.6.8** (server, push, client, client-compiled, themes) + **GWT 2.7.0**
- PostgreSQL JDBC `9.3-1103-jdbc41` (alt!)
- Logback `1.1.3`, Commons Email `1.4`
- Servlet-API 3.0.1 (provided)
- Build: WAR, Jetty-Plugin `9.2.3` (`mvn jetty:run`, Port 8080), Vaadin-/GWT-Widgetset-
  Compilation

### Backend *(neu, seit Phase 2 AP1)*
- **Spring Boot 3.5.16** (BOM-Import, siehe kb/03-modules.md), Java 21
- `spring-boot-starter-web`/`-actuator`/`-jdbc`/`-validation`
- **Flyway** (`flyway-core` + `flyway-database-postgresql`) für Migrationen; JPA folgt erst
  im nächsten Arbeitspaket
- PostgreSQL-Treiber `42.7.11`, Logback `1.5.34` (mitgeliefert von Spring Boot)
- Test: JUnit 5, Testcontainers (Default) mit lokalem PostgreSQL-Override für Docker-lose
  Umgebungen (siehe kb/04-build-and-run.md)
- Build: `spring-boot-maven-plugin` (repackage) → lauffähiges Jar

## Client-Architektur (Raspberry Pi)

Einstieg: `application/Main.java` (JavaFX `Application`). CLI-Flags:
`-f` (Vollbild), `-adafruitDisplay`, `-dry` (PowerManager ohne Funktion),
`-xsDisplay` (TOUCH_SMALL 320×240), `-mdDisplay` (TOUCH_MEDIUM 800×480).
Ohne Flag wird die Displaygröße automatisch anhand der Bildschirmbreite erkannt.

Zentrale Komponenten:

- **`ElwaManager`** (Singleton) – zentraler Manager, hält Verweise auf Configuration,
  DataManager/Retriever, ExecutionManager, MainFormController, Location; koordiniert Start/
  Stop.
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
- **`configuration/`**: `WashguardConfiguration`, `LocationManager` – liest
  `elwasys.properties`.
- **`application/MaintenanceServerManager`** – stellt die serverseitige Maintenance-
  Verbindung bereit (Gegenstelle zum Portal).

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

## Maintenance-Protokoll (Common)

Package `common.maintenance`: Nachrichtenbasiertes Protokoll mit
`MaintenanceMessage`/`MaintenanceRequest`/`MaintenanceResponse` und konkreten Nachrichten:
- Connection: `ConnectionRequest/Response`, `CloseConnectionMessage`,
  `CheckConnectionRequest/Response`
- Status/Log: `GetStatusRequest/Response`, `GetLogRequest/Response`
- Steuerung: `RestartAppRequest`, `ErrorMessage`
- Infrastruktur: `MaintenanceServer` (im Client), `MaintenanceClient` (im Portal),
  `IClientConnection`, `IMaintenanceMessageHandler`
- Datenobjekte (`data/`): `InterfaceStatus`, `BacklightStatus`

Der Client startet einen `MaintenanceServer` (Port aus `maintenance.port`, Default 3591),
das Portal verbindet sich als `MaintenanceClient`. Der Client registriert in der DB-Tabelle
`locations` seine `client_ip`, `client_port`, `client_uid`, `client_last_seen`, damit das
Portal weiß, wie es ihn erreicht.

## Kommunikationswege (Zusammenfassung)

1. **Client ⇄ DB** (PostgreSQL, User `elwaclient1`, eingeschränkte Rechte)
2. **Portal ⇄ DB** (PostgreSQL, User `elwaportal`, volle CRUD-Rechte außer credit_accounting-
   Änderungen)
3. **Portal ⇄ Client** (Maintenance-WebSocket, für Fernwartung)
4. **Client ⇄ deCONZ** (REST + WebSocket-Events, für Schalten & Leistungsmessung)
5. **Client → SMTP** (E-Mail-Benachrichtigung)
6. **Client → Pushover** (Push-Benachrichtigung)
7. **elwaapi**-DB-User (in DB-Schema angelegt) – vermutlich für eine mobile App
   (`app_id`/`access_key`/`auth_key` in `users`, Tabelle `reservations`), App-Code nicht in
   diesem Repo.
