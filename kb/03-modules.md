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

| Typ | Richtung | Phase-2-Verhalten (AP4) |
|---|---|---|
| `HELLO` | Terminal → Backend | Server antwortet `HELLO_ACK` |
| `HELLO_ACK` | Backend → Terminal | Payload `locationId`, `locationName`, `serverTime`, `protocolVersion` |
| `PING` | beide Richtungen | Empfänger antwortet `PONG` (server-seitig; ein vom Backend gesendetes `PING`, siehe Heartbeat unten, erwartet ein `PONG` vom Terminal) |
| `PONG` | beide Richtungen | Aktualisiert intern den „zuletzt gesehen“-Zeitstempel der Verbindung |
| `STATUS_REQUEST` | beide Richtungen | Server antwortet `STATUS_RESPONSE` (Gerüst: `locationId`, `locationName`, `connectedSince`, `serverTime` – die volle Fernwartungs-Portierung [laufende Ausführungen, Backlight-/Interface-Status, fachliche Referenz `GetStatusResponse`] folgt Phase 3/4) |
| `STATUS_RESPONSE` | beide Richtungen | Phase 2: nur geloggt, kein Handler (Portal-seitige Auswertung folgt mit der Fernwartungs-Portierung) |
| `LOG_REQUEST`/`LOG_RESPONSE` | reserviert | Beantwortet mit `ERROR{reason:"not-implemented"}` (fachliche Referenz `GetLogRequest`/`GetLogResponse`, Portierung folgt Phase 3/4) |
| `RESTART_REQUEST` | reserviert | wie oben (fachliche Referenz `RestartAppRequest`) |
| `ERROR` | beide Richtungen | Protokoll-/Verarbeitungsfehler, `payload.reason` (`malformed-message`/`not-implemented`) |

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
| Portal `PasswordForgotWindow`: „Passwort vergessen" | E-Mail (dieselbe `Utilities#sendEmail`) | Nutzer per E-Mail-Adresse gefunden | **Nein** – hängt am neuen Portal-Login-Flow (Reset-Key/-URL), laut Roadmap Phase 3 |
| Portal `UserWindow`: Admin setzt neues Passwort | E-Mail | Admin-Aktion im Nutzer-Fenster | **Nein** – ebenfalls Phase 3 |

**Wichtiger Fallstrick** (im Code dokumentiert, siehe `NotificationService`-Javadoc):
`users.push_notification` (Entity-Feld `UserEntity#isPushNotification()`) ist im Alt-Code
das Opt-in für den **nicht** portierten elwaApp/Ionic-Kanal, nicht für Pushover. Das
Pushover-Opt-in ergibt sich ausschließlich daraus, ob `pushover_user_key` gesetzt ist.

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

### Portal-UI (Vaadin Flow, Phase 3 AP1, 2026-07-20, Package `backend/.../ui/`)

Admin-Portal als Vaadin-Flow-UI im Backend (siehe kb/05-migration-plan.md, Zielarchitektur
„Portal ist Teil des Backends“) – dieses Arbeitspaket liefert nur das Grundgerüst
(Login/Layout/Navigation/Rollen-Guard); Stammdaten-Inhalte folgen in AP2/AP3.

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
  Funktionsänderung. 6 Platzhalter-Views (`AdminDashboardView`, `AdminUsersView`,
  `AdminUserGroupsView`, `AdminProgramsView`, `AdminDevicesView`, `AdminLocationsView`), alle
  `@RolesAllowed("ADMIN")` – Inhalte folgen in AP2/AP3.
- `user/UserLayout` (`AppLayout` + `SideNav`, ein Menüpunkt „Übersicht") – fachlicher
  Nachfolger des schlanken `Portal/.../UserLayout`; laut Auftraggeber loggen sich im
  Wesentlichen nur Admins ein (kb/05-migration-plan.md, „Entscheidungen“), entsprechend
  niedrigere Parity-Priorität. `UserDashboardView` (`@RolesAllowed("USER")`) ist ein
  Platzhalter – Guthaben-/Übersichtsinhalte (vgl. P15) folgen in AP3.
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
