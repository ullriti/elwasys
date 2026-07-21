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

## Portal (`org.kabieror.elwasys.webportal`) – ⚠️ STILLGELEGT (Phase 3 AP6, 2026-07-21)

> **Fachlich abgelöst** durch das neue, ins Backend eingebettete Portal
> (`backend/.../ui/`, Vaadin Flow – siehe „Portal-UI (Vaadin Flow)" weiter unten).
> Feature-Parität ist über die neue Playwright-Suite (`backend/e2e/`, P1–P20, siehe
> kb/06-ui-tests.md) nachgewiesen; die alte Playwright-Suite (`Portal/e2e/`) läuft nicht mehr
> in der CI. **Der Code dieses Moduls bleibt laut Roadmap bis Phase 5 im Repo** (siehe
> kb/05-migration-plan.md) – nicht gelöscht, nur nicht mehr Teil des E2E-Abnahmepfads. Die CI
> baut das Modul weiterhin (Job `portal-legacy-build` in `.github/workflows/ci.yml`), damit
> Regressionen am liegengebliebenen Code trotzdem auffallen, solange er existiert. Die
> Fernwartungsverbindung (`MaintenanceConnectionManager`, Testfälle P21/P22) bleibt bis Phase 4
> in Betrieb (siehe kb/05-migration-plan.md, „Entscheidungen") – die Cross-Component-E2E-Suite
> dafür läuft unverändert weiter (Teil des „client"-CI-Jobs, hängt nur an `Common`, nicht an
> diesem Modul).

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

**Aktueller Stand (Phase 3 AP1, 2026-07-20)**: Actuator-Health, Flyway-Baseline gegen das
Bestandsschema (AP1), JPA-Entities/Repositories und die Kern-Geschäftslogik (Abrechnung,
Berechtigungen, Preisberechnung, Execution-Lebenszyklus) als Services (AP2), Auth
(Argon2id-Hashing + SHA1-Migrationspfad, session-basiertes Login-Fundament) mit Spring
Security (AP3), eine fachliche REST-API v1 (`/api/v1/**`, Standort-Token-Auth) + ein
WebSocket-Endpunkt-Fundament für Terminals (AP4, siehe Abschnitt „REST-API v1 +
Standort-Token-Auth + WebSocket“ unten), ein Benachrichtigungsdienst hinter einem
Konfig-Flag (AP5), Deployment-Artefakte (AP6) sowie – seit Phase 3 AP1 – ein
**Vaadin-Flow-Admin-Portal-Grundgerüst** (Login, rollenbasierte Layouts/Navigation,
Platzhalter-Views; siehe Abschnitt „Portal-UI (Vaadin Flow)“ unten). Die REST-API/WebSocket-
Schicht wird weiterhin von niemandem produktiv konsumiert (Terminals sprechen bis Phase 4
laut Roadmap weiter direkt SQL); das Portal-Grundgerüst hat noch keine Stammdaten-Inhalte
(folgen in AP2/AP3 dieser Phase).

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
- `src/main/resources/db/migration/V3__create_terminal_tokens.sql` – neue Tabelle
  `terminal_tokens` (AP4, siehe kb/02-data-model.md)
- `src/main/resources/application-token-cli.yml` – Profil `token-cli` für
  `TerminalTokenCliRunner` (AP4, `spring.main.web-application-type: none`)
- `src/main/java/.../backend/domain/` – JPA-Entities (AP2, siehe oben) + `TerminalTokenEntity`
  (AP4)
- `src/main/java/.../backend/repository/` – Spring-Data-Repositories (AP2) +
  `TerminalTokenRepository` (AP4)
- `src/main/java/.../backend/service/` – Geschäftslogik-Services (AP2)
- `src/main/java/.../backend/exception/` – `NotEnoughCreditException` (AP2)
- `src/main/java/.../backend/auth/` – Auth (AP3, siehe oben):
  `PasswordVerificationService`, `AuthProperties`, `ElwasysAuthenticationProvider`,
  `ElwasysUserPrincipal`, `SecurityConfig`
- `src/main/java/.../backend/auth/terminal/` – Standort-Token-Auth (AP4, siehe unten):
  `TerminalTokenService`, `IssuedTerminalToken`, `TerminalPrincipal`,
  `TerminalAuthenticationToken`, `TerminalTokenAuthenticationFilter`,
  `TerminalApiSecurityConfig`, `TerminalTokenCliRunner`
- `src/main/java/.../backend/api/` – REST-API v1 (AP4, siehe unten): Controller
  (`CardLoginController`, `LocationController`, `DeviceController`, `ExecutionController`,
  `UserController`), `api/dto/` (DTOs), `api/exception/` (`ApiException`-Hierarchie),
  `ApiExceptionHandler`, `TerminalScopeGuard`, `OpenApiConfig`
- `src/main/java/.../backend/ws/` – WebSocket-Endpunkt (AP4, siehe unten):
  `TerminalWsMessage`/`TerminalWsMessageType` (Protokoll), `TerminalWebSocketHandler`,
  `TerminalHandshakeInterceptor`, `TerminalConnectionRegistry`, `TerminalHeartbeatScheduler`,
  `TerminalWebSocketConfig`
- `src/main/java/.../backend/ui/` – Vaadin-Flow-Admin-Portal (Phase 3 AP1, siehe Abschnitt
  „Portal-UI (Vaadin Flow)“ unten): `RootView`, `login/LoginView`, `admin/AdminLayout` +
  6 Admin-Views, `user/UserLayout` + `UserDashboardView`, `component/UserMenuBar`,
  `component/PlaceholderView`
- `src/main/java/.../backend/events/` – Domain-Events (Phase 3 AP5, siehe unten):
  `DomainEvent` (sealed Marker-Interface) + 7 Records
- `src/main/java/.../backend/ui/push/` – `UiBroadcaster` (Phase 3 AP5, siehe unten)
- `src/main/frontend/` – von Vaadin auto-generierte Frontend-Tooling-Ausgabe (`generated/`
  ist per `.gitignore` ausgeschlossen, nie hand-editiert; `index.html` ist das
  Standard-Bootstrap-Template, wird committet)
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
  echte HTTP-/Servlet-Schicht), `terminal/TerminalTokenServiceTest` (AP4)
- `src/test/java/.../backend/api/` – REST-API-Tests (AP4, siehe unten):
  `TerminalApiSecurityTest`, `CardLoginControllerTest`, `DeviceControllerTest`,
  `ExecutionControllerTest`
- `src/test/java/.../backend/support/AbstractApiIT.java` – Test-Support (AP4): Basisklasse
  für REST-API-Integrationstests (MockMvc, beide Sicherheitsketten) + Fixture-Helfer
  (Standort/Token/Benutzer/Gerät/Programm anlegen)
- `src/test/java/.../backend/ws/TerminalWebSocketTest.java` – WebSocket-End-to-End-Test (AP4,
  siehe unten)
- `src/test/java/.../backend/ui/` – Portal-UI-Tests (Phase 3 AP1, siehe unten):
  `VaadinPortalSecurityTest`, `RouteAccessAnnotationsTest`

### REST-API v1 + Standort-Token-Auth + WebSocket (AP4, 2026-07-20)

Erste fachliche HTTP-/WS-Schicht des Backends (siehe kb/05-migration-plan.md, Roadmap-Punkte
5+6). Terminals sprechen bis Phase 4 weiter direkt SQL (Alt-Code unverändert) – diese Schicht
wird in Phase 2 von niemandem produktiv konsumiert, sondern ist das Fundament, gegen das die
Terminal-Modernisierung (Phase 4) später implementiert.

#### Standort-Token-Auth

Package `backend/.../auth/terminal/`:
- `TerminalTokenEntity`/`TerminalTokenRepository` – siehe kb/02-data-model.md
  (`terminal_tokens`).
- `TerminalTokenService` – erzeugt Tokens (`createToken`: 32 Byte {@code SecureRandom},
  Base64-URL-kodiert, Präfix `elwt_`), prüft sie (`authenticate`: SHA-256-Hash-Lookup,
  aktualisiert `last_used_at`), widerruft sie (`revoke`). Nur der Hash wird gespeichert, das
  Klartext-Token existiert nur im Rückgabewert von `createToken` (`IssuedTerminalToken`).
- `TerminalPrincipal` (record: `tokenId`, `locationId`, `locationName`) +
  `TerminalAuthenticationToken` – der Standort-Kontext im `SecurityContext` eines
  authentifizierten Terminal-Requests.
- `TerminalTokenAuthenticationFilter` – liest `Authorization: Bearer <token>`, prüft über
  `TerminalTokenService`, setzt bei Erfolg die `TerminalAuthenticationToken` in den
  `SecurityContext`; bei fehlendem/unbekanntem/widerrufenem Token antwortet der Filter SELBST
  mit `401` + `ProblemDetail` (kein Weiterreichen an einen Entry-Point).
- `TerminalApiSecurityConfig` – eigene, zustandslose `SecurityFilterChain`
  (`securityMatcher(new AntPathRequestMatcher("/api/v1/**"))`, `@Order(1)`, `STATELESS`,
  CSRF aus), wie in `SecurityConfig` (AP3) vorgesehen, OHNE diese Klasse zu ändern. Zwei
  Fallstricke dabei gefunden (siehe Klassen-Javadoc für Details): (1) jede
  `Filter`-Spring-Bean wird von Spring Boot zusätzlich automatisch als globaler Servlet-Filter
  für ALLE Pfade registriert – eine `FilterRegistrationBean` mit `setEnabled(false)`
  unterdrückt das, sonst hätte der Token-Filter auch `/actuator/health` blockiert; (2)
  `securityMatcher(String...)` löst über Spring MVC auf und braucht dafür die Bean
  `mvcHandlerMappingIntrospector`, die in den `webEnvironment=NONE`-Tests dieses Moduls nicht
  existiert – der explizite `AntPathRequestMatcher` umgeht das.
- **Entscheidung – Header**: `Authorization: Bearer <token>` (Standard-HTTP-Mechanismus,
  funktioniert unverändert für den WebSocket-Handshake, kein proprietärer Header nötig) statt
  eines eigenen `X-Elwasys-Terminal-Token`-Headers.
- **Entscheidung – Speicherung/Rotation**: nur der SHA-256-Hash landet in der DB (nie das
  Klartext-Token); pro Standort sind beliebig viele aktive Tokens gleichzeitig gültig, das
  ermöglicht Rotation ohne Ausfallfenster (neues Token anlegen, Terminal umstellen, altes
  Token widerrufen).
- **Verwaltungspfad (Phase 2, kein Admin-UI)**: `TerminalTokenCliRunner`
  (`@Profile("token-cli")`, siehe `application-token-cli.yml`,
  `spring.main.web-application-type: none`) – Kommandos siehe kb/04-build-and-run.md. Das
  Klartext-Token wird genau einmal auf `stdout` ausgegeben.

#### REST-API v1 (`/api/v1/**`, Package `backend/.../api/`)

DTOs (`api/dto/`, Java Records) statt Entity-Serialisierung an der API-Grenze (die AP2-EAGER-
Assoziationen würden sonst ungefiltert/rekursiv serialisiert). Fehlerbilder einheitlich als
RFC-7807-`ProblemDetail` über `ApiExceptionHandler` (`@RestControllerAdvice`, nur für
`org.kabieror.elwasys.backend.api`), gespeist aus der `ApiException`-Hierarchie
(`api/exception/`, je Unterklasse HTTP-Status + `type`-URI `urn:elwasys:<slug>` + Titel).
Standort-Scope (Geräte/Executions nur am eigenen Standort, vgl. Client-E2E-Fall C16) über
`TerminalScopeGuard` durchgesetzt – ein Gerät/eine Ausführung eines ANDEREN Standorts wird wie
ein unbekanntes behandelt (`404`, nicht `403`, um keine Existenz an fremden Standorten zu
verraten). API-Dokumentation: springdoc-openapi (`/v3/api-docs`, `/swagger-ui.html`, hinter
der AP3-Catch-all-Kette, also login-pflichtig).

| Methode | Pfad | Zweck | Erfolg | Fehler |
|---|---|---|---|---|
| `POST` | `/api/v1/card-login` | Kartenlogin (`CardLoginRequest{cardId}` → `UserDto` inkl. Guthaben), 1:1 `MainFormController#onCardDetected` | `200` | `404 card-not-found`, `403 user-blocked`, `403 location-not-allowed` |
| `GET` | `/api/v1/locations/me` | Standort des Tokens (`LocationDto`) | `200` | – |
| `GET` | `/api/v1/devices?userId=` | Geräteliste des Standorts, je Gerät `usableByUser`/`occupied`/gefilterte `programs` (`PermissionService`) | `200` | `400` (userId fehlt), `404 user-not-found` |
| `GET` | `/api/v1/devices/{id}?userId=` | Einzelgerät (Standort-Scope) | `200` | `404 device-not-found`, `404 user-not-found` |
| `POST` | `/api/v1/executions` | Execution anlegen+starten (`ExecutionStartRequest{userId,deviceId,programId}`) | `201 ExecutionDto` | `404 device/program/user-not-found`, `403 user-blocked/location-not-allowed/device-not-usable/program-not-available`, `409 device-occupied`, `402 insufficient-credit` |
| `GET` | `/api/v1/executions/{id}` | Aktueller Stand einer Ausführung | `200` | `404 execution-not-found` |
| `POST` | `/api/v1/executions/{id}/finish` | Reguläres Ende (bezahlt) | `200` | `404`, `409 execution-already-finished` |
| `POST` | `/api/v1/executions/{id}/abort` | Vorzeitiger Abbruch (persistenzseitig identisch zu `finish`, eigener Endpunkt für API-Klarheit) | `200` | wie `finish` |
| `POST` | `/api/v1/executions/{id}/reset` | Zurücksetzen ohne Abrechnung (Alt-Code: Steckdose ließ sich nach Anlegen nicht einschalten) | `200` | `404` |
| `GET` | `/api/v1/users/{id}/credit` | Guthabenabfrage (NICHT standortgebunden – Guthaben ist personenbezogen, siehe `UserController`-Javadoc) | `200 CreditResponse` | `404 user-not-found` |

Alle Endpunkte (außer der reinen Standort-Selbstauskunft) prüfen Berechtigungen 1:1 über die
AP2-Services (`PermissionService`, `PricingService`, `CreditService`, `ExecutionService`) –
keine Duplikation der Fachlogik in der API-Schicht.

#### WebSocket-Endpunkt (`/api/v1/terminal-ws`, Package `backend/.../ws/`)

Liegt bewusst unter `/api/v1/**`, damit dieselbe `TerminalApiSecurityConfig`-Kette auch den
Handshake absichert – der Handshake ist zunächst eine normale HTTP-Anfrage und durchläuft
daher den `TerminalTokenAuthenticationFilter` wie jeder REST-Aufruf.
`TerminalHandshakeInterceptor` übernimmt danach den bereits authentifizierten
`TerminalPrincipal` aus dem `SecurityContext` in die `WebSocketSession`-Attribute
(`elwasys.terminal.locationId`/`elwasys.terminal.locationName`).

**Nachrichtenformat** (JSON, `TerminalWsMessage`, versioniert über das Feld `v`):
```json
{ "v": 1, "type": "HELLO", "id": "<Korrelations-Id>", "payload": { "clientVersion": "0.4.0" } }
```
`id` wird vom Absender vergeben; eine Antwort trägt dieselbe `id` wie die auslösende Anfrage
(`TerminalWsMessage.inReplyTo`), damit der Absender Anfrage/Antwort zuordnen kann. Unbekannte
zusätzliche Felder werden ignoriert (Vorwärtskompatibilität).

**Nachrichtentypen** (`TerminalWsMessageType`):

| Typ | Richtung | Verhalten |
|---|---|---|
| `HELLO` | Terminal → Backend | Server antwortet `HELLO_ACK` |
| `HELLO_ACK` | Backend → Terminal | Payload `locationId`, `locationName`, `serverTime`, `protocolVersion` |
| `PING` | beide Richtungen | Empfänger antwortet `PONG` (server-seitig; ein vom Backend gesendetes `PING`, siehe Heartbeat unten, erwartet ein `PONG` vom Terminal) |
| `PONG` | beide Richtungen | Aktualisiert intern den „zuletzt gesehen“-Zeitstempel der Verbindung |
| `STATUS_REQUEST` | beide Richtungen | Server antwortet `STATUS_RESPONSE` (Gerüst: `locationId`, `locationName`, `connectedSince`, `serverTime` – die volle Fernwartungs-Portierung [laufende Ausführungen, Backlight-/Interface-Status, fachliche Referenz `GetStatusResponse`] bleibt für eine spätere Vertiefung offen, siehe „Offene Punkte“ in kb/05) |
| `STATUS_RESPONSE` | beide Richtungen | Phase 2: nur geloggt, kein Handler (die Portal-UI nutzt für den Verbindungsstatus stattdessen direkt `TerminalConnectionRegistry#isConnected`/`#connectedSince`, siehe AP4 unten – kein Bedarf für einen Roundtrip) |
| `LOG_REQUEST` | **Backend → Terminal** (seit Phase 3 AP4) | Portal-initiierte Anfrage (`TerminalMaintenanceService#requestLog`, Admin-Dashboard „Log anzeigen“), kein Payload (fachliche Referenz `GetLogRequest`) |
| `LOG_RESPONSE` | **Terminal → Backend** (seit Phase 3 AP4) | Antwort auf `LOG_REQUEST`, Payload `{"lines": [...]}` (fachliche Referenz `GetLogResponse`); wird über die Korrelations-`id` an die wartende Anfrage zurückgeroutet |
| `RESTART_REQUEST` | **Backend → Terminal** (seit Phase 3 AP4) | Portal-initiierte Anfrage (`TerminalMaintenanceService#requestRestart`, Admin-Dashboard „Neustart“), kein Payload (fachliche Referenz `RestartAppRequest`) |
| `RESTART_RESPONSE` | **Terminal → Backend, NEU seit Phase 3 AP4** | Bestätigung von `RESTART_REQUEST` (im Alt-Protokoll gab es dafür keine Entsprechung – dort „fire-and-forget“; bewusste UX-Verbesserung, der Admin erfährt zuverlässig, ob der Befehl ankam) |
| `ERROR` | beide Richtungen | Protokoll-/Verarbeitungsfehler, `payload.reason` (`malformed-message`/`not-implemented`) |

**Phase 3 AP4 (Fernwartungs-Vermittlung, siehe kb/05-migration-plan.md)**: `TerminalMaintenanceService`
(Package `ws`) ist die Portal-seitige Vermittlung für `LOG_REQUEST`/`RESTART_REQUEST` – sendet mit
einer selbst vergebenen Korrelations-`id`, merkt sich ein `CompletableFuture` je Anfrage und
erfüllt es, sobald `TerminalWebSocketHandler` eine `LOG_RESPONSE`/`RESTART_RESPONSE` mit
passender `inReplyTo`-Id empfängt (`completeIfPending`). Ein nicht verbundener Standort
(`TerminalConnectionRegistry#isConnected` false) liefert SOFORT `TerminalNotConnectedException`
ohne Nachrichtenversand; eine Anfrage ohne Antwort innerhalb von 10s liefert
`TerminalRequestTimeoutException`. **Realität in Phase 3**: da sich Alt-Clients laut
kb/05-migration-plan.md erst in Phase 4 über diesen Kanal verbinden, zeigt die Admin-Dashboard-
Toolbar in der Praxis fast immer „Nicht verbunden“ – der laut Auftrag geforderte klare Zustand.
Die Vermittlungslogik selbst ist über einen SIMULIERTEN WS-Client in `TerminalWebSocketTest`
(JUnit, kein echter Terminal) bewiesen: erfolgreiches Log/Neustart-Roundtrip, sofortiger Fehler
bei nicht verbundenem Standort, Timeout bei einem verbundenen, aber nicht antwortenden Terminal.
**Bewusst NICHT portiert**: das Alt-TCP-Protokoll (`Common.maintenance.*`,
`MaintenanceConnectionManager`/`-Server`) selbst – das Alt-Portal bleibt dafür bis zum Cutover
in Betrieb (siehe kb/05-migration-plan.md, „Entscheidungen“).

**Verbindungsregistry**: `TerminalConnectionRegistry` (in-memory, `Map<locationId, Session>`)
– ersetzt fachlich die alte `client_ip`/`client_port`-Registrierung in `locations` (siehe
kb/02-data-model.md). Genau eine aktive Session pro Standort: verbindet sich ein Terminal
erneut, wird die alte Session geschlossen und ersetzt.

**Heartbeat**: `TerminalHeartbeatScheduler` (`@Scheduled(fixedRate = 30_000)`) sendet allen
verbundenen Terminals ein `PING` und schließt Verbindungen, deren letztes `PONG` länger als 90
Sekunden zurückliegt. `@EnableScheduling` sitzt auf `TerminalWebSocketConfig`, die bewusst
`@Profile("!token-cli")` trägt: Spring Boots Standard-Scheduler-Thread ist kein Daemon-Thread
und hätte den einmaligen `TerminalTokenCliRunner`-Prozess sonst nie beendet (beim manuellen
Testen der CLI gefunden und behoben).

**Tests**: `TerminalWebSocketTest` nutzt den JDK-eigenen `java.net.http`-WebSocket-Client
(keine zusätzliche Testabhängigkeit – derselbe Client, den der Terminal laut
kb/05-migration-plan.md ohnehin nutzen soll) gegen einen echten, per
`@SpringBootTest(webEnvironment=RANDOM_PORT)` gestarteten Server: Handshake ohne/mit
ungültigem Token wird abgelehnt, HELLO/HELLO_ACK, PING/PONG, STATUS_REQUEST/STATUS_RESPONSE
und ein unimplementierter Typ (→ `ERROR`) sind grün.

**Tests der REST-API/Token-Auth**: `TerminalApiSecurityTest` (401 bei fehlendem/unbekanntem/
widerrufenem Token, 200 bei gültigem, Terminal-Token gewährt KEINEN Zugriff auf die
AP3-Catch-all-Kette), `TerminalTokenServiceTest` (Erzeugung/Prüfung/Rotation/Widerruf,
Hash-statt-Klartext), `CardLoginControllerTest`, `DeviceControllerTest` (inkl. Standort-Scope,
Programm-Filterung, `occupied`/`usableByUser`), `ExecutionControllerTest` (voller
Lebenszyklus + alle Fehlerfälle, orientiert an C9/C16). 44 neue Tests, siehe
kb/05-migration-plan.md (Änderungslog, AP4) für die Gesamtzahl.

**Build-Besonderheit**: `backend/pom.xml` setzt `maven.compiler.parameters=true` (nur für
dieses Modul) – ohne dieses Flag wirft Spring MVC zur Laufzeit eine
`IllegalArgumentException` für jeden `@RequestParam`/`@PathVariable` ohne explizit
angegebenen Namen („parameter name information not available via reflection“); **Vorsicht
bei künftigen `pom.xml`-Änderungen**: der Maven-Compiler-Plugin erkennt eine reine
Properties-/Plugin-Konfigurationsänderung nicht immer als Grund für eine Neukompilierung
(inkrementeller Compiler) – nach einer solchen Änderung ggf. `mvn clean` davor schalten,
sonst bleiben alte, ohne `-parameters` kompilierte Klassen im `target`-Verzeichnis liegen
(in AP4 tatsächlich aufgetreten, siehe kb/05-migration-plan.md).

### Benachrichtigungsdienst (AP5, 2026-07-20, Package `backend/.../notification/`)

1:1-Portierung der Benachrichtigungslogik aus `ExecutionFinisher#executeAction()` im
Client-Alt-Code (`Client-Raspi/.../executions/ExecutionFinisher.java`): dort löst das
Ende einer Programmausführung (regulär oder Abbruch) bis zu drei Benachrichtigungskanäle
aus. Vollständiges Alt-Inventar und Portierungsstand:

| Auslöser (Alt-Code) | Kanal | Empfängerregel (Alt-Code) | Portiert? |
|---|---|---|---|
| `ExecutionFinisher`: Programm regulär beendet | E-Mail (`Utilities#sendEmail`, commons-mail `SimpleEmail`) | `user.getEmailNotification()` (Spalte `email_notification`) | **Ja** – `NotificationService#notifyExecutionFinished` |
| `ExecutionFinisher`: Programm regulär beendet | Pushover (`net.pushover.client`) | `user.getPushoverUserKey()` nicht leer (Spalte `pushover_user_key`) | **Ja** |
| `ExecutionFinisher`: Ausführung abgebrochen | E-Mail | wie oben | **Ja** – `NotificationService#notifyExecutionAborted` |
| `ExecutionFinisher`: Ausführung abgebrochen | Pushover | wie oben | **Ja** |
| `ExecutionFinisher`: beide obigen Fälle | elwaApp-Push (`https://api.ionic.io/push/notifications`) | `user.isPushEnabled()` (Spalte `push_notification`, **nicht** `pushover_user_key`!) und `pushIonicId` gesetzt | **Nein** – mobile App laut Auftraggeber nicht relevant, Reste (`app_id`) fallen in Phase 5 weg; Auftrag ist zudem explizit auf „SMTP + Pushover" begrenzt |
| Portal `PasswordForgotWindow`: „Passwort vergessen" | E-Mail (dieselbe `Utilities#sendEmail`) | Nutzer per E-Mail-Adresse gefunden | **Ja** (Phase 3 AP4) – `NotificationService#sendPasswordResetEmail`, aufgerufen von `PasswordResetService#requestReset`; eigener Schalter `elwasys.password-reset.enabled` (Default **AN**, siehe unten) statt `elwasys.notifications.enabled` |
| Portal `UserWindow`: Admin setzt neues Passwort | E-Mail | Admin-Aktion im Nutzer-Fenster | **Ja** (Phase 3 AP4) – `NotificationService#sendNewPasswordEmail`, aufgerufen von `PasswordResetService#resetPasswordByAdminAndNotify` (Checkbox „Sende dem Benutzer per Email ein neues Passwort" im `UserFormDialog`); derselbe Schalter wie oben |

**Wichtiger Fallstrick** (im Code dokumentiert, siehe `NotificationService`-Javadoc):
`users.push_notification` (Entity-Feld `UserEntity#isPushNotification()`) ist im Alt-Code
das Opt-in für den **nicht** portierten elwaApp/Ionic-Kanal, nicht für Pushover. Das
Pushover-Opt-in ergibt sich ausschließlich daraus, ob `pushover_user_key` gesetzt ist.

**Passwort-Reset-Mails (Phase 3 AP4) hängen an einem EIGENEN Schalter, nicht an
`elwasys.notifications.enabled`**: `elwasys.password-reset.enabled` (Default **`true`**, siehe
`PasswordResetProperties`). Begründung (siehe auch kb/05-migration-plan.md, „Entscheidungen"):
`elwasys.notifications.enabled` schützt vor einem *Doppelversand* – im Parallelbetrieb
verschickt Client-Raspi weiterhin selbst Ausführungs-Benachrichtigungen für JEDES
Programmende, ein zusätzlicher Versand durch das Backend für dasselbe automatische Ereignis
wäre doppelt. Ein Passwort-Reset ist dagegen KEIN automatisches, wiederkehrendes Ereignis,
sondern eine explizite, interaktive Aktion einer einzelnen Portal-Session (Klick auf „Passwort
vergessen?" bzw. Admin setzt ein neues Passwort) – es gibt keinen Alt-Code-Pfad, der auf
dasselbe Backend-Ereignis reagiert und doppelt verschicken würde. Der Schalter existiert
trotzdem als Ops-Bremse (z.B. um das Backend ohne konfigurierten SMTP-Server sauber laufen zu
lassen). Nutzt denselben `spring.mail.*`-Transport wie oben.

**Komponenten**:
- `NotificationsProperties` (`@ConfigurationProperties(prefix = "elwasys.notifications")`):
  `enabled` (Default **`false`**, kritisch – siehe unten), `smtp.sender-address`,
  `pushover.api-token`, `pushover.base-url` (für Tests überschreibbar).
- `NotificationService`: `notifyExecutionFinished`/`notifyExecutionAborted` bauen Betreff/
  Kurztext/Langtext wortgleich zum Alt-Code (deutsch, inkl. eines Alt-Code-Tippfehlers/
  Leerzeichens, das bewusst 1:1 übernommen wurde) und lösen darauf basierend E-Mail/
  Pushover aus. Versandfehler werden geloggt, brechen aber nie den Aufruf ab (wie im
  Alt-Code, dort `catch (EmailException/PushoverException)`, hier bewusst breiter
  `catch (Exception)`, siehe Klassen-Javadoc).
- E-Mail-Transport: `spring-boot-starter-mail`/`JavaMailSender`, Konfiguration über die
  Standard-Properties `spring.mail.*` (Mapping zu den Alt-`ConfigurationManager`-Feldern
  `smtp.server`/`-port`/`-user`/`-password`/`-useSSL` in `application.yml` dokumentiert).
- Pushover-Transport: `PushoverClient` (`java.net.http`, kein Fremd-Client) – Formular-
  Request 1:1 aus dem Bytecode der Alt-Bibliothek `com.github.sps.pushover.net:
  pushover-client:1.0.0` hergeleitet (Felder `token/user/message/title/url/url_title/
  priority`, `url`/`url_title` sind wie im Alt-Aufruf fest verdrahtet, nicht konfigurierbar).

**Scharfschaltung (kritisch, Doppelversand-Risiko)**: `elwasys.notifications.enabled`
(Env `ELWASYS_NOTIFICATIONS_ENABLED`) ist per Default **aus** und wird von **keinem**
produktiven Ablauf aufgerufen – Client-Raspi verschickt im Parallelbetrieb (Phase 2–4)
weiterhin selbst. Verdrahtung mit echten Ereignissen (Terminal meldet „Programm beendet"/
„abgebrochen" über die API) sowie das Abschalten des Alt-Versands kommen in Phase 4,
danach kann das Flag scharfgeschaltet werden. Analog zu `elwasys.auth.rehash-on-login`
(AP3).

**Actuator-Nebenwirkung**: `spring-boot-starter-mail` auf dem Klassenpfad aktiviert
automatisch einen Mail-Health-Indikator, der ohne konfigurierten SMTP-Server den
Health-Endpoint auf `DOWN` zieht – `management.health.mail.enabled: false` in
`application.yml` deaktiviert ihn (kein aussagekräftiges Signal, solange der Dienst
per Default aus ist).

**Tests** (11 neu, Package `backend/.../notification/`):
- `NotificationServiceEmailTest`: echter lokaler Test-SMTP (GreenMail, `greenmail-junit5`
  Testabhängigkeit) – Betreff/Body/Empfänger/Absender byte-genau geprüft (Betreff/Body als
  wörtliches Zitat aus dem Alt-Code kommentiert, da `Utilities#sendEmail` sich ohne echte
  `DataManager`/DB-Anbindung nicht isoliert mit einer echten E-Mail-Adresse aufrufen lässt –
  anders als die SHA1-Parität in AP3). Deckt auch Opt-in aus und Versandfehler ab.
- `NotificationServicePushoverTest`: eingebetteter JDK-`HttpServer` als Mock – prüft
  Methode/Pfad/Content-Type/alle Formularfelder inkl. der fest verdrahteten `url`/
  `url_title`/`priority`-Werte; eigener Regressionstest für den `push_notification`-
  Fallstrick oben.
- `NotificationsPropertiesDefaultTest`: voller Spring-Kontext, beweist `enabled=false` ohne
  gesetzte Umgebungsvariable.

### Portal-UI (Vaadin Flow, Phase 3 AP1–AP6, 2026-07-20/21, Package `backend/.../ui/`)

**AP6 (2026-07-21, letztes Arbeitspaket der Phase-3-Roadmap)**: Feature-Parität mit dem
Alt-Portal ist jetzt durch eine portierte Playwright-Suite (`backend/e2e/`, P1–P20
vollständig inkl. dem zuvor nie umgesetzten P11) nachgewiesen – Details, Selektor-Strategie
und Test-für-Test-Status in kb/06-ui-tests.md. Ein dabei gefundener kleiner, aber echter
1:1-Abweichungs-Bug (`LoginView`: „Passwort vergessen?"-Knopf blieb beim Vaadin-Default
„Forgot password" hängen) wurde behoben. Das Alt-Portal-Modul ist damit als E2E-Ziel
stillgelegt (siehe Abschnitt „Portal" oben), bleibt aber bis Phase 5 im Repo.

Admin-Portal als Vaadin-Flow-UI im Backend (siehe kb/05-migration-plan.md, Zielarchitektur
„Portal ist Teil des Backends“) – AP1 lieferte das Grundgerüst (Login/Layout/Navigation/
Rollen-Guard, siehe unten), AP2 füllte die 5 Stammdaten-Views (Benutzer, Benutzergruppen,
Geräte, Programme, Standorte) mit echten Listen + CRUD-Dialogen (siehe Abschnitt
„Stammdaten-Views (AP2)“), AP3 füllte Admin-Dashboard, Guthaben-Aufladen/-Historie und
UsersDashboard (siehe Abschnitt „Dashboard, Guthaben, UsersDashboard (AP3)“), AP4 ergänzte die
restlichen Dialoge/Funktionen – eigenes Passwort ändern (P16), Passwort per Email
zurücksetzen (P19, Selbstbedienung + Admin-Reset), UserSettings (P17), ExpiredExecutions
sowie Log-Viewer/Fernwartung über den bestehenden WebSocket-Kanal – siehe Abschnitt „Dialoge/
Funktionen (AP4)". **AP5 ergänzt Live-Updates zwischen Sessions** (Domain-Events + Vaadin
Push, ersetzt die `events/`-Listener + Vaadin-Push-Mechanik des Alt-Portals) – siehe Abschnitt
„Live-Updates zwischen Sessions (AP5)“ am Ende dieses Kapitels.

**Abhängigkeiten**: `vaadin-bom`/`vaadin-spring-boot-starter` (Version **24.10.8**) per
BOM-Import, analog zum Spring-Boot-BOM (siehe AP1 oben). Zwei Ausschlüsse aus
`vaadin-spring-boot-starter`:
- `hilla` (TypeScript/React-Gegenstück zu Flow – nicht Auftrag, siehe kb/05
  „Technologie-Entscheidungen“: „Vaadin Flow 24“). Ohne diesen Ausschluss wirft
  `com.vaadin.flow.spring.security.RequestUtil#isAllowedHillaView` beim Auswerten der
  Sicherheitskette eine `IllegalStateException` in jedem Testkontext ohne echten,
  Servlet-Container-gebootstrappten `ServletContainerInitializer` (u. a. `SecurityConfigTest`,
  `AbstractApiIT`-Unterklassen, `webEnvironment=MOCK`) – hätte die komplette Backend-Testsuite
  gebrochen.
- `collaboration-engine` (kommerzielles Vaadin-Pro-Add-on für Echtzeit-Kollaboration, hier
  nirgends genutzt) – sauberer Ausschluss statt funktionslosem Mitschleppen.

**Wichtiger Befund – Vaadin-Lizenzpflicht im Dev-Modus** (siehe kb/05-migration-plan.md,
Risikotabelle, für die vollständige Herleitung und den Klärungsbedarf mit dem Auftraggeber):
unabhängig von den beiden Ausschlüssen oben verlangt Vaadin 24.10.x beim ersten
`VaadinServlet#init()` im Dev-Modus einen Online-Lizenzcheck gegen vaadin.com ("This Vaadin
version requires an extended maintenance subscription" – die 24-Linie gilt inzwischen als
kostenpflichtige „Extended Maintenance“). Diese Sandbox-/Build-Umgebung hat keinen
Netzwerkzugriff auf vaadin.com; `mvn spring-boot:run` (Dev-Modus) scheitert hier daher beim
Servlet-Start, ein erzwungener Produktionsmodus findet mangels gebautem
`-Pproduction`-Bundle kein Frontend. Die automatisierte Testsuite ist davon nicht betroffen
(erzwingt `vaadin.productionMode=true` für alle Tests, siehe unten – kein Test ruft eine
Vaadin-UI-Route über einen echten Servlet-Container auf).

**Update AP2 (2026-07-20, siehe kb/05-migration-plan.md, Änderungslog „Phase 3 AP2“)**: der
Produktionsmodus-Build (`mvn -f backend/pom.xml package -Pproduction`) wurde als
De-Risking-Auftrag geprüft und läuft in dieser Sandbox tatsächlich grün durch (auch nach
`mvn clean`) – Vaadin erkennt, dass für diese rein aus Standard-Flow-Komponenten bestehende
UI kein eigener Frontend-Build nötig ist, und liefert ein vorgefertigtes Produktions-Bundle
aus den `vaadin-core`-Jars aus (kein npm/Netzwerkzugriff im Build-Log). Das damit gebaute Jar
wurde gegen eine frische Postgres-Instanz gestartet: `/actuator/health` → `200 UP`, `/login` →
`200` mit echtem Vaadin-Bootstrap-HTML samt ausgeliefertem JS/CSS-Bundle. Der Server loggt
beim Start dennoch dieselbe Lizenz-Fehlermeldung wie im Dev-Modus, bricht aber – anders als
dort – nicht ab. Für AP6 heißt das: eine Playwright-Suite kann gegen einen
produktionsmodus-gebauten, hier gestarteten Server laufen, ohne dass eine
Extended-Maintenance-Subscription/ein Offline-Lizenzschlüssel beschafft werden muss (die
rechtliche Frage, ob das so betrieben werden darf, bleibt trotzdem offen – siehe kb/05,
„Offene Fragen“).

**Views/Layouts**:
- `login/LoginView` (Route `/login`, `@AnonymousAllowed`) – Vaadins eingebaute `LoginForm`
  mit deutschen Texten 1:1 aus dem Alt-Portal (`Portal/.../PublicLayout`,
  `SessionManager#login`): Titel „Login"/Beschriftung „Waschportal“, Felder
  „Benutzername"/„Passwort", Fehlermeldung „Login fehlgeschlagen"/„Bitte prüfen Sie die
  Anmeldedaten und versuchen Sie es erneut.". Formular postet über Spring Securitys
  Standard-Mechanismus an `/login` (siehe `SecurityConfig` unten) – authentifiziert weiterhin
  über den unveränderten `ElwasysAuthenticationProvider` (AP3), inkl. dessen Verschärfung
  „gesperrte Nutzer werden abgewiesen".
- `RootView` (Route `""`, `@PermitAll`) – fachlicher Nachfolger von
  `WaschportalUI#loadSessionContent`: leitet nach dem Login abhängig von der Rolle sofort
  weiter (`AuthenticationContext#hasRole("ADMIN")`) – Administratoren zu
  `AdminDashboardView`, alle anderen angemeldeten Benutzer zu `UserDashboardView` (vgl. P2/P15).
- `admin/AdminLayout` (`AppLayout` + `SideNav`) – fachlicher Nachfolger von
  `Portal/.../AdministratorLayout` + `components/MainMenu`: Navigation Dashboard/Benutzer/
  Benutzergruppen/Programme/Geräte/**Standorte**. „Standorte" ist NEU als eigener Menüpunkt
  (im Alt-Portal nur über einen Dashboard-Dialog erreichbar, `components/LocationWindow`,
  vgl. P14) – bewusste, vom Auftraggeber ausdrücklich gewünschte UX-Verbesserung (siehe
  kb/05-migration-plan.md, „Entscheidungen“, Gestaltungsrahmen Portal-Neubau), keine
  Funktionsänderung. 6 Views (`AdminDashboardView`, `AdminUsersView`, `AdminUserGroupsView`,
  `AdminProgramsView`, `AdminDevicesView`, `AdminLocationsView`), alle `@RolesAllowed("ADMIN")`
  – seit AP3 haben auch `AdminDashboardView` und die Guthaben-Dialoge in `AdminUsersView`
  echte Inhalte (siehe Abschnitt „Dashboard, Guthaben, UsersDashboard (AP3)“ unten).
- `user/UserLayout` (`AppLayout` + `SideNav`, ein Menüpunkt „Übersicht") – fachlicher
  Nachfolger des schlanken `Portal/.../UserLayout`; laut Auftraggeber loggen sich im
  Wesentlichen nur Admins ein (kb/05-migration-plan.md, „Entscheidungen“), entsprechend
  niedrigere Parity-Priorität. `UserDashboardView` (`@RolesAllowed("USER")`) zeigt seit AP3
  Guthaben/Übersicht (vgl. P15, siehe Abschnitt „Dashboard, Guthaben, UsersDashboard (AP3)“
  unten).
- `component/UserMenuBar` – gemeinsame Kopfzeilenkomponente (Name des angemeldeten Benutzers +
  Logout-Knopf, `AuthenticationContext#logout()`) für Admin-/Benutzer-Layout; fachlicher
  Nachfolger des Benutzermenüs in `Portal/.../components/MainMenu`. „Einstellungen"/„Passwort
  ändern" (dort zusätzlich vorhanden) sind bewusst NICHT Teil dieses Grundgerüsts.
- `component/PlaceholderView` – gemeinsame Basis aller Platzhalter-Views (Titel + Hinweistext).

**Security-Integration** (`backend/.../auth/SecurityConfig`, unverändertes Paket seit AP3):
statt `formLogin(Customizer.withDefaults())` jetzt
`http.with(VaadinSecurityConfigurer.vaadin(), c -> c.loginView(LoginView.class).anyRequest(AuthorizedUrl::authenticated))`.
Das bindet `LoginView` als Login-Ziel, gibt die Login-Route + alle Vaadin-internen statischen
Ressourcen für nicht angemeldete Anfragen frei und aktiviert Vaadins routenweise
`NavigationAccessControl` (wertet `@RolesAllowed`/`@PermitAll`/`@AnonymousAllowed` an den
Views aus – siehe oben). Der `AuthenticationManager` (`ElwasysAuthenticationProvider`) bleibt
unverändert derselben Kette zugeordnet. Terminal-API-Kette (`TerminalApiSecurityConfig`,
`/api/v1/**`) und WebSocket-Endpunkt sind UNBERÜHRT – eigene, niedrigere `@Order`-Kette
(AP4), dedizierter Regressionstest (`TerminalApiSecurityTest`, unverändert grün) beweist das.

**Zwei nicht offensichtliche Fallstricke gefunden und behoben** (Details siehe Javadoc
`SecurityConfig`/`AbstractBackendIT`):
1. Vaadins Spring-Autokonfigurationsklassen (`SpringBootAutoConfiguration`,
   `SpringSecurityAutoConfiguration`, `VaadinScopesConfig`, siehe
   `vaadin-spring`s `META-INF/spring/...AutoConfiguration.imports`) sind NICHT auf
   `@ConditionalOnWebApplication` eingeschränkt und versuchen daher auch in den bestehenden
   `webEnvironment=NONE`-Tests (`AbstractBackendIT`, AP2) einen `WebApplicationContext`/eine
   `ServletRegistrationBean<SpringServlet>` zu autowiren – hätte ALLE Service-/Repository-/
   Auth-Tests aus AP2/AP3 gebrochen. Fix: `AbstractBackendIT` schließt alle drei
   Autokonfigurationsklassen explizit aus (`spring.autoconfigure.exclude`).
2. `VaadinSecurityConfigurer` braucht für den Login-Routen-Pfad zwingend einen echten
   `ServletContext`-Bean (`getServletContextPath()`) – in einem Nicht-Web-Kontext wäre die
   `securityFilterChain`-Bean-Erzeugung mit einer `NullPointerException` gescheitert. Fix:
   `@ConditionalOnWebApplication` NUR auf dieser einen Bean-Methode (nicht auf der ganzen
   `SecurityConfig`-Klasse, sonst hätte `TerminalApiSecurityConfig` seinen von
   `@EnableWebSecurity` bereitgestellten `HttpSecurity`-Bean verloren).

**Build-Konfiguration** (`backend/pom.xml`): Default-`mvn package` bleibt schnell (nur
`vaadin-maven-plugin:prepare-frontend`, kein npm-Bundling); neues Maven-Profil `production`
(`mvn package -Pproduction`) baut zusätzlich das produktive Frontend-Bundle
(`build-frontend`, `vaadin.productionMode=true`). Surefire-Konfiguration erzwingt
`vaadin.productionMode=true` als System-Property für ALLE Testläufe dieses Moduls – verhindert,
dass ein Testfall mit echtem eingebettetem Servlet-Container (z. B. `TerminalWebSocketTest`,
`BackendApplicationTest`, `webEnvironment=RANDOM_PORT`) Vaadins Dev-Modus (inkl. des oben
beschriebenen Lizenzchecks) startet.

**Tests** (9 neu, Package `backend/.../ui/`): bewusst KEIN Test über einen echten
eingebetteten Servlet-Container, der eine Vaadin-UI-Route tatsächlich rendern lässt – siehe
„Wichtiger Befund" oben, ein solcher Test könnte in dieser Sandbox nicht grün laufen.
Stattdessen:
- `VaadinPortalSecurityTest` (5, `webEnvironment=MOCK`, genügt für alles, was rein in der
  Spring-Security-Filterkette entschieden wird): geschützte Routen (`/`, `/admin`) leiten
  nicht angemeldete Anfragen um; Formular-Login über die Vaadin-Login-Route authentifiziert
  gültige Zugangsdaten UND weist gesperrte Nutzer ab (`ElwasysAuthenticationProvider`-
  Integration); Logout leitet zur Login-Seite um.
- `RouteAccessAnnotationsTest` (4, reiner Reflection-Test ohne Spring-Kontext/DB): beweist die
  `@AnonymousAllowed`/`@PermitAll`/`@RolesAllowed`-Zuordnung je View-Klasse (`LoginView`,
  `RootView`, alle 6 Admin-Views, `UserDashboardView`) – das ist genau die Information, die
  Vaadins `NavigationAccessControl` zur Laufzeit auswertet (vgl. Testfall P18).

Ein vollständiger, per Browser/JS getriebener Login-Durchstich (Vaadins `LoginForm` ist eine
clientseitig gerenderte Web-Komponente, kein klassisches Server-HTML-Formular mit
scrapebarem CSRF-Feld) bleibt der späteren Playwright-E2E-Suite vorbehalten (kb/08-test-plan.md, P18).

**Build/Test/Run**: siehe kb/04-build-and-run.md (Abschnitt „Backend bauen, testen, lokal
starten“). `backend/run-backend-tests.sh` für den Docker-losen lokalen Testweg,
`backend/verify-schema-baseline.sh` für den dokumentierten Schema-Äquivalenz-/
`baselineOnMigrate`-Nachweis.

#### Stammdaten-Views (AP2, 2026-07-20)

Die 5 Admin-Platzhalter-Views aus AP1 (`AdminUsersView`, `AdminUserGroupsView`,
`AdminDevicesView`, `AdminProgramsView`, `AdminLocationsView`) haben jetzt echte Inhalte –
Feature-Parität zum Alt-Portal (siehe kb/08-test-plan.md, Testfälle P6/P7/P9–P14). `@Route`/
`@PageTitle`/`@RolesAllowed` sind dabei UNVERÄNDERT geblieben (nur die Basisklasse wechselt
von `PlaceholderView` auf `VerticalLayout` mit echtem Inhalt) – `RouteAccessAnnotationsTest`
brauchte deshalb keine Anpassung.

**Services** (`backend/.../service/`, jeweils mit den entsprechenden Alt-Fenstern als
fachlicher Referenz):
- `UserService` – Anlegen/Bearbeiten/weiches Löschen von Benutzern (fachlicher Nachfolger von
  `Portal/.../components/UserWindow`, ohne dessen Admin-Passwort-Reset-Teil – der ist AP4).
  Löschen entspricht 1:1 `Common.User#setDeleted`: der Benutzername wird zusätzlich mit
  `#del<id>#` präfixiert, damit er (UNIQUE-Constraint auf `users.username`) sofort wieder
  frei ist. Kartennummer-Mehrfachvergabe wirft `DuplicateCardIdException` (neu, Package
  `backend/.../exception/`).
- `UserGroupService` – Anlegen/Bearbeiten/Löschen (fachlicher Nachfolger von
  `Portal/.../components/UserGroupWindow`). Löschen entspricht 1:1
  `Common.UserGroup#delete`: Benutzer der gelöschten Gruppe werden einer anderen Gruppe
  zugewiesen (`UserGroupRepository#findFirstByIdNotOrderByIdAsc` bildet die Alt-SQL nach);
  gibt es keine andere Gruppe, wirft der Service `EntityInUseException` (neu) statt eines
  NOT-NULL-Constraint-Verstoßes wie im Alt-Code. Besonderheit: `UserGroupEntity` selbst
  besitzt KEINE Sammlung ihrer Standorte/Geräte/Programme (die drei `@ManyToMany`-Relationen
  sind unidirektional von `LocationEntity`/`DeviceEntity`/`ProgramEntity` aus modelliert,
  siehe deren Klassenkommentare) – `setValidLocations`/`-Devices`/`-Programs` togglen die
  Gruppenzugehörigkeit deshalb von der jeweils anderen Tabellenseite aus (iterieren über alle
  Standorte/Geräte/Programme).
- `DeviceService` – Anlegen/Bearbeiten/Löschen (fachlicher Nachfolger von
  `Portal/.../components/DeviceWindow`). Löschen bewusst OHNE Wächter, 1:1
  `Common.Device#delete` (Ausführungen behalten ihren Bezug per `ON DELETE SET DEFAULT` auf
  ein virtuelles Gerät, siehe kb/02-data-model.md).
- `ProgramService` – Anlegen/Bearbeiten/Löschen (fachlicher Nachfolger von
  `Portal/.../components/ProgramWindow`). Löschen mit Wächter: ein Programm, das noch
  mindestens einem Gerät zugeordnet ist, wird NICHT gelöscht (1:1
  `Portal/.../views/ProgramsView#deleteProgram` – eine fachliche Schutzregel, obwohl
  `device_program_rel` selbst `ON DELETE CASCADE` trägt und die DB das technisch zuließe).
- `LocationService` – Anlegen/Bearbeiten/Löschen. Standorte sind seit AP1 ein eigener
  Menüpunkt (`AdminLocationsView`, im Alt-Portal nur über einen Dashboard-Dialog erreichbar,
  `Portal/.../components/LocationWindow`) – eine vom Auftraggeber gewünschte
  UX-Verbesserung. Löschen mit Wächter analog zu `ProgramService` (ein Standort mit noch
  zugeordneten Geräten wird nicht gelöscht, entspricht fachlich
  `DataManager#removeUnusedLocations`, jetzt als expliziter Admin-Vorgang statt impliziter
  Hintergrundaktion).

Neue Repository-Methoden: `UserGroupRepository#findFirstByIdNotOrderByIdAsc`,
`DeviceRepository#findAllByOrderByNameAsc`/`#findByPrograms_Id`,
`ProgramRepository#findAllByOrderByNameAsc`.

**UI** (neues Sub-Paket `backend/.../ui/admin/dialog/`): jede der 5 Views ist ein Vaadin
`Grid` (Spalten wie im Alt-Portal, siehe kb/08-test-plan.md) mit Symbolleiste „Neu“ und
Zeilen-Aktionen (Bearbeiten/Löschen), die einen modalen `Dialog` öffnen – fachliche
Nachfolger der Alt-`components/*Window`-Fenster: `UserFormDialog`, `UserGroupFormDialog`,
`DeviceFormDialog`, `ProgramFormDialog`, `LocationFormDialog`. Neue gemeinsame Komponente
`backend/.../ui/component/ConfirmDeleteDialog` (Nachfolger von
`Portal/.../components/ConfirmWindow`) nutzt Vaadins eingebauten `ConfirmDialog` mit
deutschen „Ja“/„Nein“-Beschriftungen. Die Alt-`TwinColSelect`-Mehrfachauswahlen (Standorte/
Geräte/Programme/Benutzergruppen) sind als `MultiSelectComboBox` umgesetzt – funktional
identisch, moderneres Bedienelement (UX-Verbesserung im erlaubten Rahmen, siehe
kb/05-migration-plan.md, „Entscheidungen“).

**Bewusst NICHT Teil dieses Arbeitspakets** (bewusste Abweichungen, keine stillen Lücken):
Admin-Passwort-Reset im Benutzer-Dialog (AP4); „Nicht abgerechnete Programmausführungen“-
Warnicon (`ExpiredExecutionsWindow`, eigener Roadmap-Punkt „Dialoge/Funktionen“, AP4);
`DeviceWindow`s Inline-Standort-Anlage entfällt ersatzlos (durch die eigenständige
Standort-Verwaltung überflüssig). Guthaben-AUFLADEN (`UserCreditWindow`) folgte in AP3, siehe
unten.

**Tests** (19 neu, Package `backend/.../service/`, Muster wie Phase 2 AP2 –
`AbstractBackendIT`/`Fixtures`): `UserServiceTest`, `UserGroupServiceTest` +
`UserGroupServiceDeleteGuardTest` (reiner Mockito-Unit-Test für den in der gemeinsam
genutzten Testdatenbank nicht sauber herstellbaren Randfall „keine andere Gruppe mehr
vorhanden“), `DeviceServiceTest`, `ProgramServiceTest`, `LocationServiceTest`. Backend-Suite
insgesamt **135/135** grün. Bewusst KEIN Test, der über einen echten eingebetteten
Servlet-Container eine Vaadin-Route rendert (unverändertes Risiko aus AP1) – siehe
„Wichtiger Befund“ oben für den De-Risking-Nachweis über den Produktionsmodus-Build.

#### Dashboard, Guthaben, UsersDashboard (AP3, 2026-07-20)

**Admin-Dashboard** (`AdminDashboardView`, fachlicher Nachfolger von
`Portal/.../views/AdminDashboardView`): zeigt je Standort dessen Geräte mit „Frei“/„Besetzt“
(Testfall P20 – direkt aus der laufenden `ExecutionEntity` in der DB abgeleitet, kein
Client-Kontakt), bei einer laufenden Ausführung zusätzlich Programm, Benutzer und Restzeit
(Restzeit ist eine bewusste ZUSÄTZLICHE Information, im Alt-Dashboard nicht vorhanden – reine
Ergänzung, keine Verhaltensänderung), sowie je Gerät die vollständige Ausführungshistorie
(Datum/Benutzer/Dauer/Preis, mit hervorgehobener laufender/abgelaufener Zeile über
`Grid#setPartNameGenerator`) – analog zur Tabelle im Alt-`AdminDashboardLocationPanel`. Die
Wartungsverbindungs-Toolbar des Alt-Dashboards (Log-Datei ansehen, Client neu starten,
Verbindungsstatus/IP) ist bewusst NICHT Teil dieses Arbeitspakets, siehe Roadmap-Punkt
„Fernwartung“ (AP4). Aktualisierung erfolgt beim Seitenaufruf; kein Live-Push (laut Auftrag
nicht nötig, folgt als eigener Roadmap-Punkt „Live-Updates zwischen Sessions“, AP5).

Neuer Service `DashboardService` (`backend/.../service/`) kapselt die gesamte
Datenbeschaffung (Standorte → Geräte → Status, `LocationStatus`/`DeviceStatus`-Records) OHNE
Vaadin-Abhängigkeit – bewusst so geschnitten, damit AP5 (Live-Updates) dieselbe Abfrage
wiederverwenden kann statt einer zweiten Implementierung. Neue Repository-unabhängige Methode
`DeviceService#findByLocation` (Nachfolger von `DataManager#getDevicesToDisplay`).

**Guthaben aufladen/Historie** (Testfall P8, in `AdminUsersView` über zwei neue
Zeilen-Aktionen erreichbar, wie im Alt-`UsersView`):
- `CreditTopUpDialog` – fachlicher Nachfolger von `Portal/.../components/UserCreditWindow`:
  Einzahlung/Auszahlung (Radiobuttons, Default „Einzahlung“), Betrag, Buchungstext (vorbelegt
  mit „Einzahlung/Auszahlung vom Waschportal von &lt;angemeldeter Admin&gt;“, 1:1 wie im
  Alt-Fenster). Ruft AUSSCHLIESSLICH die bestehenden Phase-2-Methoden
  `CreditService#inpayment`/`#payout` auf – erzeugt damit strukturell immer nur einen NEUEN
  Buchungssatz, ändert/löscht nie einen bestehenden. Eine Auszahlung über das verfügbare
  Guthaben hinaus zeigt dieselbe Fehlermeldung wie im Alt-Portal
  („Das Guthaben des Benutzers reicht nicht aus für diese Operation.“,
  `NotEnoughCreditException`).
- `CreditHistoryDialog` – fachlicher Nachfolger von
  `Portal/.../components/CreditAccountingWindow` („Umsätze ansehen“): rein lesende Liste
  (Datum/Betrag/Buchungstext, neueste zuerst) über die neue Methode
  `CreditService#getAccountingEntries` – bietet bewusst KEINE Bearbeitungs-/Löschfunktion.

Zwei neue `CreditService`-Methoden (beide reine Lese-Delegationen an bestehende
Repository-Queries, siehe `CreditAccountingEntryRepository`): `getAccountingEntries` (=
`DataManager#getAccountingEntries`) und `getLastInpayment` (=
`DataManager#getLastInpayment`) – letztere auch vom UsersDashboard genutzt (siehe unten).

**UsersDashboard** (`UserDashboardView`, fachlicher Nachfolger von
`Portal/.../views/UsersDashboardView`, Testfall P15: „Guthaben“/„Übersicht“ sichtbar –
„Übersicht“ ist bereits seit AP1 der Menüpunkt in `UserLayout`, „Guthaben“ die Kachel in
dieser View): zeigt eigenes Guthaben (`CreditService#getCredit`) und letzte Einzahnung
(`CreditService#getLastInpayment`) als Kacheln sowie die vollständige eigene Buchungshistorie
(`CreditService#getAccountingEntries`) in einer Tabelle „Buchungen“ – 1:1 wie im Alt-Portal.
**Datenisolation**: der angezeigte Benutzer kommt ausschließlich aus dem
`ElwasysUserPrincipal` der Session (`AuthenticationContext#getAuthenticatedUser`), nicht aus
einem Pfad-/Query-Parameter – ein Nicht-Administrator kann über diese View strukturell nur
eigene Daten sehen.

**Unveränderlichkeit der Buchungen**: strukturell sichergestellt, nicht nur durch Konvention –
`CreditAccountingEntryEntity` (seit Phase 2 AP2) bietet keine Setter für gespeicherte
Buchungen, nur den Konstruktor; alle drei neuen/wiederverwendeten UI-Bausteine
(`CreditTopUpDialog`, `CreditHistoryDialog`, `UserDashboardView`) rufen ausschließlich
lesende Methoden oder die anfügenden `CreditService#inpayment`/`#payout` auf, nie ein
Update/Delete auf `credit_accounting`.

**Tests** (9 neu, Package `backend/.../service/`): `DashboardServiceTest` (5 – freies/
besetztes/abgelaufenes Gerät, Standort-Gruppierung, Historie), `CreditServiceAccountingHistoryTest`
(3 – Sortierung neueste zuerst, unveränderte Werte bei wiederholtem Abruf,
`getLastInpayment` ignoriert Auszahlungen), plus ein neuer Test in `DeviceServiceTest`
(`findByLocation`). Backend-Suite insgesamt **144/144** grün. Bewusst KEIN Test, der über
einen echten eingebetteten Servlet-Container eine Vaadin-Route rendert (unverändertes Risiko
aus AP1); `RouteAccessAnnotationsTest` brauchte keine Anpassung (keine neuen Routen, nur
Inhalte bestehender Views/neue Dialoge).

#### Dialoge/Funktionen (AP4, 2026-07-21)

Letztes Arbeitspaket der Phase-3-Roadmap „Dialoge/Funktionen“ (vor Live-Updates [AP5] und der
Playwright-Portierung [AP6]) – siehe kb/05-migration-plan.md für die vollständige
Entscheidungs-Historie.

**Eigenes Passwort ändern** (`ChangePasswordDialog`, Testfall P16, fachlicher Nachfolger von
`Portal/.../components/ChangePasswordWindow`): erreichbar über das jetzt aufklappbare
Benutzermenü in `UserMenuBar` (bis AP4 nur ein Logout-Knopf, seit AP4 Menü mit
„Einstellungen“/„Passwort ändern“/„Logout“ – 1:1 wie das Alt-`MainMenu`). Neuer,
Vaadin-freier Service `PasswordService#changeOwnPassword` prüft das aktuelle Passwort über
`PasswordVerificationService` (akzeptiert sowohl Argon2id- als auch SHA1-Bestandshashes) und
setzt das neue Passwort IMMER im Argon2id-Format (`#encodeNew`, 1:1 wie der Rest von AP3).
**Wichtige Konsequenz für den Parallelbetrieb** (in `PasswordService`-Javadoc dokumentiert):
das Alt-Portal (`common.User#checkPassword`) vergleicht weiterhin `SHA1(eingegebenes
Passwort) == gespeicherter String` – ein über das NEUE Portal gesetztes Argon2id-Passwort kann
sich daher NICHT mehr im Alt-Portal anmelden. Das ist eine bewusste, dokumentierte
Einschränkung (kein Verstoß gegen „Nutzer dürfen sich nicht umstellen müssen“, weil sie erst
greift, wenn ein Nutzer AKTIV das neue Portal nutzt) – siehe kb/05-migration-plan.md,
„Entscheidungen“.

**UserSettings** (`UserSettingsDialog`, Testfall P17, fachlicher Nachfolger von
`Portal/.../components/UserSettingsWindow`): Email, Email-Benachrichtigung (Checkbox),
Pushover-Key – 1:1 dieselben drei Felder wie im Alt-Fenster, über den neuen
`UserService#updateOwnSettings` (ändert bewusst NUR diese drei Felder, nicht
Name/Username/Kartennummern/Gruppe/Gesperrt-Status/Admin-Flag – wie im Alt-Fenster).

**Passwort per Email zurücksetzen** (Testfall P19, kb/03 Notification-Tabelle oben):
- Selbstbedienung: `LoginView`s „Passwort vergessen?“-Knopf ist jetzt aktiv (bis AP4 über
  `setForgotPasswordButtonVisible(false)` deaktiviert) und öffnet `PasswordForgotDialog`
  (fachlicher Nachfolger von `PasswordForgotWindow`) – Email-Eingabe, ruft
  `PasswordResetService#requestReset` auf.
- Neue öffentliche Route `ResetPasswordView` (`/reset-password?key=<token>`,
  `@AnonymousAllowed`, `RouteAccessAnnotationsTest` erweitert) zum Setzen des neuen Passworts –
  fachlicher Nachfolger von `ResetPasswordWindow` (dort ein modales Fenster über der bereits
  geladenen Alt-Portal-Seite mit `?rp=<key>`; hier eine eigene Vaadin-Route, weil Flow
  serverseitiges Routing mit eigenen URLs kennt – fachlich gleichwertig).
- Admin-seitig: `UserFormDialog` hat die Checkbox „Sende dem Benutzer per Email ein neues
  Passwort“ zurück (in AP2 bewusst ausgespart, siehe dortiger Abschnitt) – ruft
  `PasswordResetService#resetPasswordByAdminAndNotify` auf.
- Neuer Service `PasswordResetService`: **Schlüssel-Speicherung** nutzt bewusst die
  BESTEHENDEN Bestandsspalten `users.password_reset_key`/`password_reset_timeout` (Teil der
  Flyway-Baseline `V1`, siehe kb/02-data-model.md) statt einer neuen Migration – additiv im
  Sinne der Rahmenbedingung, weil diese Spalten bereits existieren und der Alt-Code sie
  ebenfalls liest/schreibt (kein Konflikt). **Format**: 24 `SecureRandom`-Bytes, Base64-URL
  ohne Padding (32 Zeichen, passt in die bestehende Spaltenbreite) statt des Alt-Formats
  (`SHA1` über `Math.random()`-Bytes) – kryptographisch stärker. **Gültigkeit**: 2 Stunden
  (konfigurierbar, `elwasys.password-reset.token-validity`), 1:1 wie der Alt-Code. **Reset-URL**:
  `<elwasys.password-reset.portal-base-url>/reset-password?key=<token>` – anders als der
  Alt-Code (leitet die URL aus der aktuellen Browser-Anfrage ab,
  `WashportalUtilities#getPasswordResetUrl`) bewusst über eine Konfigurationseigenschaft
  gebaut (robuster hinter einem Reverse Proxy/wenn der Versand nicht im Kontext einer
  laufenden HTTP-Anfrage passiert). **Schalter**: `elwasys.password-reset.enabled` (Default
  **AN**), bewusst NICHT `elwasys.notifications.enabled` – vollständige Begründung siehe
  kb/05-migration-plan.md „Entscheidungen“ und `PasswordResetProperties`-Javadoc (Kurzfassung:
  kein Doppelversand-Risiko, weil der Versand an eine explizite interaktive Aktion gebunden
  ist, nicht an ein wiederkehrendes Ereignis wie ein Ausführungsende).

**ExpiredExecutions** (`ExpiredExecutionsDialog`, fachlicher Nachfolger von
`Portal/.../components/ExpiredExecutionsWindow`): in `AdminUsersView` öffnet ein
Warndreieck-Symbol (nur sichtbar für nicht gesperrte Benutzer mit
`ExecutionService#hasExpiredExecutions`, 1:1-Priorität wie
`Portal/.../views/UsersView#fillItemWithUserData` – Gesperrt-Icon geht vor Warndreieck) den
Dialog mit allen abgelaufenen, nicht abgerechneten Ausführungen (`getExpiredExecutions`, neue
Methode). Je Zeile „Abrechnen“ (`ExecutionService#finishExecution`, entspricht
`User#payExecution` + `Execution#finish`) oder „Löschen“ (neue Methode
`ExecutionService#delete`, entspricht `Execution#delete`), zusätzlich „Alle abrechnen“ als
Sammelaktion – 1:1 wie im Alt-Fenster.

**Log-Viewer + Fernwartung** (Status/Logs/Restart, siehe WS-Protokolltabelle oben für die
Nachrichtentypen/`TerminalMaintenanceService`): `AdminDashboardView` zeigt je Standort jetzt
eine Kopfzeile mit Verbindungsstatus („Verbunden“/„Nicht verbunden“, aus
`TerminalConnectionRegistry` – ersetzt die Alt-„IP-Adresse“, die mit der ausgehenden
Verbindung entfällt, siehe kb/02-data-model.md) sowie den Knöpfen „Log anzeigen“
(`LogViewerDialog`, fachlicher Nachfolger von `LogViewerWindow`) und „Neustart“ – fachlicher
Nachfolger der `AdminDashboardLocationPanel`-Toolbar. Beide Knöpfe rufen
`TerminalMaintenanceService` auf und zeigen für einen NICHT verbundenen Standort denselben
Fehlertext wie der Alt-Code („Keine Verbindung zum Client“/„...zum Standort.“) – der in dieser
Phase (siehe Roadmap: Alt-Clients verbinden sich erst in Phase 4 über diesen Kanal) praktisch
immer erwartbare Fall. **Bewusst NICHT portiert**: das Alt-TCP-Protokoll selbst
(`MaintenanceConnectionManager`/`Common.maintenance.*`) – das Alt-Portal bleibt dafür bis zum
Cutover in Betrieb, siehe kb/05-migration-plan.md, „Entscheidungen“.

**Tests** (17 neu): `PasswordServiceTest` (4, inkl. Migration eines SHA1-Bestandshashes beim
Ändern), `PasswordResetServiceTest` (6, mit echtem SMTP-Mock GreenMail durch den vollen
Spring-Kontext – Token-Erzeugung/-Gültigkeit/-Einmalverwendung, unbekannte Email, Admin-Reset),
2 neue Tests in `ExecutionServiceTest` (`getExpiredExecutions`, `delete`), 1 neuer Test in
`RouteAccessAnnotationsTest` (`ResetPasswordView` ist `@AnonymousAllowed`), 4 neue Tests in
`TerminalWebSocketTest` (Fernwartungs-Vermittlung mit einem SIMULIERTEN WS-Client als
Terminal-Gegenstelle – Log-/Neustart-Roundtrip, sofortiger Fehler bei nicht verbundenem
Standort, Timeout bei einem verbundenen, aber nicht antwortenden Terminal; bewusst in die
bestehende Testklasse integriert statt einer eigenen, um keinen weiteren Spring-Kontext/
Connection-Pool gegen den gemeinsam genutzten Test-Postgres-Cluster zu öffnen – das hatte in
einer eigenen Klasse "too many clients" ausgelöst, siehe kb/05-migration-plan.md). Backend-Suite
insgesamt **161/161** grün. Bewusst KEIN Test, der über einen echten eingebetteten
Servlet-Container eine Vaadin-Route rendert (unverändertes Risiko aus AP1).

#### Live-Updates zwischen Sessions (AP5, 2026-07-21)

Letztes Arbeitspaket der Phase-3-Roadmap vor der Playwright-Portierung (AP6, siehe
kb/05-migration-plan.md) – ersetzt die `events/`-Listener + Vaadin-Push-Mechanik des
Alt-Portals durch echte Cross-Session-Live-Updates.

**Befund zum Alt-Portal** (wichtig für die Einordnung „Feature-Parität"): `Portal/pom.xml`
bindet zwar `vaadin-push` ein und die Komponenten-Inventur (kb/05) listet „Vaadin-Push" für
`events/`, aber im tatsächlichen Alt-Code steht NIRGENDS ein `@Push` (weder auf
`WaschportalUI` noch sonstwo). Die 5 Interfaces in `Portal/.../events/`
(`IUserUpdatedEventListener`, `IUserGroupUpdatedEventListener`, `IDeviceUpdatedEventListener`,
`IProgramUpdatedEventListener`, `ILocationUpdatedEventListener`) sind reine SAME-SESSION-
Callbacks: z.B. registriert `DevicesView` beim Öffnen von `DeviceWindow` einen Listener
(`win.addDeviceUpdatedEventListener(d -> this.updateDevice(d))`), den `DeviceWindow` nach dem
Speichern synchron im selben `VaadinSession`/UI-Thread aufruft
(`DeviceWindow#save` iteriert `this.listeners`) – analog `AdminDashboardLocationPanel implements
ILocationUpdatedEventListener`, dessen `onLocationUpdated` von `LocationWindow#save` aus
derselben Session heraus aufgerufen wird. Es gibt im Alt-Code KEINEN Mechanismus, der eine
ANDERE offene Browser-Session über eine Änderung informiert. Das heißt: der in AP5 geforderte
Mindeststandard „mindestens gleichziehen mit dem Alt-Portal" war für Cross-Session-Updates
trivial erreichbar (das Alt-Portal aktualisiert dort gar nichts) – AP5 hat trotzdem die volle,
im Auftrag beschriebene Infrastruktur gebaut, nicht nur das Minimum.

**Ereignis-Infrastruktur** (neues, Vaadin-freies Package `backend/.../events/`):
`DomainEvent` – `sealed` Marker-Interface, `permits` listet alle 7 Ereignistypen explizit auf
(vollständige Inventur an einer Stelle sichtbar). 7 Records, je einer pro fachlicher
Ereignisquelle: `UserChangedEvent(userId)`, `UserGroupChangedEvent(userGroupId)`,
`DeviceChangedEvent(deviceId)`, `ProgramChangedEvent(programId)`,
`LocationChangedEvent(locationId)`, `CreditChangedEvent(userId)`,
`ExecutionChangedEvent(executionId, deviceId, userId)`.

**Auslösung in der Service-Schicht** (Auftrag: nicht in der UI, damit API-ausgelöste
Änderungen dieselben Events feuern): alle 7 Fachlogik-Services (`UserService`,
`UserGroupService`, `DeviceService`, `ProgramService`, `LocationService`, `CreditService`,
`ExecutionService`) bekamen einen `ApplicationEventPublisher`-Konstruktorparameter und
publizieren nach jeder erfolgreichen Änderung das passende Ereignis, unmittelbar nach dem
`repository.save`/`.delete`-Aufruf. Geprüfte Quelle für API-ausgelöste Executions:
`backend/.../api/ExecutionController` ruft `ExecutionService#createExecution`/
`#startExecution`/`#finishExecution`/`#resetExecution` auf (`CreditService#payExecution`
läuft dabei innerhalb von `finishExecution` mit) – da die Ereignis-Auslösung in
`ExecutionService`/`CreditService` selbst liegt, feuern Terminal-API-Aufrufe automatisch
dieselben Ereignisse wie ein Klick im Portal-UI (verifiziert in `DomainEventsTest`, siehe
„Tests" unten). Zwei bewusste Vereinfachungen (dokumentiert, keine stillen Lücken): (1)
`ExecutionService#stopExecution` (nur intern von `finishExecution` aufgerufen) publiziert
selbst NICHT, um pro Aufruf nicht zwei Ereignisse zu erzeugen; (2)
`UserGroupService#setValidLocations`/`-Devices`/`-Programs` publizieren jeweils nur ein
`UserGroupChangedEvent` statt zusätzlicher Events je einzeln betroffener
Location/Device/Program – keine der Stammdaten-Grids dieser drei Entitäten zeigt
Gruppenzugehörigkeit an, ein feingranulareres Event hätte keinen sichtbaren Effekt.

**`UiBroadcaster`** (neu, `backend/.../ui/push/`, klassisches Vaadin-„Broadcaster"-Muster):
Spring-`@Component`, hört über `@TransactionalEventListener(fallbackExecution=true)` zu
(Default-Phase `AFTER_COMMIT` – ein wegen einer Exception zurückgerollter Service-Aufruf,
z.B. `DuplicateCardIdException`, löst also KEINEN Push aus, weil das Event nie „committet"
wird). `register(UI ui, Consumer<DomainEvent> listener)` merkt sich Aufrufer-`UI` + Listener
und liefert eine `Registration` zum Abmelden zurück; Verteilung eines Ereignisses iteriert alle
Registrierungen und ruft `ui.access(() -> listener.accept(event))` – Zustellungsfehler (UI
evtl. schon geschlossen, z.B. Browser-Tab zu/Session-Timeout) werden geloggt statt die übrigen
Listener zu blockieren.

**Vaadin Push aktiviert**: neue Klasse `backend/.../ui/ElwasysAppShell implements
AppShellConfigurator`, annotiert `@Push(value=PushMode.AUTOMATIC,
transport=Transport.WEBSOCKET_XHR)` – WebSocket bevorzugt, automatischer Fallback auf lang
laufende XHR-Requests, falls ein Proxy/eine Firewall WebSockets blockiert (Vaadins
Standardkombination). Kein neuer Dependency-Eintrag nötig: `flow-push` (Atmosphere) hängt
bereits transitiv an `vaadin-spring` (Teil von `vaadin-spring-boot-starter`, seit AP1
eingebunden).

**Security-/Pfad-Koexistenz** (Auftrag – geprüft, nicht nur behauptet):
1. **Terminal-WebSocket**: Vaadins Push-Endpunkt läuft über die normale
   Vaadin-Servlet-Zuordnung (Atmosphere-Transport innerhalb desselben Request-Handlings), der
   Terminal-WebSocket-Endpunkt liegt unter dem disjunkten Pfad `/api/v1/terminal-ws`
   (`TerminalApiSecurityConfig`, eigene `@Order(1)`-Kette, `securityMatcher("/api/v1/**")`,
   siehe Abschnitt „WebSocket-Endpunkt" oben) – keine Pfad-Kollision. Bestätigt durch
   `TerminalWebSocketTest` weiterhin grün (10/10) und einen manuellen Smoke-Test des
   produktionsmodus-gebauten Jars (siehe „Build-Verifikation" unten).
2. **`VaadinSecurityConfigurer`** (`auth/SecurityConfig`, Catch-all-Kette, unverändert seit
   AP1): der Push-Handshake ist zunächst eine normale HTTP-Anfrage und durchläuft daher diese
   Kette. `VaadinSecurityConfigurer` erkennt Vaadin-interne Anfragen (über
   `com.vaadin.flow.server.HandlerHelper#isFrameworkInternalRequest`, verifiziert per
   `javap` gegen `flow-server-24.10.9.jar`) und lässt sie unabhängig vom Anmeldestatus durch –
   dieselbe Freigabe wie für Themes/Icons/das JS-Bundle selbst, die bereits seit AP1 in
   `SecurityConfig`s Javadoc als „Push-Endpunkt" erwähnt wird. Die eigentliche Autorisierung
   (welche Daten eine Session sehen darf) bleibt Sache der einzelnen Views (`@RolesAllowed`
   usw.) – der Push-Kanal selbst transportiert nur bereits serverseitig autorisierte Updates.
   `VaadinPortalSecurityTest` (5/5) blieb unverändert grün.
3. **Produktionsmodus-Build** (die für dieses Arbeitspaket kritischste Prüfung, weil `@Push`
   das Frontend-Bundle ändern kann, siehe kb/05-migration-plan.md, AP2-Befund):
   `mvn -f backend/pom.xml clean package -Pproduction -DskipTests` läuft weiterhin grün durch
   (auch nach `clean`, kein gecachter Alt-Zustand) – Vaadin liefert unverändert „A production
   mode bundle build is not needed" und nutzt das vorgefertigte `vaadin-core`-Bundle. Ein damit
   gestarteter Server liefert `/actuator/health` → `200`, `/login` → `200` wie vor AP5, mit
   derselben, bereits aus AP2 bekannten, NICHT fatalen `MissingLicenseKeyException` im Log
   (keine NEUE, durch `@Push` verursachte Fehlermeldung).

**Views abonniert** (Muster überall identisch – Lifecycle-Anforderung aus dem Auftrag
eingehalten): `onAttach(AttachEvent)` → `this.registration = broadcaster.register(
attachEvent.getUI(), listener)`; `onDetach(DetachEvent)` → `registration.remove()` + Feld auf
`null`, verhindert Session-Leaks (eine dauerhaft referenzierte `UI` nach Verlassen der Route).
- `AdminDashboardView`: reagiert auf `DeviceChangedEvent`/`ExecutionChangedEvent` mit
  GEZIELTEM Nachladen NUR des betroffenen Geräte-Panels – `refreshDevice(deviceId)` schlägt das
  Panel in einer `Map<Integer, VerticalLayout> devicePanelsByDeviceId` nach und ruft
  `populateDevicePanel(panel, dashboardService.getDeviceStatus(device))`, das den Panel-INHALT
  in-place neu aufbaut (`removeAll()` + neu befüllen), OHNE einen neuen DOM-Knoten zu erzeugen
  – nutzt damit genau die Methode, die `DashboardService` laut seinem AP3-Javadoc extra für
  diesen Zweck bereitstellt ("... damit AP5 sie wiederverwenden kann"). Ein `LocationChangedEvent`
  oder ein Ereignis für ein (noch) nicht angezeigtes Gerät (z.B. gerade neu angelegt) fällt auf
  ein vollständiges `loadData()` zurück.
- Die 5 Stammdaten-Grids (`AdminUsersView`, `AdminUserGroupsView`, `AdminDevicesView`,
  `AdminProgramsView`, `AdminLocationsView`) laden bei ihrem jeweiligen `*ChangedEvent`
  vollständig neu (`grid.setItems(service.findAll())`); `AdminUsersView` zusätzlich bei
  `CreditChangedEvent`/`ExecutionChangedEvent`, weil die Liste eine Guthaben-Spalte und ein
  Warndreieck-Icon für abgelaufene Ausführungen zeigt (beide hängen von diesen Ereignissen ab).
- `UserDashboardView`: für AP5 refaktoriert – Guthaben-/„Letzte Einzahlung"-Kachel und die
  Buchungstabelle sind jetzt Felder (`Span`/`Grid`) statt lokaler Konstruktor-Variablen, eine
  neue `refresh()`-Methode aktualisiert nur deren Inhalt. Abonniert `CreditChangedEvent`/
  `ExecutionChangedEvent`, gefiltert auf die EIGENE Benutzer-Id (`concernsOwnUser`, ein
  Java-21-Pattern-Matching-`switch` über die `DomainEvent`-Records) – andere Benutzer betreffen
  diese Session dank der seit AP3 bestehenden Datenisolation (Benutzer kommt aus
  `ElwasysUserPrincipal`, nicht aus einem Pfad-/Query-Parameter) ohnehin nicht.

**Bewusst NICHT Teil dieses Arbeitspakets** (siehe kb/05-migration-plan.md, „Offene
Punkte/Risiken"): der bestehende blockierende Request/Response-Aufruf der
Fernwartungs-Knöpfe (`AdminDashboardView#showLog`/`#restart` → `TerminalMaintenanceService`,
synchron im Vaadin-Request-Thread, bis zu ~10s bei einem verbundenen, aber nicht antwortenden
Terminal) auf einen asynchronen Aufruf mit Ladeindikator umzustellen – die dafür nötige
Push-Infrastruktur steht jetzt zur Verfügung, der Umbau selbst war nicht Auftragsgegenstand
dieses Arbeitspakets und bleibt offen für ein künftiges Arbeitspaket.

**Tests** (12 neu, Backend-Suite insgesamt **173/173** grün, 161 vorher + 12 neu):
- `UiBroadcasterTest` (4, Package `backend/.../ui/push/`, reiner Unit-Test OHNE Spring-Kontext
  – `UI#access` per Mockito synchron simuliert, kein echter Servlet-Container nötig): deckt
  „Broadcaster verteilt an registrierte Listener" (einzeln + an mehrere gleichzeitig) und
  „Abmelden funktioniert" (inkl. „nur der abgemeldete Listener verstummt, andere bleiben aktiv")
  laut Auftrag ab.
- `DomainEventsTest` (8, Package `backend/.../events/`, ECHTER Spring-Kontext über
  `AbstractBackendIT`/`Fixtures`): deckt zusätzlich Springs `@TransactionalEventListener`/
  `AFTER_COMMIT`-Mechanismus selbst mit ab (nicht nur den reinen Verteil-Mechanismus von
  `UiBroadcasterTest`) – je ein Test pro Service-Ereignisquelle (User/UserGroup/Device/Program/
  Location/Credit), ein Execution-Lebenszyklus-Test (create→start→finish erzeugt mindestens 3
  `ExecutionChangedEvent`s, alle mit derselben `deviceId`) sowie ein Abmelde-Test über den
  vollen Stack (Service → Event → `UiBroadcaster`).
- Bewusst KEIN Test, der über einen echten eingebetteten Servlet-Container eine Vaadin-Route
  rendert (unverändertes Risiko/Vorgabe aus AP1, siehe kb/05-migration-plan.md, Lizenz-Befund)
  – die geänderten Views sind stattdessen durch Compile + `RouteAccessAnnotationsTest`
  (unverändert grün, keine Routen/Rollen-Annotationen angefasst) + `VaadinPortalSecurityTest`
  (unverändert grün) + den manuellen Produktionsmodus-Smoke-Test oben abgesichert.
- **Naming-Fallstrick gefunden und behoben**: eine ursprünglich `DomainEventsIT` genannte
  Testklasse wurde von Maven Surefire (Standard-Includes `**/*Test.java`, NICHT `**/*IT.java` –
  letzteres ist die Failsafe/`mvn verify`-Konvention, dieses Projekt/`run-backend-tests.sh`
  laufen `mvn test`) beim ersten Testlauf stillschweigend NIE ausgeführt (0 Fehler, weil 0
  Tests gefunden – nicht sofort auffällig). Behoben durch Umbenennen auf `DomainEventsTest`;
  seither sind `*IT.java`-Dateien in diesem Modul wieder ausschließlich die beiden abstrakten
  Test-Basisklassen (`AbstractBackendIT`, `AbstractApiIT`), wie im übrigen Modul bereits
  durchgängige Konvention.

**Build-Verifikation**: `mvn -f backend/pom.xml compile`/`test-compile` grün;
`backend/run-backend-tests.sh` **173/173** grün; `mvn -f backend/pom.xml clean package
-Pproduction -DskipTests` grün (siehe oben, kritische Prüfung wegen `@Push`); `git status`/
`git diff --stat` bestätigen 0 Quelländerungen an Common/Client-Raspi/Portal (nur `backend/`,
`kb/`).
