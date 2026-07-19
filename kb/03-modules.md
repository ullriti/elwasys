# 03 – Module im Detail

## Common (`org.kabieror.elwasys.common`)

Gemeinsame Bibliothek (JAR), Dependency von Client & Portal. Java 8.

**Datenmodell-Klassen** (Domain + DB-Mapping):
`User`, `UserGroup`, `Device`, `Location`, `Program`, `ProgramType`, `Execution`,
`CreditAccountingEntry`, `DiscountType`.

**Infrastruktur**:
- `ConfigurationManager` – Basis-Konfigurationsverwaltung
- `DataManager` – zentraler DB-Zugriff (Connection, Laden/Speichern der Entitäten)
- `FormatUtilities`, `Utilities` (enthält `APP_VERSION`)
- Exceptions: `NotEnoughCreditException`, `LocationOccupiedException`, `NoDataFoundException`

**maintenance/** – Fernwartungsprotokoll (siehe 01-architecture.md):
Nachrichtenklassen, `MaintenanceServer`/`MaintenanceClient`, Handler-Interfaces,
`data/InterfaceStatus`, `data/BacklightStatus`.

**resources/**: `database-init.sql`, `database-upgrade/*.sql`, ISO-Warn-SVGs.

## Client-Raspi (`org.kabieror.elwasys.raspiclient`)

JavaFX-Terminal, fat-jar. Java 16. 89 Java-Dateien.

**Pakete** (siehe 01-architecture.md für Details):
- `application/` – `Main`, `ElwaManager` (Singleton), `MaintenanceServerManager`,
  `SingleInstanceManager`, `ActionContainer`, `ApplicationInterfaceType`, Close-Listener
- `ui/` – State-Machine + zwei UI-Größen:
  - `ui/small/` – 320×240 (`MainFormController`, `MainFormStateManager`, `ProgramListItem`)
  - `ui/medium/` – 800×480 (Haupt-UI): `MainFormController`, `MainFormStateManager`,
    `controller/` (Startup, DeviceView, ProgramListEntry, Confirmation, Abort, Error,
    Toolbar, UserSettings, Wait, Copyright), `state/` (ErrorState, ToolbarState, Listener)
  - `ui/scheduler/` – `InactivityScheduler`, `InactivityJob/Future`, `BacklightManager`
  - `ui/` gemeinsam: `MainFormState`, `Icons`, `UiUtilities`, `AbstractMainFormController`
- `executions/` – `ExecutionManager`, `ExecutionFinisher`, Listener, `FhemException`
- `devices/` – `deconz/` (Service, ApiAdapter, EventListener, PowerManager,
  RegistrationService, `model/`), `FhemDevicePowerManager`, Interfaces, `DevicePowerState`
- `io/` – `CardReader`, `TelnetClient`, Card-Events
- `configuration/` – `WashguardConfiguration`, `LocationManager`
- `util/` – `BlockingMap`

**FXML** (unter `ui/small/` und `ui/medium/components/`): MainForm + DevicePane, WaitPane,
ConfirmationPane, ToolbarPane, ErrorPane, AbortPane, UserSettingsPane, StartupPane,
CopyrightBar, DeviceListEntry, ProgramListEntry.

**Tests** (`src/test/...`): `AutoEndTest`, `DevicePowerStatisticsAnalyzer`,
`fhemsimulator/` (FhemSimulator, SimulatedDevice, SwitchDevice, PowerMeasurementDevice,
DeviceState), `MaintenanceConnectionTest`, `InactivitySchedulerTest` (liegt im main-Baum).
→ gemischt JUnit/TestNG, kein CI-Lauf.

**Konfiguration**: `elwasys.properties` (Beispiel: `elwasys.example.properties`).
Wichtige Keys: `database.*`, `location`, `displayTimeout`, `startupDelay`,
`sessionTimeout`, `portalUrl`, `deconz.*` **oder** `fhem.*`, `smtp.*`, `maintenance.port`.

## Portal (`org.kabieror.elwasys.webportal`)

Vaadin-7-Webanwendung (WAR). Java 8. 36 Java-Dateien.

**Struktur** (siehe 01-architecture.md):
- Einstieg `WaschportalUI`, Layouts (Public/User/Administrator)
- `views/` – Dashboards & Listenansichten
- `components/` – modale CRUD-Fenster & Hilfskomponenten
- Manager: `WashportalManager`, `SessionManager`, `MaintenanceConnectionManager`
- `events/` – Update-Listener-Interfaces
- `WashportalConfiguration`, `WashportalUtilities`

**Ressourcen**: `MyAppWidgetset.gwt.xml`, `defaultconfig.properties`,
`webapp/VAADIN/themes/waschportal/` (SCSS-Theme, Bilder, favicon).

**Konfiguration**: `/etc/elwaportal/elwaportal.properties` (Beispiel:
`elwaportal.example.properties`). Keys: `database.*`, `smtp.*`, `maintenance.timeout`.

**Build/Run**: `mvn install` (Common zuerst) → `mvn jetty:run` (Port 8080).
Achtung: Vaadin-/GWT-Widgetset-Compilation ist langsam und ressourcenintensiv.
