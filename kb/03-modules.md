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

## Backend (`org.kabieror.elwasys.backend`) – seit Phase 2 AP1

Neues Spring-Boot-Modul (Java 21), läuft im Strangler-Muster **parallel** zu Common/
Client-Raspi/Portal auf derselben PostgreSQL-Datenbank (siehe kb/05-migration-plan.md,
Zielarchitektur). Kein Reactor-Bezug zu `common` – das Backend bekommt sein eigenes
Datenmodell (Entities folgen im nächsten Arbeitspaket), keine Wiederverwendung der alten
`Common`-POJOs/`DataManager`.

**Aktueller Stand (AP3, 2026-07-20)**: Actuator-Health, Flyway-Baseline gegen das
Bestandsschema (AP1), JPA-Entities/Repositories und die Kern-Geschäftslogik (Abrechnung,
Berechtigungen, Preisberechnung, Execution-Lebenszyklus) als Services (AP2), sowie Auth
(Argon2id-Hashing + SHA1-Migrationspfad, session-basiertes Login-Fundament) mit Spring
Security (AP3). Noch **keine** fachlichen REST-Endpunkte (folgen in AP4 laut Roadmap) – die
Services/das Auth-Fundament in diesem Stand werden ausschließlich von Tests konsumiert, das
Backend schreibt produktiv noch nichts.

### Datenmodell & Geschäftslogik (AP2)

**Entities** (`backend/.../domain/`, siehe kb/02-data-model.md für die Tabellenherkunft):
`UserGroupEntity`, `UserEntity`, `LocationEntity`, `DeviceEntity`, `ProgramEntity`,
`ExecutionEntity`, `CreditAccountingEntryEntity`, `ConfigEntity` (Vollständigkeit, aktuell
ungenutzt). Die vier n:m-Tabellen (`locations_valid_user_groups`,
`devices_valid_user_groups`, `programs_valid_user_groups`, `device_program_rel`) sind als
`@ManyToMany`+`@JoinTable` modelliert (keine eigenen Entity-Klassen, Standard-JPA-Praxis
für reine Verknüpfungstabellen). Postgres-native Enums (`DISCOUNT_TYPE`, `PROGRAM_TYPE`,
`TIME_UNIT_TYPE`) werden über `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` gebunden. Alle fachlich
genutzten Assoziationen sind bewusst `FetchType.EAGER` (analog zum durchgängig eager
ladenden Alt-`DataManager`; AP2 hat noch keine Web-/Transaktionsgrenze, die `LAZY` sauber
absichern würde) – siehe kb/05-migration-plan.md (Änderungslog, AP2) für die vollständige
Begründung. `spring.jpa.hibernate.ddl-auto=none` ist explizit gesetzt: das Schema kommt
ausschließlich von Flyway.

App-Relikt-Spalten (`app_id`/`access_key`/`auth_key` auf `users`; Tabellen
`reservations`/`foreign_authkeys`) sind bewusst NICHT gemappt (Rahmenbedingung, siehe
kb/05-migration-plan.md – werden in Phase 5 entfernt). Der DB-Trigger
`user_authkey_trigger` befüllt `auth_key` unabhängig davon bei jedem INSERT automatisch.

**Repositories** (`backend/.../repository/`): `UserGroupRepository`, `LocationRepository`,
`DeviceRepository`, `ProgramRepository`, `UserRepository` (inkl. `findByCardId` –
parametrisierte Nachbildung der Alt-Code-Regex-Kartennummernsuche), `ExecutionRepository`,
`CreditAccountingEntryRepository`.

**Services** (`backend/.../service/`), jeweils 1:1-Portierung der entsprechenden
Alt-Code-Logik (siehe Javadoc der Klassen für exakte Quellenverweise):
- `PricingService` – Programmpreisberechnung (FIXED/DYNAMIC, Freiminuten, Gruppenrabatt),
  aus `Common.Program#getPrice`/`#getDynamicPrice`.
- `CreditService` – Guthabenberechnung und -buchung (Einzahlung, Auszahlung,
  Ausführungs-Bezahlung; Buchungen sind unveränderlich), aus `Common.User#loadCredit`/
  `#payExecution`/`#inpayment`/`#payout`.
- `PermissionService` – Berechtigungs-Matrix (Standort/Gerät/Programm × Benutzergruppe,
  gesperrte Benutzer, deaktivierte Geräte), aus den inline UI-Prüfungen in
  `Client-Raspi/.../MainFormController`/`DeviceListEntry` sowie `Common.Device#getPrograms`.
- `ExecutionService` – Execution-Lebenszyklus auf Persistenzebene (Anlegen/Start/Ende/
  Abbruch/Reset, Preis, Ablauf-Erkennung), aus `Common.Execution` sowie den DB-Anteilen von
  `Common.DataManager`/`Client-Raspi/.../ExecutionManager`/`ExecutionFinisher`
  (hardwarenahe Teile – Leistungsmessung, Steckdose schalten, Benachrichtigungen – bleiben
  bewusst im Terminal, siehe Zielarchitektur in kb/05).

**Alt-vs-Neu-Vergleichstests**: `common` ist als **test-scope**-Dependency in
`backend/pom.xml` eingebunden (nur Testklassenpfad, keine Laufzeit-Abhängigkeit).
`backend/.../support/LegacyDataManagerFactory` baut eine echte Alt-Code-`DataManager`
gegen dieselbe Test-Datenbank auf; `PricingServiceParityTest`/`CreditServiceParityTest`
lesen dieselbe committete Datenzeile einmal über den Alt-Code und einmal über den neuen
Service und vergleichen bitgenau (Wert **und** `BigDecimal`-Skala). Details/gefundene
Alt-Code-Eigenheiten siehe kb/05-migration-plan.md (Änderungslog, AP2, „Beobachtungen").

### Auth (AP3)

**Package `backend/.../auth/`**:
- `PasswordVerificationService` – erkennt und verifiziert BEIDE im Bestand vorkommenden
  Passwortformate: Argon2id (neu, Präfix `$argon2id$`, über Spring Securitys
  `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`) und SHA1-Legacy (Alt-Format,
  40-Zeichen-Hex, 1:1 nachgebildet aus `Common.Utilities#sha1`, konstanter Byte-Vergleich
  über `MessageDigest.isEqual` statt `String#equals`). `encodeNew(...)` erzeugt IMMER
  Argon2id – jedes Neusetzen eines Passworts über das Backend soll das neue Format
  bekommen, unabhängig vom Re-Hash-Flag (das steuert nur die Migration bestehender
  SHA1-Hashes beim Login).
- `AuthProperties` – `elwasys.auth.rehash-on-login` (Default **`false`**): siehe
  kb/05-migration-plan.md ("Entscheidungen") für die vollständige Begründung, warum der
  Re-Hash-Migrationspfad hinter diesem Flag steckt (Parallelbetrieb mit dem SHA1-
  verifizierenden/-schreibenden Alt-Portal).
- `ElwasysAuthenticationProvider` – fachlicher Nachfolger von
  `Portal/.../SessionManager#login` + `common.User#checkPassword`: lädt den Benutzer per
  case-insensitivem Username-Vergleich (`UserRepository#findByUsernameIgnoreCaseAndDeletedFalse`,
  schließt gelöschte Nutzer 1:1 wie der Alt-Code aus), prüft das Passwort über
  `PasswordVerificationService`, migriert bei SHA1-Treffer + aktiviertem Flag transaktional
  auf Argon2id, aktualisiert `last_login` (1:1 wie `common.User#updateLastLogin`) und
  vergibt `ROLE_ADMIN`/`ROLE_USER` aus `users.is_admin`. **Bewusste Abweichung vom
  Alt-Code**: weist gesperrte Nutzer (`blocked=true`) aktiv ab – der Alt-Portal-Login prüft
  das NICHT (nur der Terminal-Kartenlogin tut das), siehe Klassen-Javadoc und
  kb/05-migration-plan.md ("Entscheidungen") für die Herleitung.
- `ElwasysUserPrincipal` – `UserDetails`-Implementierung ohne echten Passwort-Hash in der
  Session (`getPassword()` liefert immer `""`); trägt Benutzer-ID/-Name/Admin-Flag für
  spätere Arbeitspakete.
- `SecurityConfig` – eine `SecurityFilterChain`: `/actuator/health` `permitAll()`, alles
  andere `authenticated()` mit Formular-Login. Bewusst so gehalten, dass AP4 für die
  Terminal-Standort-Token-Auth eine EIGENE, zustandslose `SecurityFilterChain` (eigener
  `securityMatcher`, z. B. `/api/v1/**`, niedrigere `@Order`-Zahl) danebenstellen kann, ohne
  diese Klasse zu ändern.

**Befund – Bestandsspalte `users.password` zu klein für Argon2id**: `VARCHAR(50)` reichte
für die bisherigen 40-Zeichen-SHA1-Hex-Hashes, aber Argon2id-Strings mit den oben genannten
Parametern sind empirisch gemessen konstant 97 Zeichen lang. Additive Flyway-Migration
`V2__widen_users_password_column.sql` (`ALTER TABLE users ALTER COLUMN password TYPE
VARCHAR(255)`) behoben – abwärtskompatibel, der Alt-Code prüft die Spaltenlänge nicht
selbst. Siehe kb/05-migration-plan.md ("Entscheidungen") für die vollständige Abwägung und
`backend/verify-schema-baseline.sh` für die dadurch entstehende (erwartete) Anmerkung zur
Schema-Divergenz gegenüber dem reinen Alt-Weg.

**Abhängigkeiten** (Spring Boot **3.5.16**, per BOM-Import in `dependencyManagement`
eingebunden – nicht über `spring-boot-starter-parent`, da das Modul bereits `elwasys-parent`
erbt und ein zweiter Parent in Maven nicht möglich ist):
- `spring-boot-starter-web`, `-actuator`, `-jdbc`, `-validation`, `-data-jpa` (AP2),
  `-security` (AP3)
- `org.bouncycastle:bcprov-jdk18on` (AP3, fest gepinnte Version) – von Spring Securitys
  `Argon2PasswordEncoder` als Kryptografie-Provider benötigt; bewusst NICHT vom
  Spring-Boot-BOM verwaltet (offiziell als „optional, selbst hinzufügen" dokumentiert)
- `flyway-core` + `flyway-database-postgresql`, PostgreSQL-Treiber
- Tests: `spring-boot-starter-test`, `spring-security-test` (AP3), `testcontainers`
  (`junit-jupiter`, `postgresql`), `common` (**test-scope**, AP2: Alt-vs-Neu-
  Vergleichstests, keine Laufzeit-Abhängigkeit)
- **Wichtig**: `elwasys-parent`s eigene `dependencyManagement` pinnt `logback-classic`/
  `-core` (1.2.9) und `slf4j-api` (1.7.12) für Common/Client-Raspi fest – zu alt für Spring
  Boot 3.5 (braucht Logback ≥ 1.5). Ein BOM-Import überschreibt nie eine bereits explizit
  gepinnte Version, daher hat `backend/pom.xml` eigene, dem BOM entsprechende Overrides
  (Logback 1.5.34, slf4j-api 2.0.18, Postgres-Treiber 42.7.11) – Details in
  kb/05-migration-plan.md (Änderungslog).

**Struktur**:
- `src/main/java/.../backend/BackendApplication.java` – Einstiegspunkt (`@SpringBootApplication`)
- `src/main/resources/application.yml` – Konfiguration (DB-URL/User/Passwort per
  `ELWASYS_DB_URL`/`ELWASYS_DB_USER`/`ELWASYS_DB_PASSWORD` überschreibbar), Flyway-
  (`baselineOnMigrate`), Actuator- und Logging-Einstellungen
- `src/main/resources/db/migration/V1__baseline_schema_0_4_0.sql` – Flyway-Baseline, siehe
  kb/02-data-model.md
- `src/main/resources/db/migration/V2__widen_users_password_column.sql` – `users.password`
  `VARCHAR(50)` → `VARCHAR(255)` (AP3, Argon2id-Hashes brauchen ~97 Zeichen, siehe oben)
- `src/main/java/.../backend/domain/` – JPA-Entities (AP2, siehe oben)
- `src/main/java/.../backend/repository/` – Spring-Data-Repositories (AP2)
- `src/main/java/.../backend/service/` – Geschäftslogik-Services (AP2)
- `src/main/java/.../backend/exception/` – `NotEnoughCreditException` (AP2)
- `src/main/java/.../backend/auth/` – Auth (AP3, siehe oben):
  `PasswordVerificationService`, `AuthProperties`, `ElwasysAuthenticationProvider`,
  `ElwasysUserPrincipal`, `SecurityConfig`
- `src/test/java/.../backend/BackendApplicationTest.java` – Integrationstest (Spring-Kontext +
  Flyway-Migration gegen echtes PostgreSQL, prüft Health-Endpoint + Baseline-Schema/Seeds)
- `src/test/java/.../backend/support/TestPostgres.java` – DB-Bereitstellung für Tests:
  Testcontainers (Default) oder lokaler Override via `ELWASYS_TEST_JDBC_URL`
- `src/test/java/.../backend/support/AbstractBackendIT.java`,
  `LegacyDataManagerFactory.java`, `Fixtures.java` – Test-Support (AP2): Basisklasse für
  DB-Integrationstests, Alt-Code-`DataManager`-Fabrik für Vergleichstests, eindeutige
  Testdatennamen
- `src/test/java/.../backend/service/`, `.../repository/` – Service-/Repository-Tests
  (AP2, siehe oben)
- `src/test/java/.../backend/auth/` – Auth-Tests (AP3): `PasswordVerificationServiceTest`
  (reine Unit-Tests), `PasswordVerificationServiceParityTest` (Alt-vs-Neu über die echte
  `Utilities.sha1`-Routine), `ElwasysAuthenticationProviderTest` (DB-Integrationstests,
  Re-Hash-Flag AUS), `ElwasysAuthenticationProviderRehashEnabledTest` (Flag per
  `@TestPropertySource` gezielt AN), `SecurityConfigTest` (MockMvc-End-to-End über die
  echte HTTP-/Servlet-Schicht)

**Build/Test/Run**: siehe kb/04-build-and-run.md (Abschnitt „Backend bauen, testen, lokal
starten“). `backend/run-backend-tests.sh` für den Docker-losen lokalen Testweg,
`backend/verify-schema-baseline.sh` für den dokumentierten Schema-Äquivalenz-/
`baselineOnMigrate`-Nachweis.
