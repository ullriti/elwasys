# 03 – Module im Detail

Der Root-Reactor umfasst zwei Module: **Client-Raspi** (JavaFX-Terminal) und **backend**
(zentrales Spring-Boot-Backend inkl. eingebettetem Vaadin-Flow-Portal). Beide bauen mit
Sprachlevel **Java 21** gegen die gemeinsame PostgreSQL-Datenbank – die Terminals dabei
ausschließlich über die REST-API v1 + WebSocket des Backends, nie direkt (siehe
[ADR 0004](../architecture/0004-terminals-ohne-direkt-db-zugriff.md)).

## Client-Raspi (`org.kabieror.elwasys.raspiclient`)

JavaFX-Terminal, fat-jar, läuft auf einer Java-21-Runtime.

**Datenzugriff**: einzige Pfade sind die REST-API v1 des Backends (`api/ApiClient`) und die vom
Terminal ausgehende WebSocket-Verbindung (`ws/TerminalWebSocketClient`) – beide über dieselbe
Backend-URL und dasselbe Standort-Token (`backend.url`/`backend.token`). Kein `DataManager`,
kein JDBC-Import in `src/main` (der PostgreSQL-Treiber ist nur noch test-scope, da nur die
E2E-Harness per JDBC seedet).

**Ex-`Common`-Klassen**: sechs gemeinsame Utility-Klassen liegen im Package
`org.kabieror.elwasys.common`, physisch unter `Client-Raspi/src/main/org/kabieror/elwasys/common/`,
und werden mit dem Client gebaut. Nur das Terminal nutzt sie zur Laufzeit; das Backend hat ein
eigenes, per Flyway verwaltetes Datenmodell und keine Produktiv- oder Test-Abhängigkeit auf
`common` (das Alt-SHA1-Format bildet der Backend-Test-Helfer `LegacySha1` lokal nach):
- `ConfigurationManager` – Basis-Konfigurationsverwaltung
- `Utilities` (enthält `APP_VERSION`, Passwort-Hilfsfunktionen inkl. `sha1`, `maskCardId`,
  `getCurrentLogFile`), `FormatUtilities`
- `ProgramType` – Enum
- Exceptions: `LocationOccupiedException`, `NoDataFoundException`

Die von diesen Klassen benötigten Bibliotheken sind direkte Client-Raspi-Dependencies (u. a.
Commons Lang3). Test-Harnesses, die eine 0.4.0-DB brauchen, spielen die Flyway-Baseline `V1`
direkt per psql ein (DB vorher per `CREATE DATABASE` anlegen).

**Pakete** (siehe [01-architecture.md](01-architecture.md) für Details):
- `application/` – `Main`, `ElwaManager` (Singleton, verdrahtet `api/ApiClient` +
  `ws/TerminalWebSocketClient`; `#cardLogin`/`#getDevicesForUser`/`#createExecution`/
  `#getManagedDevices` kapseln Online-Pfad + Offline-Fallback hinter demselben DTO-Vertrag),
  `SingleInstanceManager`, `ActionContainer`, `ApplicationInterfaceType`, Close-Listener,
  `TerminalReadinessMarker`.
- `api/` – `ApiClient` (schlanke REST-Schicht auf `java.net.http`, Standort-Token als
  `Authorization: Bearer`, `Idempotency-Key`-Header für Execution-Endpunkte,
  `replayCreateExecution` für den privilegierten Nachbuchungs-Pfad), `ApiException`, `dto/`
  (Records: `CardLoginRequest`, `CreditResponse`, `DeviceDto`, `DeviceOverviewDto`,
  `ExecutionDto`, `ExecutionEndRequest`/`-StartRequest`, `LocationDto`, `ProgramDto`,
  `UpdateDeconzUuidRequest`, `UserDto`, `SnapshotDto` +
  `SnapshotUserDto`/`SnapshotUserGroupDto`/`SnapshotDeviceDto`/`SnapshotProgramDto`,
  `DiscountType`) – 1:1 an die Backend-REST-API v1 angelehnt (siehe „REST-API v1“ unten).
- `ws/` – `TerminalWebSocketClient` (ausgehende Fernwartungs-Verbindung zum Backend,
  `/api/v1/terminal-ws`, `org.springframework.web.socket.client.standard.StandardWebSocketClient`,
  identisches Muster zu `devices/deconz/DeconzEventListener`), `TerminalWsMessage`/
  `TerminalWsMessageType` (Client-seitiges Gegenstück zum Backend-Protokoll) – siehe
  „Ausgehende Fernwartungs-Verbindung“ unten.
- `model/` – `ClientDevice`/`ClientExecution`/`ClientProgram`/`ClientUser`: über den
  `ApiClient` befüllte Adapterklassen, die einen Identitäts-Cache nachbilden (konsistente
  Objektidentität über mehrere `ElwaManager#getManagedDevices()`-Aufrufe hinweg, siehe
  `ElwaManager`-Javadoc). `ClientExecution#isOfflinePendingReplay()` markiert eine offline
  gebuchte, noch nicht nachgemeldete Ausführung (entkoppelt von `isVirtual()`).
- `ui/` – State-Machine + zwei UI-Größen:
  - `ui/small/` – 320×240 (`MainFormController`, `MainFormStateManager`, `ProgramListItem`)
  - `ui/medium/` – 800×480 (Haupt-UI): `MainFormController`, `MainFormStateManager`,
    `controller/` (Startup, DeviceView, ProgramListEntry, Confirmation, Abort, Error,
    Toolbar, UserSettings, Wait, Copyright), `state/` (ErrorState, ToolbarState, Listener)
  - `ui/scheduler/` – `InactivityScheduler`, `InactivityJob/Future`, `BacklightManager`
  - `ui/` gemeinsam: `MainFormState`, `Icons`, `UiUtilities`, `AbstractMainFormController`
- `executions/` – `ExecutionManager`, `ExecutionFinisher`, Listener, `FhemException`
- `offline/` – `OfflineSnapshotStore`, `OfflineJournal`(`Entry`), `OfflinePricing`,
  `OfflineGateway`, `OfflineJsonSupport` – siehe „Offline-Robustheit“ unten
- `devices/` – `deconz/` (Service, ApiAdapter, EventListener, PowerManager,
  RegistrationService, `model/`), `FhemDevicePowerManager`, Interfaces, `DevicePowerState`
- `io/` – `CardReader`, `TelnetClient`, Card-Events
- `configuration/` – `WashguardConfiguration` (liest `backend.url`/`backend.token`)
- `util/` – `BlockingMap`

**Readiness-Marker**: `application/TerminalReadinessMarker` schreibt beim erfolgreichen Wechsel
in den bedienbereiten Zustand `MainFormState.SELECT_DEVICE` eine Marker-Datei mit frischem
`mtime` (`${user.dir}/.terminal-ready`, überschreibbar per System-Property
`elwasys.readyMarkerFile`). Aufgerufen als Einzeiler aus BEIDEN `MainFormStateManager#gotoState`
(medium + small). Jeder IO-Fehler wird gefangen und nur geloggt, nie in die UI geworfen – der
Bedienfluss ändert sich nicht. Der Shell-Watchdog `deploy/terminal/auto-update-watchdog.sh`
wertet den `mtime` aus, um einen erfolgreichen Start einer frisch ausgerollten Version zu
verifizieren (siehe [04-build-and-run.md](04-build-and-run.md), „Auto-Update mit Rollback“).

### Ausgehende Fernwartungs-Verbindung (Package `ws/`)

Das Terminal hält eine dauerhafte, von ihm ausgehende WebSocket-Verbindung zum Backend –
dieselbe Richtung wie die REST-API v1, NAT-/Firewall-freundlich (siehe
[ADR 0005](../architecture/0005-fernwartung-ueber-ausgehende-websocket-verbindung.md)).

- **Transport/Technologie**: `StandardWebSocketClient` + `TextWebSocketHandler`
  (`spring-boot-starter-websocket`, identisches Muster zu `devices/deconz/DeconzEventListener`).
- **Auth**: derselbe Standort-Token wie `api/ApiClient`, als `Authorization: Bearer <token>`-
  Header beim Handshake (geprüft von derselben `TerminalApiSecurityConfig`-Kette wie die
  REST-Endpunkte). Kein eigener Konfig-Schlüssel – `backend.url`/`backend.token` bedienen REST
  UND WebSocket.
- **Verbindungsaufbau**: `HELLO` (Payload `clientVersion`, `clientUid`) direkt nach
  `afterConnectionEstablished`, Backend antwortet `HELLO_ACK`.
- **Heartbeat**: rein reaktiv – das Backend sendet periodisch `PING`, der Client antwortet nur
  mit `PONG`; das Backend erkennt eine tote Verbindung über sein eigenes 90s-Timeout.
- **Reconnect**: bei Verbindungsfehler/-abbruch automatischer Reconnect mit exponentiell
  wachsender Wartezeit (5s bis max. 5min) – identisches Muster zu
  `DeconzEventListener#scheduleReconnect`. Die Verbindung überlebt einen vom Portal ausgelösten
  Neustart bewusst (`onClose(restart=true)` baut NICHT ab).
- **Fachfunktionen** (alle PORTAL-initiiert, Backend → Terminal, vermittelt über
  `TerminalMaintenanceService`; der Client sendet nur `HELLO` und Antworten):
  - `STATUS_REQUEST`: Antwort enthält `clientVersion`, `startupTime` und die Ids aller aktuell
    laufenden Ausführungen (rein lokal aus `ExecutionManager#getRunningExecutions()`, kein
    Netzwerkzugriff).
  - `LOG_REQUEST`: aktueller Inhalt der Logdatei (`Utilities#getCurrentLogFile()`, der
    INFO-Appender `"FILE"`).
  - `RESTART_REQUEST`: `ElwaManager#restart()`; der Client bestätigt den Empfang zuerst mit
    `RESTART_RESPONSE`, bevor der Neustart ausgeführt wird.

### Terminal-Robustheit & Härtung

Härtungen für den Betrieb auf einem Raspberry Pi ohne RTC/mit möglichem Stromausfall sowie gegen
Nebenläufigkeits-/Neustart-Lecks:

- **`OfflineJournal` stromausfallfest**: `append` schreibt mit `StandardOpenOption.DSYNC`, sodass
  ein Journaleintrag einen Stromausfall unmittelbar nach der Buchung übersteht. Neben dem Journal
  führt es eine Dead-Letter-Datei (`offline-journal.jsonl.deadletter`) samt
  `moveToDeadLetter`/`hasPendingEntries` – Ziel der „Poison-Entries“ beim Replay.
- **Uhren-Plausibilität**: der Snapshot-/Offline-Pfad gilt zusätzlich als unbrauchbar, wenn
  `now < generatedAt` – ein Pi ohne RTC kann nach einem Stromausfall mit einer in der
  Vergangenheit stehenden Uhr hochkommen (Herleitung siehe
  [ADR 0016](../architecture/0016-offline-replay-haertung.md)).
- **`DeconzEventListener` reconnectet selbst**: der Listener stößt den Reconnect auch aus
  `afterConnectionClosed`/`handleTransportError` an; ein `isShutdown()`-Guard in `openConnection`
  verhindert einen Reconnect während des geplanten Herunterfahrens.
- **WS-Lifecycle neustartfest**: `ElwaManager#initiate()` erzeugt den `TerminalWebSocketClient`
  nur einmal (Null-Guard) und meldet ihn nach `closeListeners.clear()` neu am Close-Event an –
  nach einem In-Prozess-Neustart (`ElwaManager#restart()`) bleibt genau ein WS-Client übrig.
  `CardReader#listenToCardDetectedEvent` ist idempotent, die UI-Handler werden über
  `MainFormController#installComponentHandlersOnce()` nur einmal installiert.
- **Nebenläufigkeit / Doppel-Finish**: `executionFinishers`/`plannedStops` sind
  `ConcurrentHashMap`; `ExecutionFinisher#retry()` läuft über denselben `runGuarded()`-Rumpf wie
  `run()` (Objekt- + Geräte-Lock, `executed`-Guard), sodass ein Retry eine bereits abgeschlossene
  Ausführung nicht ein zweites Mal beendet/abrechnet.
- **App-interner Geräte-Scan bricht nicht ab**: der Fremdeinschalt-/Geräte-Scan in
  `ExecutionManager` überspringt ein nicht erreichbares Gerät (`continue`) und prüft die übrigen
  weiter.
- **Kaputte 2xx-Antwort ist kein Offline-Fall**: `ApiException.malformedResponse(...)`
  (`isCommunicationFailure() == false`) wird von `ApiClient#parse` bei einer 2xx-Antwort mit
  `JsonSyntaxException` geworfen – ein erreichbares, aber unlesbar antwortendes Backend löst NICHT
  den Offline-Pfad aus (der ist reinen Kommunikationsfehlern vorbehalten).
- **RFID-Log-Maskierung**: `Utilities#maskCardId` zeigt nur die letzten 4 Stellen einer
  Kartennummer; `CardReader` UND beide `MainFormController` loggen ausschließlich maskiert.
- **Resume-Null-Check**: eine beim Wiederaufnahme-Scan gefundene Ausführung, deren Programm
  zwischenzeitlich entfernt wurde, wird mit einer Warnung übersprungen statt in einen
  Fehlerzustand zu laufen.
- **Setup-Härtung**: `setup.sh` erzeugt das deCONZ-Passwort per `openssl rand -hex 16` (CSPRNG).
  Der Client versendet keine Mails mehr (keine `getSmtp*`-Getter im `ConfigurationManager`).

**FXML** (unter `ui/small/` und `ui/medium/components/`): MainForm + DevicePane, WaitPane,
ConfirmationPane, ToolbarPane, ErrorPane, AbortPane, UserSettingsPane, StartupPane, CopyrightBar,
DeviceListEntry, ProgramListEntry.

**Tests** (`src/test/...`, JUnit 5 + TestFX/Xvfb, siehe [06-ui-tests.md](06-ui-tests.md) für den
vollständigen Stand): u. a. `fhemsimulator/` (fake fhem über Telnet) und `deconzsimulator/`
(`DeconzSimulator`/`DeconzWebSocketServer`/`SimulatedLight`, fake deCONZ über REST + einen
minimalen, selbst geschriebenen WebSocket-Server, JDK-only) als austauschbare Gateway-Doubles für
die `Client*E2ETest`-Klassen.

**Konfiguration**: `elwasys.properties` (Beispiel: `elwasys.example.properties`). Wichtige Keys:
`backend.url`/`backend.token` (einziger Datenzugriffspfad – Backend-Basis-URL + Standort-Token,
bedient REST-API und Fernwartungs-WebSocket), `location` (nur Anzeigename, z. B. in
Fehlermeldungen), `displayTimeout`, `startupDelay`, `sessionTimeout`, `portalUrl`,
`offline.pollIntervalSeconds` (Default 20), `deconz.*` **oder** `fhem.*`. `setup.sh` fragt keine
Datenbank-/Maintenance-Port-Werte ab.

**Abhängigkeiten**: `javafx-controls`/`-fxml`/`-web` **23.0.2** (höchste über Maven Central
verfügbare stabile JavaFX-Version, deren Bytecode noch auf der Java-21-Runtime läuft; JavaFX 24+
verlangt JDK 22+), `slf4j-api` **2.0.18**, `logback-classic`/`-core` **1.5.38** – alle drei als
direkte, versionierte Dependencies in `Client-Raspi/pom.xml` (überschreiben die ältere
`elwasys-parent`-`dependencyManagement` für dieses Modul über Maven-„nearest wins“). `pi4j-core`,
`gson` (JSON-(De-)Serialisierung für `api/ApiClient` und `ws/TerminalWsMessage`) und
`spring-boot-starter-websocket` (für `ws/TerminalWebSocketClient` und den deCONZ-WS-Client).
REST läuft durchgängig über `java.net.http`; das Terminal versendet keine Benachrichtigungen mehr
selbst (das Backend übernimmt zentral).

## Backend (`org.kabieror.elwasys.backend`)

Spring-Boot-Modul (Java 21) – das zentrale Backend: REST-API/WebSocket für die
Client-Raspi-Terminals UND das eingebettete Vaadin-Flow-Admin-Portal, auf derselben
PostgreSQL-Datenbank wie die Terminals (siehe
[ADR 0002](../architecture/0002-zentrales-spring-boot-backend.md) und
[05-migration-plan.md](05-migration-plan.md), Zielarchitektur). Keine Abhängigkeit auf `common` –
das Backend hat sein eigenes, per Flyway verwaltetes Datenmodell.

### Datenmodell & Geschäftslogik

**Entities** (`backend/.../domain/`, siehe [02-data-model.md](02-data-model.md) für die
Tabellenherkunft): `UserGroupEntity`, `UserEntity`, `LocationEntity`, `DeviceEntity`,
`ProgramEntity`, `ExecutionEntity`, `CreditAccountingEntryEntity`, `ConfigEntity`
(aktuell ungenutzt), `TerminalTokenEntity`. Die vier n:m-Tabellen (`locations_valid_user_groups`,
`devices_valid_user_groups`, `programs_valid_user_groups`, `device_program_rel`) sind als
`@ManyToMany`+`@JoinTable` modelliert (keine eigenen Entity-Klassen). Postgres-native Enums
(`DISCOUNT_TYPE`, `PROGRAM_TYPE`, `TIME_UNIT_TYPE`) werden über
`@JdbcTypeCode(SqlTypes.NAMED_ENUM)` gebunden. Alle fachlich genutzten Assoziationen sind
`FetchType.EAGER` (bildet den durchgängig eager ladenden Alt-`DataManager` nach).
`spring.jpa.hibernate.ddl-auto=none` ist explizit gesetzt: das Schema kommt ausschließlich von
Flyway. `CreditAccountingEntryEntity` bietet keine Setter für gespeicherte Buchungen, nur den
Konstruktor – Buchungen sind strukturell unveränderlich.

**Repositories** (`backend/.../repository/`): `UserGroupRepository`, `LocationRepository`,
`DeviceRepository`, `ProgramRepository`, `UserRepository`, `ExecutionRepository`,
`CreditAccountingEntryRepository`. `UserRepository#findByCardId` bildet die
Alt-Code-Kartennummernsuche nach, regex-frei über
`:cardId = ANY(string_to_array(card_ids, E'\n'))`, flankiert von `@Pattern("[0-9A-Fa-f]{1,50}")`
auf `CardLoginRequest.cardId`. Für pessimistisches Zeilen-Locking gibt es
`@Lock(LockModeType.PESSIMISTIC_WRITE) findWithLockById` auf `UserRepository`/
`ExecutionRepository`.

**Services** (`backend/.../service/`), jeweils 1:1-Portierung der Alt-Code-Logik (siehe Javadoc
für Quellenverweise):
- `PricingService` – Programmpreisberechnung (FIXED/DYNAMIC, Freiminuten, Gruppenrabatt).
- `CreditService` – Guthabenberechnung und -buchung (Einzahlung, Auszahlung,
  Ausführungs-Bezahlung; Buchungen unveränderlich). `getCredits(List)` berechnet die Guthaben
  einer Liste in zwei statt 2·N Abfragen; `requirePositive` validiert Buchungsbeträge zentral.
- `PermissionService` – Berechtigungs-Matrix (Standort/Gerät/Programm × Benutzergruppe, gesperrte
  Benutzer, deaktivierte Geräte).
- `ExecutionService` – Execution-Lebenszyklus auf Persistenzebene (Anlegen/Start/Ende/Abbruch/
  Reset, Preis, Ablauf-Erkennung; hardwarenahe Teile bleiben im Terminal). `#startExecution`/
  `#stopExecution`/`#finishExecution` haben Überladungen mit `clientTimestamp`-Parameter
  (Verhalten bei `null` unverändert: `LocalDateTime.now()`). `#stopExecution` erzwingt
  `stop = start`, falls ein nachgemeldeter `stop` VOR dem `start` läge (keine negative Dauer);
  `getPrice` deckelt die abgerechnete Dauer auf `min(stop − start, maxDuration)`.

Weitere Services: `DashboardService` (Standort→Geräte→Status, `LocationStatus`/`DeviceStatus`,
Vaadin-frei), `UserService`, `UserGroupService`, `DeviceService`, `ProgramService`,
`LocationService`, `PasswordService`, `PasswordResetService`, `RateLimiter`, `AdvisoryLockService`
– Details in den jeweiligen Abschnitten unten.

Die App-Relikt-Spalten/-Tabellen (`app_id`/`access_key`/`auth_key`, `reservations`,
`foreign_authkeys`, Trigger `user_authkey_trigger`) sind nicht gemappt und per Flyway entfernt
(siehe [02-data-model.md](02-data-model.md)).

**Auth-Parity-Tests**: Das Backend bindet `common` nicht ein. Die verbliebenen Parity-Prüfungen
betreffen nur das Alt-Passwortformat und reproduzieren es byte-genau lokal über den Test-Helfer
`LegacySha1` (`backend/src/test/.../auth/LegacySha1.java`, 1:1 aus
`org.kabieror.elwasys.common.Utilities#sha1`), z. B. in `PasswordVerificationServiceParityTest`.

### Auth (Package `backend/.../auth/`)

- `PasswordVerificationService` – erkennt und verifiziert BEIDE im Bestand vorkommenden
  Passwortformate: Argon2id (Präfix `$argon2id$`, über
  `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`) und SHA1-Legacy (40-Zeichen-Hex, 1:1
  aus `Utilities#sha1`, konstanter Byte-Vergleich über `MessageDigest.isEqual`). `encodeNew(...)`
  erzeugt IMMER Argon2id – jedes Neusetzen eines Passworts bekommt das neue Format, unabhängig
  vom Re-Hash-Flag (das steuert nur die Migration bestehender SHA1-Hashes beim Login).
- `AuthProperties` – `elwasys.auth.rehash-on-login` (Default **`false`**): der
  Re-Hash-Migrationspfad steckt hinter diesem Flag; ob/wann es aktiviert wird, ist eine
  Entscheidung der Produktivumschaltung (siehe
  [ADR 0007](../architecture/0007-passwort-hashing-argon2id-mit-rehash.md) und
  `AuthProperties`-Javadoc). Zusätzlich `maxFailedLoginAttempts`/`loginLockoutWindow` (Brute-Force,
  siehe unten).
- `ElwasysAuthenticationProvider` – lädt den Benutzer per case-insensitivem Username-Vergleich
  (`findByUsernameIgnoreCaseAndDeletedFalse`, schließt gelöschte Nutzer aus), prüft das Passwort
  über `PasswordVerificationService`, migriert bei SHA1-Treffer + aktiviertem Flag transaktional
  auf Argon2id, aktualisiert `last_login` und vergibt `ROLE_ADMIN`/`ROLE_USER` aus
  `users.is_admin`. **Bewusste Abweichung vom Alt-Code**: weist gesperrte Nutzer (`blocked=true`)
  aktiv ab (der Alt-Portal-Login prüfte das nicht; siehe
  [ADR 0018](../architecture/0018-ap4-auth-security-entscheidungen.md)). **Brute-Force-Sperre**:
  nach zu vielen Fehlversuchen (Default 5 / 15 min) wird der Login zeitweise gesperrt; die Sperre
  meldet sich mit einer neutralen `BadCredentialsException` (kein Orakel), der Fehlversuchszähler
  wird nur für existierende Nutzer geführt und bei Erfolg zurückgesetzt.
- `ElwasysUserPrincipal` – `UserDetails`-Implementierung ohne echten Passwort-Hash in der Session
  (`getPassword()` liefert `""`); trägt Benutzer-ID/-Name/Admin-Flag.
- `SecurityConfig` – die Catch-all-`SecurityFilterChain`: `/actuator/health` (inkl. der
  Untergruppen `/actuator/health/liveness`, `/actuator/health/readiness`,
  `/actuator/health/operational`) `permitAll()`, alles andere `authenticated()`. Bindet
  `LoginView` über `VaadinSecurityConfigurer` als Login-Ziel (siehe „Portal-UI“ unten). Bewusst so
  gehalten, dass `TerminalApiSecurityConfig` eine eigene, zustandslose Kette (`/api/v1/**`,
  niedrigere `@Order`-Zahl) danebenstellt, ohne diese Klasse zu ändern.
- `AdminPasswordCliRunner` – setzt das Admin-Passwort über ein CLI-Profil (siehe
  [04-build-and-run.md](04-build-and-run.md)).
- `RateLimiter` (`service/`) – In-Memory-Fixed-Window-Zähler, ein einziger `@Component`-Singleton,
  geteilt von der Login-Brute-Force-Sperre UND dem Passwort-Reset-Cooldown.
- **Case-insensitiver Username-Guard**: Anlegen/Umbenennen prüft über
  `existsByUsernameIgnoreCaseAndDeletedFalse` und wirft `DuplicateUsernameException` – bewusst OHNE
  `LOWER(username)`-Migration (der Altbestand kann Namensvarianten enthalten).

`users.password` ist `VARCHAR(255)` (Argon2id-Strings mit den obigen Parametern sind konstant
97 Zeichen; SHA1-Hex nur 40) – siehe [02-data-model.md](02-data-model.md), Migration `V2`.

### Abhängigkeiten

Spring Boot **3.5.16** per BOM-Import in `dependencyManagement` (nicht über
`spring-boot-starter-parent`, da das Modul bereits `elwasys-parent` erbt):
- `spring-boot-starter-web`, `-actuator`, `-jdbc`, `-validation`, `-data-jpa`, `-security`,
  `-websocket`, `-mail`
- `org.bouncycastle:bcprov-jdk18on` (fest gepinnt) – von `Argon2PasswordEncoder` als
  Kryptografie-Provider benötigt, bewusst NICHT vom Spring-Boot-BOM verwaltet
- `flyway-core` + `flyway-database-postgresql`, PostgreSQL-Treiber
- `vaadin-bom`/`vaadin-spring-boot-starter` **24.10.8** (siehe „Portal-UI“ unten)
- Tests: `spring-boot-starter-test`, `spring-security-test`, `testcontainers`
  (`junit-jupiter`, `postgresql`), `greenmail-junit5`
- **Overrides**: `elwasys-parent`s `dependencyManagement` pinnt Logback/slf4j für den
  Client-Kontext auf zu alte Versionen für Spring Boot 3.5; `backend/pom.xml` hat daher eigene
  BOM-konforme Overrides (Logback 1.5.34, slf4j-api 2.0.18, Postgres-Treiber 42.7.11).

**Build-Besonderheit**: `backend/pom.xml` setzt `maven.compiler.parameters=true` – ohne dieses
Flag wirft Spring MVC zur Laufzeit eine `IllegalArgumentException` für jeden
`@RequestParam`/`@PathVariable` ohne explizit angegebenen Namen. Der inkrementelle Compiler
erkennt eine reine Properties-/Plugin-Konfigurationsänderung nicht immer als Grund für eine
Neukompilierung – nach einer solchen Änderung ggf. `mvn clean` davor schalten.

### Struktur

- `.../backend/BackendApplication.java` – Einstiegspunkt (`@SpringBootApplication`)
- `src/main/resources/application.yml` – Konfiguration (DB-URL/User/Passwort per
  `ELWASYS_DB_URL`/`ELWASYS_DB_USER`/`ELWASYS_DB_PASSWORD`), Flyway (`baselineOnMigrate`),
  Actuator- und Logging-Einstellungen; `management.health.mail.enabled: false` (siehe
  „Benachrichtigungsdienst“)
- `src/main/resources/db/migration/` – Flyway-Migrationen `V1`–`V11` (siehe
  [02-data-model.md](02-data-model.md))
- `src/main/resources/application-token-cli.yml` – Profil `token-cli` für `TerminalTokenCliRunner`
  (`spring.main.web-application-type: none`)
- `.../domain/` – JPA-Entities
- `.../repository/` – Spring-Data-Repositories
- `.../service/` – Geschäftslogik-Services
- `.../exception/` – `NotEnoughCreditException`, `DuplicateCardIdException`,
  `DuplicateUsernameException`, `EntityInUseException`, `PasswordTooShortException` u. a.
- `.../auth/` – Auth (siehe oben) + `auth/terminal/` – Standort-Token-Auth (siehe unten)
- `.../api/` – REST-API v1 (siehe unten): Controller (`CardLoginController`,
  `LocationController`, `DeviceController`, `ExecutionController`, `UserController`,
  `SnapshotController`), `api/dto/`, `api/exception/` (`ApiException`-Hierarchie),
  `ApiExceptionHandler`, `TerminalScopeGuard`, `OpenApiConfig`, `api/idempotency/`
- `.../ws/` – WebSocket-Endpunkt (siehe unten)
- `.../offline/` – `ClientTimestampPolicy`, `OfflineProperties` (siehe „Offline-Robustheit“)
- `.../notification/` – Benachrichtigungsdienst (siehe unten)
- `.../ui/` – Vaadin-Flow-Admin-Portal (siehe „Portal-UI“)
- `.../events/` – Domain-Events: `DomainEvent` (sealed Marker-Interface) + 7 Records
- `.../ui/push/` – `UiBroadcaster`
- `src/main/frontend/` – von Vaadin auto-generierte Frontend-Tooling-Ausgabe (`generated/` per
  `.gitignore` ausgeschlossen; `index.html` committet)
- `src/test/java/.../support/` – Test-Support: `TestPostgres` (Testcontainers-Default oder
  `ELWASYS_TEST_JDBC_URL`-Override), `AbstractBackendIT` (DB-Integrationstests, Nicht-Web-Kontext),
  `AbstractApiIT` (REST-API-Integrationstests, beide Sicherheitsketten), `Fixtures`

### REST-API v1 + Standort-Token-Auth + WebSocket

Die fachliche HTTP-/WS-Schicht des Backends; alleiniger Datenzugriffspfad der Terminals.

#### Standort-Token-Auth

Package `backend/.../auth/terminal/`:
- `TerminalTokenEntity`/`TerminalTokenRepository` – siehe [02-data-model.md](02-data-model.md)
  (`terminal_tokens`).
- `TerminalTokenService` – erzeugt Tokens (`createToken`: 32 Byte `SecureRandom`, Base64-URL,
  Präfix `elwt_`), prüft sie (`authenticate`: SHA-256-Hash-Lookup, aktualisiert `last_used_at`
  gedrosselt auf höchstens einen Schreibvorgang alle 5 min je Token), widerruft sie (`revoke`).
  Nur der Hash wird gespeichert; das Klartext-Token existiert nur im Rückgabewert von
  `createToken` (`IssuedTerminalToken`).
- `TerminalPrincipal` (record: `tokenId`, `locationId`, `locationName`) +
  `TerminalAuthenticationToken` – der Standort-Kontext im `SecurityContext`.
- `TerminalTokenAuthenticationFilter` – liest `Authorization: Bearer <token>`, prüft über
  `TerminalTokenService`, setzt bei Erfolg die `TerminalAuthenticationToken`; bei fehlendem/
  unbekanntem/widerrufenem Token antwortet der Filter SELBST mit `401` + `ProblemDetail`.
- `TerminalApiSecurityConfig` – eigene, zustandslose `SecurityFilterChain`
  (`securityMatcher(new AntPathRequestMatcher("/api/v1/**"))`, `@Order(1)`, `STATELESS`, CSRF aus).
  Zwei Fallstricke (siehe Klassen-Javadoc): (1) jede `Filter`-Spring-Bean wird zusätzlich global
  registriert – eine `FilterRegistrationBean` mit `setEnabled(false)` unterdrückt das; (2)
  `securityMatcher(String...)` braucht `mvcHandlerMappingIntrospector`, der in den
  `webEnvironment=NONE`-Tests fehlt – der explizite `AntPathRequestMatcher` umgeht das.
- **Header**: `Authorization: Bearer <token>` (Standard-HTTP-Mechanismus, funktioniert unverändert
  für den WebSocket-Handshake) statt eines proprietären Headers.
- **Speicherung/Rotation**: nur der SHA-256-Hash landet in der DB; pro Standort sind beliebig
  viele aktive Tokens gleichzeitig gültig – Rotation ohne Ausfallfenster.
- **Verwaltungspfad**: `TerminalTokenCliRunner` (`@Profile("token-cli")`, siehe
  `application-token-cli.yml`) – Kommandos siehe [04-build-and-run.md](04-build-and-run.md); das
  Klartext-Token wird genau einmal auf `stdout` ausgegeben.

Siehe [ADR 0008](../architecture/0008-api-auth-standort-token-und-admin-session.md).

#### REST-API v1 (`/api/v1/**`, Package `backend/.../api/`)

DTOs (`api/dto/`, Java Records) statt Entity-Serialisierung an der API-Grenze. Fehlerbilder
einheitlich als RFC-7807-`ProblemDetail` über `ApiExceptionHandler` (`@RestControllerAdvice`, nur
für `org.kabieror.elwasys.backend.api`), gespeist aus der `ApiException`-Hierarchie
(`api/exception/`, je Unterklasse HTTP-Status + `type`-URI `urn:elwasys:<slug>` + Titel).
Standort-Scope über `TerminalScopeGuard` durchgesetzt – ein Gerät/eine Ausführung eines ANDEREN
Standorts wird wie unbekannt behandelt (`404`, nicht `403`, um keine Existenz an fremden
Standorten zu verraten). API-Dokumentation: springdoc-openapi (`/v3/api-docs`,
`/swagger-ui.html`, hinter der login-pflichtigen Catch-all-Kette).

| Methode | Pfad | Zweck | Erfolg | Fehler |
|---|---|---|---|---|
| `POST` | `/api/v1/card-login` | Kartenlogin (`CardLoginRequest{cardId}` → `UserDto` inkl. Guthaben) | `200` | `404 card-not-found`, `403 user-blocked`, `403 location-not-allowed` |
| `GET` | `/api/v1/locations/me` | Standort des Tokens (`LocationDto`) | `200` | – |
| `GET` | `/api/v1/devices?userId=` | Geräteliste des Standorts, je Gerät `usableByUser`/`occupied`/gefilterte `programs` (`PermissionService`) + Gateway-Konfigurationsfelder (`fhemName`/`fhemSwitchName`/`fhemPowerName`/`deconzUuid`/`autoEndPowerThreshold`/`autoEndWaitTimeSeconds`) | `200` | `400` (userId fehlt), `404 user-not-found` |
| `GET` | `/api/v1/devices/{id}?userId=` | Einzelgerät (Standort-Scope) | `200` | `404 device-not-found`, `404 user-not-found` |
| `GET` | `/api/v1/devices/overview` | Anonyme Geräteübersicht OHNE `userId` (`DeviceOverviewDto`) | `200` | – |
| `POST` | `/api/v1/executions` | Execution anlegen+starten (`ExecutionStartRequest{userId,deviceId,programId,clientTimestamp?,replay?}`); optionaler Header `Idempotency-Key` | `201 ExecutionDto` | `404 device/program/user-not-found`, `403 user-blocked/location-not-allowed/device-not-usable/program-not-available`, `409 device-occupied`, `402 insufficient-credit` |
| `GET` | `/api/v1/executions/{id}` | Aktueller Stand einer Ausführung | `200` | `404 execution-not-found` |
| `POST` | `/api/v1/executions/{id}/finish` | Reguläres Ende (bezahlt); optionaler Rumpf `ExecutionEndRequest{clientTimestamp}` + `Idempotency-Key`-Header; publiziert ein `ExecutionNotificationEvent` | `200` | `404`, `409 execution-already-finished` |
| `POST` | `/api/v1/executions/{id}/abort` | Vorzeitiger Abbruch (persistenzseitig identisch zu `finish`, eigener Endpunkt für API-Klarheit); wie `finish` mit optionalem Rumpf/Header + `ExecutionNotificationEvent` | `200` | wie `finish` |
| `POST` | `/api/v1/executions/{id}/reset` | Zurücksetzen ohne Abrechnung; optionaler `Idempotency-Key`-Header | `200` | `404` |
| `GET` | `/api/v1/users/{id}/credit` | Guthabenabfrage (NICHT standortgebunden – Guthaben ist personenbezogen) | `200 CreditResponse` | `404 user-not-found` |
| `GET` | `/api/v1/snapshot` | Standort-Snapshot für die Offline-Buchungs-Vorbereitung (`SnapshotDto`) | `200` | – |

Alle Endpunkte (außer der reinen Standort-Selbstauskunft) prüfen Berechtigungen 1:1 über die
Services (`PermissionService`, `PricingService`, `CreditService`, `ExecutionService`) – keine
Duplikation der Fachlogik in der API-Schicht.

**Anonyme Geräteübersicht** (`GET /api/v1/devices/overview`, `DeviceOverviewDto`, Methode
`DeviceController#overview`): bewusst ein eigener Pfad statt `userId` auf `GET /api/v1/devices`
optional zu machen – dessen 400-Vertrag bei fehlendem `userId` bleibt unangetastet. Liefert je
Gerät die Gateway-Konfiguration (fhem/deCONZ), `occupied`, `runningExecutionId` (für den
Wiederaufnahme-Scan) und `lastUserId`/`lastUserName`. Enthält bewusst KEINE
`programs`/`usableByUser`-Felder (die setzen einen bekannten Benutzer voraus) – nach einem
Kartenlogin ruft der Client `GET /api/v1/devices?userId=...` auf.

**Privilegierter Nachbuchungs-Pfad** (`ExecutionStartRequest#replay`): mit `replay=true`
überspringt `POST /api/v1/executions` sämtliche fachlichen Prüfungen (Sperrung/Standort/
Nutzbarkeit/Belegung/Guthaben) und lässt auch negative Salden zu – gedacht für die verzögerte
Offline-Nachmeldung, bei der die Berechtigung bereits am Terminal geprüft wurde. Das Flag ist
ausschließlich über den authentifizierten Terminal-Kanal setzbar (siehe
[ADR 0016](../architecture/0016-offline-replay-haertung.md)).

**Benachrichtigung entkoppelt**: `ExecutionController` ruft `NotificationService` bei
`finish`/`abort` nicht direkt (in-Transaktion) auf, sondern publiziert ein
`ExecutionNotificationEvent`. Ein `ExecutionNotificationListener` versendet die Mail per
`@TransactionalEventListener(AFTER_COMMIT)` erst NACH dem erfolgreichen Commit – ein Rollback der
Buchung unterdrückt damit auch die Mail. Dasselbe Entkopplungs-Muster nutzt der `UiBroadcaster`
für die Portal-Live-Updates.

**Eingabevalidierung deCONZ-UUID**: der Request zum Setzen der deCONZ-UUID
(`UpdateDeconzUuidRequest`) validiert das Feld mit `@NotBlank @Size(max=64)` – leere oder
überlange UUIDs werden mit `400` abgewiesen.

**Idempotenz + Replay** (Package `backend/.../api/idempotency/`): `IdempotencyService#execute`
dedupliziert terminal-gemeldete Execution-Ereignisse über den optionalen Header `Idempotency-Key`
(eine vom Terminal erzeugte UUID pro fachlichem Ereignis) – `POST /api/v1/executions`,
`.../finish`, `.../abort`, `.../reset` akzeptieren ihn alle. Wird derselbe Schlüssel erneut
gesendet, liefert der Endpunkt die ZUERST berechnete Antwort erneut aus, OHNE die fachliche Aktion
(Abrechnung, Execution-Anlage, Benachrichtigung) ein zweites Mal auszulösen. Speicherung in
`terminal_idempotency_keys` (Migration `V4`). Fehlt der Header, verhalten sich alle vier
Endpunkte exakt wie ohne – vollständig additiv/abwärtskompatibel. Mechanik:
- Gleiche Schlüssel werden per `pg_advisory_xact_lock` (zweiargumentige Namensraum-Form,
  Namensraum 2) **serialisiert** – eine zweite, gleichzeitige Anfrage blockiert, bis die erste
  committet hat, und erhält dann die gespeicherte Antwort.
- Der „bereits beendet“-Wächter von `finish`/`abort` (`409 execution-already-finished`) wird
  INNERHALB des Idempotenz-Zweigs geprüft (ein Replay ist bereits `finished=true` und muss die
  gespeicherte `200` erhalten, nicht `409`) – als Regressionstest festgehalten
  (`ExecutionControllerNotificationTest#executionAlreadyFinishedIsCheckedInsideTheIdempotencyBranch`).
  Analog wandern bei `start` alle fachlichen Prüfungen in den Idempotenz-Zweig, damit ein
  zwischenzeitlich geänderter Zustand einen Replay nicht scheitern lässt.
- Ein Schlüssel länger als 64 Zeichen → `400` (`InvalidIdempotencyKeyException`); Wiederverwendung
  desselben Schlüssels für eine ANDERE Vorgangsart → `409` (`IdempotencyKeyReusedException`); ein
  Replay mit zwischenzeitlich gelöschtem `program`/`user` liefert die gespeicherte Antwort statt
  `404` (`program`/`user` werden im „neu“-Zweig aufgelöst, nur `device` bleibt außerhalb, siehe
  `IdempotencyService`-Javadoc).

**Backend-seitiges Locking bei nebenläufigen Buchungen**: gleichzeitige Buchungen desselben
Nutzers bzw. Geräts werden serialisiert. Der Nutzer wird beim Start UND beim Finish per
pessimistischem Zeilen-Lock geladen (`findWithLockById`; der Finish holt die Ausführung frisch
gesperrt über `ExecutionService#getForUpdate`). Der Start-Pfad nimmt zusätzlich ein Advisory-Lock
je Gerät (`AdvisoryLockService`, `pg_advisory_xact_lock`, Namensraum 1 = Gerät), das eine parallele
Doppelbelegung desselben Geräts verhindert. Bewusst KEIN partieller Unique-Index auf „ein aktives
Execution je Gerät“ – die Serialisierung über das Advisory-Lock deckt den Fall ab (siehe
[ADR 0017](../architecture/0017-abrechnungs-integritaet-locking.md)).

**Original-Zeitstempel**: `ExecutionStartRequest#clientTimestamp`/`ExecutionEndRequest#clientTimestamp`
(beide optional, `LocalDateTime`) lassen das Terminal den tatsächlichen Ereigniszeitpunkt
mitschicken statt der Serverzeit beim Empfang – für die Offline-Nachmeldung. `ClientTimestampPolicy`
(Package `offline/`, siehe „Offline-Robustheit“) prüft ihn gegen ein Fenster; liegt er außerhalb,
verwendet der `ExecutionController` die Serverzeit (Protokollhinweis, kein Fehler).

**Standort-Snapshot** (`GET /api/v1/snapshot`, `SnapshotController`, `SnapshotDto` + vier
Teil-DTOs): liefert Nutzer (Kartennummern, Guthaben, Sperr-Status, Gruppen-Id), Geräte (inkl.
Gateway-Konfiguration, zulässige Gruppen-Ids, Programm-Ids), Programme (Preisfelder, zulässige
Gruppen-Ids) und Benutzergruppen (Rabattregel) des Standorts mit Zeitstempel und
`offlineMaxDurationMinutes`. **Enthält bewusst KEINE Passwort-Hashes**. **Scope**:
`users`/`userGroups` sind auf Benutzer/Gruppen beschränkt, die an DIESEM Standort zugelassen sind
(`location.getValidUserGroups()`) – ein Terminal muss offline nur wissen, wer bei ihm einchecken
darf; eine Karte eines dort nicht zugelassenen Nutzers wird offline wie eine unbekannte Karte
behandelt (bewusst konservative Vereinfachung, siehe
[ADR 0010](../architecture/0010-offline-buchungen-am-terminal.md)).

#### Offline-Robustheit

Zwei Stufen: (A) laufende, online gestartete Ausführungen bei einem Backend-Ausfall lokal zu Ende
führen + nachmelden; (B) während des Ausfalls komplett neu gebuchte Ausführungen
(„Offline-Buchungen“), solange der Standort-Snapshot nicht älter als dessen `offline.max-duration`
ist. Siehe [ADR 0010](../architecture/0010-offline-buchungen-am-terminal.md).

**Backend-seitig** (additiv, kein bestehender Endpunkt-Vertrag geändert):
- `locations.offline_max_duration_minutes` (Migration `V5`, Default 60) – pro Standort im
  Portal-Standorte-Dialog editierbar (`LocationFormDialog`; `LocationService#create`/`#update`
  haben eine 3-Arg-Überladung) und über `SnapshotDto#offlineMaxDurationMinutes()` ausgeliefert.
- Package `backend/.../offline/`: `ClientTimestampPolicy` (+ `OfflineProperties`,
  Konfigurationsschlüssel `elwasys.offline.clock-drift-tolerance`, Default `PT5M`) prüft einen vom
  Terminal mitgeschickten `clientTimestamp` gegen das Fenster `[jetzt − (offline.max-duration +
  Toleranz), jetzt + Toleranz]` – außerhalb verwendet der `ExecutionController` die Serverzeit.
  `#isNotificationSuppressed` unterdrückt die `finish`/`abort`-Benachrichtigung, wenn das Ereignis
  selbst älter als `offline.max-duration` ist (ohne Toleranz). Das Deduplizieren wiederholter
  Meldungen läuft über den `Idempotency-Key`-Mechanismus.

**Client-seitig, Package `Client-Raspi/.../offline/`** (Terminal spricht ausschließlich über die
bestehende REST-API v1):
- `OfflineSnapshotStore`: persistiert den zuletzt geladenen `SnapshotDto` als JSON
  (`offline-snapshot.json` im Arbeitsverzeichnis) – neustartfest, unverschlüsselt (keine
  Passwort-Hashes enthalten).
- `OfflineJournal`: neustartfestes, append-only Ereignis-Journal (`offline-journal.jsonl`, eine
  JSON-Zeile je `OfflineJournalEntry` – Start/Ende/Abbruch mit Idempotenz-Schlüssel,
  Original-Zeitstempel, und bei Ende/Abbruch entweder einer echten Backend-Id [Stufe A] oder dem
  Idempotenz-Schlüssel des zugehörigen `START`-Eintrags [Stufe B]). `computeOfflineDebits()`
  leitet das lokal aufgelaufene, vom gecachten Guthaben abzuziehende Offline-Delta direkt aus dem
  Journal her (jeder Ende/Abbruch-Eintrag trägt den lokal berechneten `chargedPrice`, rein
  informativ, nie an das Backend geschickt).
- `OfflinePricing`: 1:1-Portierung von `PricingService` für die Offline-Preisberechnung.
- `OfflineGateway`: zentrale Offline-Entscheidungslogik + Journal-Replay.
  - Kartenlogin/Geräte-Übersicht/Geräte-für-Benutzer/Buchen liefern JEWEILS dieselben Wire-DTOs
    (`UserDto`/`DeviceOverviewDto`/`DeviceDto`) bzw. dasselbe Fehler-Vokabular
    (`ApiException`-Status/Slug) wie der Online-Pfad – der komplette UI-/Modellcode bleibt
    unverändert nutzbar.
  - Fehlt ein nutzbarer (nicht abgelaufener) Snapshot, wird die URSPRÜNGLICHE `ApiException`
    unverändert weitergereicht.
  - `replay()`: überträgt das Journal in Reihenfolge über die idempotenten Execution-Endpunkte.
    Robust gegen Teilfehler: bricht NUR bei einem reinen Kommunikationsfehler
    (`isCommunicationFailure()`) ab (die restlichen Einträge bleiben für den nächsten Versuch
    erhalten); ein FACHLICHER Fehler / „Poison-Entry“ (404/403/402, zwischenzeitlich gelöschtes
    Gerät/Programm, ein unbekannter Eintragstyp) wandert in eine Dead-Letter-Datei, statt den
    Replay dauerhaft zu blockieren. Erfolgreich übertragene Einträge werden einzeln per
    `removeEntry` entfernt; die Paar-Reihenfolge bleibt gewahrt (ein `START` wird erst nachgemeldet,
    wenn seine Terminierung vorliegt). Die Nachmeldung eines offline gebuchten Starts läuft über
    `ApiClient#replayCreateExecution` (`replay=true`); Doppelverarbeitung schützt der
    `Idempotency-Key` (siehe [ADR 0016](../architecture/0016-offline-replay-haertung.md)).
- **Stufe A** (`executions/ExecutionFinisher#executeAction`): der Live-`finish`/`abort`-Aufruf
  wird versucht; scheitert er an einem reinen Kommunikationsfehler, wird die Ausführung lokal
  abgeschlossen und im Journal hinterlegt (kein Fehler-/Retry-UX) – ein echter fachlicher Fehler
  (z. B. 409) löst weiterhin das bestehende Retry-UX aus.
- **Periodischer Hintergrundabgleich** (`ElwaManager#initiate`, eigener
  `ScheduledExecutorService`, Intervall `offline.pollIntervalSeconds`, Default 20s): aktualisiert
  den Snapshot und ruft `OfflineGateway#replay()` auf, sobald das Backend erreichbar ist. Ist das
  Backend beim Terminal-Start nicht erreichbar, aber ein noch nicht abgelaufener Snapshot
  vorhanden, bootet der Client im Offline-Modus statt in den Fehlerzustand.

**Restrisiko**: startet der Client selbst NEU, während er offline ist UND eine Ausführung gerade
läuft, kann diese laufende Ausführung nicht wiederaufgenommen werden (der Wiederaufnahme-Scan
braucht die Backend-Antwort auf `GET /api/v1/devices/overview`/`GET /api/v1/executions/{id}` –
offline liefert der Snapshot bewusst keine Belegungsdaten). Ein enger, dokumentierter Sonderfall
(Terminal-Neustart UND Backend-Ausfall gleichzeitig), kein Verlust bereits gemeldeter Daten.

#### WebSocket-Endpunkt (`/api/v1/terminal-ws`, Package `backend/.../ws/`)

Liegt bewusst unter `/api/v1/**`, damit dieselbe `TerminalApiSecurityConfig`-Kette auch den
Handshake absichert (der Handshake ist zunächst eine normale HTTP-Anfrage und durchläuft den
`TerminalTokenAuthenticationFilter`). `TerminalHandshakeInterceptor` übernimmt danach den bereits
authentifizierten `TerminalPrincipal` aus dem `SecurityContext` in die
`WebSocketSession`-Attribute (`elwasys.terminal.locationId`/`elwasys.terminal.locationName`).

**Nachrichtenformat** (JSON, `TerminalWsMessage`, versioniert über das Feld `v`):
```json
{ "v": 1, "type": "HELLO", "id": "<Korrelations-Id>", "payload": { "clientVersion": "0.4.0" } }
```
`id` wird vom Absender vergeben; eine Antwort trägt dieselbe `id` wie die auslösende Anfrage
(`TerminalWsMessage.inReplyTo`). Unbekannte zusätzliche Felder werden ignoriert
(Vorwärtskompatibilität).

**Nachrichtentypen** (`TerminalWsMessageType`):

| Typ | Richtung | Verhalten |
|---|---|---|
| `HELLO` | Terminal → Backend | Server antwortet `HELLO_ACK` |
| `HELLO_ACK` | Backend → Terminal | Payload `locationId`, `locationName`, `serverTime`, `protocolVersion` |
| `PING` | beide Richtungen | Empfänger antwortet `PONG` (das Backend sendet periodisch `PING`, siehe Heartbeat) |
| `PONG` | beide Richtungen | Aktualisiert intern den „zuletzt gesehen“-Zeitstempel der Verbindung |
| `STATUS_REQUEST` | beide Richtungen | Portal-initiiert (Backend → Terminal, `TerminalMaintenanceService#requestStatus`): Antwort vom Terminal mit `clientVersion`/`startupTime`/`runningExecutionIds`. Terminal → Backend: Server antwortet SELBST `STATUS_RESPONSE` (`locationId`, `locationName`, `connectedSince`, `serverTime`) als reiner Verbindungsbeweis |
| `STATUS_RESPONSE` | beide Richtungen | Antwort des Terminals auf ein portal-initiiertes `STATUS_REQUEST` – über die Korrelations-`id` an die wartende Anfrage zurückgeroutet |
| `LOG_REQUEST` | Backend → Terminal | Portal-initiierte Anfrage (`TerminalMaintenanceService#requestLog`, Admin-Dashboard „Log anzeigen“), kein Payload |
| `LOG_RESPONSE` | Terminal → Backend | Antwort auf `LOG_REQUEST`, Payload `{"lines": [...]}`; über die Korrelations-`id` zurückgeroutet |
| `RESTART_REQUEST` | Backend → Terminal | Portal-initiierte Anfrage (`TerminalMaintenanceService#requestRestart`, Admin-Dashboard „Neustart“), kein Payload |
| `RESTART_RESPONSE` | Terminal → Backend | Bestätigung von `RESTART_REQUEST` – der Admin erfährt zuverlässig, ob der Befehl ankam |
| `ERROR` | beide Richtungen | Protokoll-/Verarbeitungsfehler, `payload.reason` (`malformed-message`/`not-implemented`) |

**Fernwartungs-Vermittlung**: `TerminalMaintenanceService` (Package `ws`) ist die Portal-seitige
Vermittlung für `STATUS_REQUEST`/`LOG_REQUEST`/`RESTART_REQUEST` – sendet mit einer selbst
vergebenen Korrelations-`id`, merkt sich ein `CompletableFuture` je Anfrage und erfüllt es, sobald
`TerminalWebSocketHandler` die passende Antwort mit passender `inReplyTo`-Id empfängt
(`completeIfPending`). `completeIfPending(senderLocationId, …)` prüft zusätzlich, dass die Antwort
vom ADRESSIERTEN Standort stammt – eine Antwort mit passender Korrelations-`id` von einem anderen
Standort kann eine wartende Anfrage nicht fälschlich erfüllen. Ein nicht verbundener Standort
(`TerminalConnectionRegistry#isConnected` false) liefert SOFORT `TerminalNotConnectedException`;
eine Anfrage ohne Antwort innerhalb von 10s liefert `TerminalRequestTimeoutException`.
`requestStatus` ist bewusst NICHT an einen Portal-UI-Knopf angebunden, bleibt aber als getesteter,
aufrufbarer Service verfügbar („läuft der Client“/„laufende Ausführungen“ sind über die
Verbunden-Anzeige bzw. die Geräte-Panels des Admin-Dashboards bereits sichtbar).

**Verbindungsregistry**: `TerminalConnectionRegistry` (in-memory, `Map<locationId, Session>`) –
genau eine aktive Session pro Standort: verbindet sich ein Terminal erneut, wird die alte Session
geschlossen und ersetzt. Die Admin-Dashboard-Toolbar zieht „Verbunden“/„Verbunden seit“ direkt aus
`isConnected`/`connectedSince` (kein Roundtrip nötig).

**Heartbeat**: `TerminalHeartbeatScheduler` (`@Scheduled(fixedRate = 30_000)`) sendet allen
verbundenen Terminals ein `PING` und schließt Verbindungen, deren letztes `PONG` länger als 90
Sekunden zurückliegt. `@EnableScheduling` sitzt auf `TerminalWebSocketConfig`, die bewusst
`@Profile("!token-cli")` trägt (Spring Boots Standard-Scheduler-Thread ist kein Daemon-Thread und
hätte den einmaligen `TerminalTokenCliRunner`-Prozess sonst nie beendet).

**Tests**: `TerminalWebSocketTest` nutzt den JDK-eigenen `java.net.http`-WebSocket-Client gegen
einen echten, per `@SpringBootTest(webEnvironment=RANDOM_PORT)` gestarteten Server (Handshake
ohne/mit ungültigem Token abgelehnt, HELLO/HELLO_ACK, PING/PONG, STATUS_REQUEST/STATUS_RESPONSE,
ein unimplementierter Typ → `ERROR`, sowie die Fernwartungs-Vermittlung mit einem SIMULIERTEN
WS-Client: Status-/Log-/Neustart-Roundtrip, sofortiger Fehler bei nicht verbundenem Standort,
Timeout bei einem verbundenen, aber nicht antwortenden Terminal). Die End-to-End-Abdeckung mit
einem ECHTEN, verbundenen Client liefert `TerminalMaintenanceRealClientE2ETest`
(`@SpringBootTest(webEnvironment=RANDOM_PORT)`, ruft `TerminalMaintenanceService` als echte Bean
auf; der „Client“ ist der echte, gepackte `raspi-client-*-jar-with-dependencies.jar` als Subprozess
mit `-dry`, über `--module-path`/`--add-modules` mit den JavaFX-Plattform-Modulen versorgt). Drei
Testfälle (`@TestMethodOrder`): Status, Log, Restart. Der Restart-Test verifiziert die echte
`RESTART_RESPONSE`-Bestätigung plus dass die WebSocket-Verbindung den Neustart überlebt
(`onClose(restart=true)` baut nicht ab) und danach weiter antwortet – „Neustart“ ist ein
In-Prozess-Reinitialisieren (`ElwaManager#restart()`), kein OS-Prozess-Neustart. Harness:
`Client-Raspi/run-cross-component-e2e.sh` (baut den Client-Jar, bereitet eine frische Postgres-DB
vor, Flyway migriert über den Testkontext, startet die Suite unter `xvfb-run`).
`TerminalMaintenanceRealClientE2ETest` ist über `<excludes>` in `backend/pom.xml` bewusst NICHT
Teil des normalen `mvn test`/`run-backend-tests.sh`-Laufs; `-Dtest=...` in
`run-cross-component-e2e.sh` überschreibt den Ausschluss gezielt.

**Tests der REST-API/Token-Auth**: `TerminalApiSecurityTest` (401 bei fehlendem/unbekanntem/
widerrufenem Token, 200 bei gültigem, Terminal-Token gewährt KEINEN Zugriff auf die
Catch-all-Kette), `TerminalTokenServiceTest` (Erzeugung/Prüfung/Rotation/Widerruf,
Hash-statt-Klartext), `CardLoginControllerTest`, `DeviceControllerTest` (Standort-Scope,
Programm-Filterung, `occupied`/`usableByUser`), `ExecutionControllerTest` (voller Lebenszyklus +
alle Fehlerfälle), `ExecutionControllerNotificationTest`/`ExecutionControllerOfflineReplayTest`
(Idempotenz-/Replay-Fallstricke).

### Benachrichtigungsdienst (Package `backend/.../notification/`)

1:1-Portierung der Benachrichtigungslogik aus dem Alt-Code: das Ende einer Programmausführung
(regulär oder Abbruch) löst E-Mail und Pushover aus; zusätzlich verschickt das Portal
Passwort-Reset-Mails. Aktuelle Kanäle und Empfängerregeln:

| Auslöser | Kanal | Empfängerregel | Methode |
|---|---|---|---|
| Programm regulär beendet | E-Mail | `user.emailNotification` (Spalte `email_notification`) | `NotificationService#notifyExecutionFinished` |
| Programm regulär beendet | Pushover | `pushover_user_key` nicht leer | dieselbe Methode |
| Ausführung abgebrochen | E-Mail / Pushover | wie oben | `NotificationService#notifyExecutionAborted` |
| „Passwort vergessen“ (Selbstbedienung) | E-Mail | Nutzer per E-Mail-Adresse gefunden | `NotificationService#sendPasswordResetEmail` (via `PasswordResetService#requestReset`) |
| Admin setzt neues Passwort | E-Mail | Admin-Aktion im Nutzer-Dialog | `NotificationService#sendNewPasswordEmail` (via `PasswordResetService#resetPasswordByAdminAndNotify`) |

Der elwaApp/Ionic-Push-Kanal des Alt-Codes ist bewusst NICHT umgesetzt (mobile App laut
Auftraggeber nicht relevant; Auftrag explizit auf „SMTP + Pushover“ begrenzt).

**Fallstrick** (im `NotificationService`-Javadoc dokumentiert): `users.push_notification`
(Entity-Feld `isPushNotification()`) ist das Opt-in für den nicht umgesetzten elwaApp-Kanal, nicht
für Pushover. Das Pushover-Opt-in ergibt sich ausschließlich daraus, ob `pushover_user_key` gesetzt
ist.

**Komponenten**:
- `NotificationsProperties` (`@ConfigurationProperties(prefix = "elwasys.notifications")`):
  `enabled` (Default **`false`**), `smtp.sender-address`, `pushover.api-token`,
  `pushover.base-url` (für Tests überschreibbar).
- `NotificationService`: baut Betreff/Kurztext/Langtext wortgleich zum Alt-Code (deutsch, inkl.
  eines bewusst 1:1 übernommenen Alt-Code-Tippfehlers) und löst E-Mail/Pushover aus. Versandfehler
  werden geloggt, brechen aber nie den Aufruf ab.
- E-Mail-Transport: `spring-boot-starter-mail`/`JavaMailSender`, Konfiguration über
  `spring.mail.*` (Mapping zu den Alt-`smtp.*`-Feldern in `application.yml` dokumentiert).
- Pushover-Transport: `PushoverClient` (`java.net.http`, kein Fremd-Client) – Formular-Request 1:1
  aus dem Bytecode der Alt-Bibliothek hergeleitet (Felder `token/user/message/title/url/url_title/
  priority`; `url`/`url_title` wie im Alt-Aufruf fest verdrahtet).
- `ExecutionNotificationEvent`/`ExecutionNotificationListener` – Entkopplung über
  `@TransactionalEventListener(AFTER_COMMIT)` (siehe „Benachrichtigung entkoppelt“ oben).

**Scharfschaltung** (kritisch, Doppelversand-Risiko): `elwasys.notifications.enabled` (Env
`ELWASYS_NOTIFICATIONS_ENABLED`) ist per Default **aus** (abgesichert durch
`NotificationsPropertiesDefaultTest`). Der Controller/Listener kennt den Schalter nicht; das Gating
bleibt vollständig in `NotificationService#dispatch` gekapselt – solange das Flag AUS bleibt, ist
jeder Aufruf ein wirkungsloser No-Op. Das tatsächliche Scharfschalten
(`ELWASYS_NOTIFICATIONS_ENABLED=true` in der Deployment-Konfiguration, z. B.
`deploy/compose/.env`) ist ein operativer Schritt der Produktivumschaltung. Bei einem idempotenten
Replay derselben Execution-Meldung wird die Benachrichtigung NICHT erneut ausgelöst (die fachliche
Aktion wird bei einem Replay gar nicht erneut ausgeführt). Siehe
[ADR 0009](../architecture/0009-zentraler-benachrichtigungsdienst-hinter-flag.md).

**Passwort-Reset-Mails hängen an einem EIGENEN Schalter**: `elwasys.password-reset.enabled` (Default
**`true`**, `PasswordResetProperties`), NICHT `elwasys.notifications.enabled`. Begründung: der
`notifications`-Schalter schützt vor einem Doppelversand bei automatischen, wiederkehrenden
Ereignissen (Programmende) – ein Passwort-Reset ist dagegen eine explizite, interaktive Aktion, für
die es keinen zweiten sendenden Pfad gibt. Der Schalter existiert als Ops-Bremse (Backend ohne
konfigurierten SMTP-Server sauber betreiben). Nutzt denselben `spring.mail.*`-Transport.

**Actuator-Nebenwirkung**: `spring-boot-starter-mail` aktiviert automatisch einen
Mail-Health-Indikator, der ohne konfigurierten SMTP-Server den Health-Endpoint auf `DOWN` zieht –
`management.health.mail.enabled: false` in `application.yml` deaktiviert ihn.

**Tests** (Package `backend/.../notification/`): `NotificationServiceEmailTest` (echter lokaler
Test-SMTP GreenMail, Betreff/Body/Empfänger/Absender byte-genau), `NotificationServicePushoverTest`
(eingebetteter JDK-`HttpServer` als Mock – Methode/Pfad/Content-Type/alle Formularfelder inkl. der
fest verdrahteten Werte; Regressionstest für den `push_notification`-Fallstrick),
`NotificationsPropertiesDefaultTest` (voller Spring-Kontext, beweist `enabled=false`).

### Betrieb: Health-Indicators & geplante Aufgaben

**Betriebs-Health-Indicators**: zwei fachliche Actuator-Indicators liefern ein Alerting-Signal
über den reinen „läuft der Prozess“-Zustand hinaus – `TerminalConnectivityHealthIndicator` (sind
die erwarteten Terminals verbunden?) und `ExpiredExecutionsHealthIndicator` (stauen sich nicht
abgerechnete, abgelaufene Ausführungen?). Beide hängen in einer EIGENEN Health-Gruppe
`/actuator/health/operational` (für das Alerting) – getrennt von den für die Orchestrierung
gedachten Gruppen `/actuator/health/liveness` + `/actuator/health/readiness`, die ein fachliches
Alerting-Signal NICHT enthalten (sonst würde ein Orchestrator z. B. bei fehlenden
Terminal-Verbindungen unnötig neu starten). Alle drei Gruppen liegen unter der Health-`permitAll()`
von `SecurityConfig`.

**`IdempotencyKeyRetentionScheduler`**: täglicher Purge-Lauf, der abgelaufene Einträge aus
`terminal_idempotency_keys` entfernt – Default-Aufbewahrung 30 Tage (konfigurierbar). Hält die
Idempotenz-Tabelle beschränkt, ohne die kurzfristige Replay-Sicherheit anzutasten.

### Portal-UI (Vaadin Flow, Package `backend/.../ui/`)

Admin-Portal als Vaadin-Flow-UI im Backend (siehe
[ADR 0003](../architecture/0003-portal-als-vaadin-flow-im-backend.md)). Feature-Parität mit dem
Alt-Portal ist über eine Playwright-Suite (`backend/e2e/`, P1–P20) nachgewiesen – Details,
Selektor-Strategie und Test-für-Test-Status in [06-ui-tests.md](06-ui-tests.md).

**Abhängigkeiten**: `vaadin-bom`/`vaadin-spring-boot-starter` (Version **24.10.8**) per
BOM-Import. Zwei Ausschlüsse aus `vaadin-spring-boot-starter`:
- `hilla` (TypeScript/React-Gegenstück zu Flow – nicht Auftrag). Ohne diesen Ausschluss wirft
  `RequestUtil#isAllowedHillaView` beim Auswerten der Sicherheitskette eine `IllegalStateException`
  in jedem Testkontext ohne echten, Servlet-Container-gebootstrappten
  `ServletContainerInitializer` (u. a. `SecurityConfigTest`, `AbstractApiIT`-Unterklassen,
  `webEnvironment=MOCK`).
- `collaboration-engine` (kommerzielles Vaadin-Pro-Add-on, hier nirgends genutzt).

**Vaadin-Lizenzpflicht im Dev-Modus** (Restrisiko, siehe
[ADR 0019](../architecture/0019-ap6-vaadin-lizenz-restrisiko.md)): Vaadin 24.10.x verlangt beim
ersten `VaadinServlet#init()` im Dev-Modus einen Online-Lizenzcheck gegen vaadin.com (die 24-Linie
gilt als kostenpflichtige „Extended Maintenance“). Eine Sandbox ohne Netzwerkzugriff auf vaadin.com
lässt `mvn spring-boot:run` (Dev-Modus) am Servlet-Start scheitern. Der Produktionsmodus-Build
(`mvn -f backend/pom.xml package -Pproduction`) läuft dagegen grün durch: Vaadin erkennt, dass für
diese rein aus Standard-Flow-Komponenten bestehende UI kein eigener Frontend-Build nötig ist, und
liefert ein vorgefertigtes Bundle aus den `vaadin-core`-Jars (kein npm/Netzwerkzugriff). Der Server
loggt beim Start dieselbe, aber NICHT fatale `MissingLicenseKeyException`. Die automatisierte
Testsuite ist nicht betroffen (erzwingt `vaadin.productionMode=true` für alle Tests).

**Views/Layouts**:
- `login/LoginView` (Route `/login`, `@AnonymousAllowed`) – Vaadins `LoginForm` mit deutschen
  Texten 1:1 aus dem Alt-Portal (Titel „Login“/„Waschportal“, Felder „Benutzername“/„Passwort“,
  Fehlermeldung „Login fehlgeschlagen“/…). Postet über Spring Securitys Standard-Mechanismus an
  `/login`, authentifiziert über `ElwasysAuthenticationProvider` (inkl. „gesperrte Nutzer werden
  abgewiesen“). Der „Passwort vergessen?“-Knopf ist aktiv und öffnet `PasswordForgotDialog`.
- `RootView` (Route `""`, `@PermitAll`) – leitet nach dem Login abhängig von der Rolle weiter
  (`AuthenticationContext#hasRole("ADMIN")`): Administratoren zu `AdminDashboardView`, alle anderen
  zu `UserDashboardView`.
- `admin/AdminLayout` (`AppLayout` + `SideNav`) – Navigation Dashboard/Benutzer/Benutzergruppen/
  Programme/Geräte/Standorte. „Standorte“ ist ein eigener Menüpunkt (im Alt-Portal nur über einen
  Dashboard-Dialog erreichbar) – eine vom Auftraggeber gewünschte UX-Verbesserung, keine
  Funktionsänderung. 6 Views (`AdminDashboardView`, `AdminUsersView`, `AdminUserGroupsView`,
  `AdminProgramsView`, `AdminDevicesView`, `AdminLocationsView`), alle `@RolesAllowed("ADMIN")`.
- `user/UserLayout` (`AppLayout` + `SideNav`, Menüpunkt „Übersicht“); `UserDashboardView`
  (`@RolesAllowed("USER")`) zeigt Guthaben/Übersicht.
- `ResetPasswordView` (Route `/reset-password?key=<token>`, `@AnonymousAllowed`) – Setzen des neuen
  Passworts.
- `component/UserMenuBar` – gemeinsame Kopfzeile (Name des angemeldeten Benutzers +
  aufklappbares Menü „Einstellungen“/„Passwort ändern“/„Logout“, `AuthenticationContext#logout()`).
- `component/PlaceholderView` – gemeinsame Basis von Platzhalter-Views.

**Security-Integration** (`backend/.../auth/SecurityConfig`): statt `formLogin` jetzt
`http.with(VaadinSecurityConfigurer.vaadin(), c -> c.loginView(LoginView.class).anyRequest(...authenticated))`.
Das bindet `LoginView` als Login-Ziel, gibt Login-Route + Vaadin-interne statische Ressourcen für
nicht angemeldete Anfragen frei und aktiviert Vaadins routenweise `NavigationAccessControl` (wertet
`@RolesAllowed`/`@PermitAll`/`@AnonymousAllowed` aus). Der `AuthenticationManager`
(`ElwasysAuthenticationProvider`) bleibt derselben Kette zugeordnet. Terminal-API-Kette
(`TerminalApiSecurityConfig`, `/api/v1/**`) und WebSocket-Endpunkt sind UNBERÜHRT (eigene,
niedrigere `@Order`-Kette). Zwei nicht offensichtliche Fallstricke (Javadoc
`SecurityConfig`/`AbstractBackendIT`):
1. Vaadins Spring-Autokonfigurationsklassen (`SpringBootAutoConfiguration`,
   `SpringSecurityAutoConfiguration`, `VaadinScopesConfig`) sind nicht auf
   `@ConditionalOnWebApplication` eingeschränkt und versuchen auch in den
   `webEnvironment=NONE`-Tests einen `WebApplicationContext` zu autowiren – `AbstractBackendIT`
   schließt alle drei explizit aus (`spring.autoconfigure.exclude`).
2. `VaadinSecurityConfigurer` braucht einen echten `ServletContext`-Bean – `@ConditionalOnWebApplication`
   sitzt daher NUR auf dieser einen Bean-Methode (nicht auf der ganzen `SecurityConfig`-Klasse,
   sonst hätte `TerminalApiSecurityConfig` seinen `HttpSecurity`-Bean verloren).

**Push / Live-Updates zwischen Sessions**: echte Cross-Session-Live-Updates über Domain-Events +
Vaadin Push.
- **Ereignis-Infrastruktur** (Vaadin-freies Package `backend/.../events/`): `DomainEvent` – `sealed`
  Marker-Interface, `permits` listet alle 7 Ereignistypen explizit auf. 7 Records:
  `UserChangedEvent(userId)`, `UserGroupChangedEvent(userGroupId)`, `DeviceChangedEvent(deviceId)`,
  `ProgramChangedEvent(programId)`, `LocationChangedEvent(locationId)`, `CreditChangedEvent(userId)`,
  `ExecutionChangedEvent(executionId, deviceId, userId)`.
- **Auslösung in der Service-Schicht** (nicht in der UI, damit API-ausgelöste Änderungen dieselben
  Events feuern): alle 7 Fachlogik-Services bekamen einen `ApplicationEventPublisher` und
  publizieren nach jeder erfolgreichen Änderung das passende Ereignis, unmittelbar nach dem
  `repository.save`/`.delete`. Da die Auslösung in `ExecutionService`/`CreditService` selbst liegt,
  feuern Terminal-API-Aufrufe automatisch dieselben Ereignisse wie ein Klick im Portal. Zwei
  bewusste Vereinfachungen: `ExecutionService#stopExecution` (nur intern von `finishExecution`
  aufgerufen) publiziert selbst nicht; `UserGroupService#setValidLocations`/`-Devices`/`-Programs`
  publizieren jeweils nur ein `UserGroupChangedEvent`.
- **`UiBroadcaster`** (`backend/.../ui/push/`, klassisches Vaadin-„Broadcaster“-Muster):
  Spring-`@Component`, hört über `@TransactionalEventListener(fallbackExecution=true)` zu
  (Default-Phase `AFTER_COMMIT` – ein zurückgerollter Service-Aufruf löst KEINEN Push aus).
  `register(UI, Consumer<DomainEvent>)` merkt sich Aufrufer-`UI` + Listener und liefert eine
  `Registration`; Verteilung ruft `ui.access(() -> listener.accept(event))` – Zustellungsfehler
  werden geloggt statt die übrigen Listener zu blockieren.
- **Vaadin Push aktiviert**: `backend/.../ui/ElwasysAppShell implements AppShellConfigurator`,
  annotiert `@Push(value=PushMode.AUTOMATIC, transport=Transport.WEBSOCKET_XHR)` – WebSocket
  bevorzugt, Fallback auf lang laufende XHR-Requests. `flow-push` (Atmosphere) hängt bereits
  transitiv an `vaadin-spring`.
- **Security-/Pfad-Koexistenz**: Vaadins Push-Endpunkt läuft über die normale
  Vaadin-Servlet-Zuordnung, der Terminal-WebSocket liegt unter dem disjunkten Pfad
  `/api/v1/terminal-ws` (eigene `@Order(1)`-Kette) – keine Pfad-Kollision.
  `VaadinSecurityConfigurer` erkennt Vaadin-interne Anfragen (über
  `HandlerHelper#isFrameworkInternalRequest`) und lässt sie unabhängig vom Anmeldestatus durch; die
  eigentliche Autorisierung bleibt Sache der einzelnen Views (`@RolesAllowed` usw.) – der Push-Kanal
  transportiert nur bereits serverseitig autorisierte Updates.
- **Views abonniert** (Muster überall identisch): `onAttach` → `registration =
  broadcaster.register(ui, listener)`; `onDetach` → `registration.remove()` + Feld auf `null`
  (verhindert Session-Leaks). `AdminDashboardView` reagiert auf
  `DeviceChangedEvent`/`ExecutionChangedEvent` mit gezieltem Nachladen NUR des betroffenen
  Geräte-Panels (`refreshDevice(deviceId)` schlägt das Panel in einer
  `Map<Integer, VerticalLayout>` nach und baut den Panel-Inhalt in-place neu auf); ein
  `LocationChangedEvent` oder ein Ereignis für ein noch nicht angezeigtes Gerät fällt auf ein
  vollständiges `loadData()` zurück. Die 5 Stammdaten-Grids laden bei ihrem `*ChangedEvent`
  vollständig neu; `AdminUsersView` zusätzlich bei `CreditChangedEvent`/`ExecutionChangedEvent`
  (Guthaben-Spalte + Warndreieck-Icon). `UserDashboardView` abonniert
  `CreditChangedEvent`/`ExecutionChangedEvent`, gefiltert auf die EIGENE Benutzer-Id
  (`concernsOwnUser`, ein Java-21-Pattern-Matching-`switch`).

**Build-Konfiguration** (`backend/pom.xml`): Default-`mvn package` bleibt schnell (nur
`vaadin-maven-plugin:prepare-frontend`, kein npm-Bundling); das Profil `production`
(`mvn package -Pproduction`) baut zusätzlich das produktive Frontend-Bundle (`build-frontend`,
`vaadin.productionMode=true`). Die Surefire-Konfiguration erzwingt `vaadin.productionMode=true` für
ALLE Testläufe dieses Moduls – verhindert, dass ein Test mit echtem eingebettetem
Servlet-Container Vaadins Dev-Modus (inkl. Lizenzcheck) startet.

**Portal-Tests** (Package `backend/.../ui/`): bewusst KEIN Test über einen echten eingebetteten
Servlet-Container, der eine Vaadin-UI-Route rendert (könnte in dieser Sandbox nicht grün laufen).
Stattdessen:
- `VaadinPortalSecurityTest` (`webEnvironment=MOCK`): geschützte Routen (`/`, `/admin`) leiten nicht
  angemeldete Anfragen um; Formular-Login authentifiziert gültige Zugangsdaten UND weist gesperrte
  Nutzer ab; Logout leitet zur Login-Seite um.
- `RouteAccessAnnotationsTest` (ohne Spring-Kontext/DB): beweist die
  `@AnonymousAllowed`/`@PermitAll`/`@RolesAllowed`-Zuordnung. Der Test scannt den Classpath über das
  `ui`-Paket (`ClassPathScanningCandidateComponentProvider`) und fordert von jeder gefundenen View
  eine explizite Absicherung (`@RolesAllowed`/`@PermitAll`), während `@AnonymousAllowed` für neu
  hinzukommende Views verboten ist (abgesehen von `LoginView`/`ResetPasswordView`) – so fällt jede
  künftig versehentlich ungesicherte View automatisch durch.

Ein vollständiger, per Browser/JS getriebener Login-Durchstich bleibt der Playwright-E2E-Suite
vorbehalten (siehe [08-test-plan.md](08-test-plan.md), P18).

#### Stammdaten-Views

Die 5 Admin-Views (`AdminUsersView`, `AdminUserGroupsView`, `AdminDevicesView`,
`AdminProgramsView`, `AdminLocationsView`) sind je ein Vaadin `Grid` mit Symbolleiste „Neu“ und
Zeilen-Aktionen (Bearbeiten/Löschen), die einen modalen `Dialog` (`ui/admin/dialog/`) öffnen –
fachliche Nachfolger der Alt-`components/*Window`: `UserFormDialog`, `UserGroupFormDialog`,
`DeviceFormDialog`, `ProgramFormDialog`, `LocationFormDialog`. Die gemeinsame Komponente
`ui/component/ConfirmDeleteDialog` nutzt Vaadins `ConfirmDialog` mit „Ja“/„Nein“. Mehrfachauswahlen
(Standorte/Geräte/Programme/Benutzergruppen) sind als `MultiSelectComboBox` umgesetzt.

**Services** (jeweils mit den Alt-Fenstern als fachlicher Referenz):
- `UserService` – Anlegen/Bearbeiten/weiches Löschen. Löschen entspricht 1:1
  `Common.User#setDeleted`: der Benutzername wird mit `#del<id>#` präfixiert, damit er (UNIQUE auf
  `users.username`) sofort wieder frei ist. Kartennummer-Mehrfachvergabe wirft
  `DuplicateCardIdException`. `#updateOwnSettings` ändert bewusst NUR Email/Email-Benachrichtigung/
  Pushover-Key.
- `UserGroupService` – Löschen entspricht 1:1 `Common.UserGroup#delete`: Benutzer der gelöschten
  Gruppe werden einer anderen Gruppe zugewiesen (`findFirstByIdNotOrderByIdAsc`); gibt es keine,
  wirft der Service `EntityInUseException`. `UserGroupEntity` besitzt KEINE Sammlung ihrer
  Standorte/Geräte/Programme (die drei `@ManyToMany`-Relationen sind unidirektional von
  `LocationEntity`/`DeviceEntity`/`ProgramEntity` aus modelliert) –
  `setValidLocations`/`-Devices`/`-Programs` togglen von der jeweils anderen Tabellenseite aus.
- `DeviceService` – regulär gelöschte Geräte behalten ihren Bezug per `ON DELETE SET DEFAULT` auf
  ein virtuelles Gerät. `delete` wirft `EntityInUseException`, wenn am Gerät noch eine laufende oder
  abgelaufene Ausführung hängt (`start IS NOT NULL AND finished=false`). `#findByLocation`
  (Nachfolger von `DataManager#getDevicesToDisplay`).
- `ProgramService` – Löschen mit Wächter: ein Programm, das noch mindestens einem Gerät zugeordnet
  ist, wird NICHT gelöscht (obwohl `device_program_rel` `ON DELETE CASCADE` trägt).
- `LocationService` – Standorte sind ein eigener Menüpunkt. Löschen mit Wächter analog zu
  `ProgramService` (ein Standort mit zugeordneten Geräten wird nicht gelöscht) – jetzt als
  expliziter Admin-Vorgang statt der impliziten `DataManager#removeUnusedLocations`-Hintergrundaktion.

Neue Repository-Methoden: `UserGroupRepository#findFirstByIdNotOrderByIdAsc`,
`DeviceRepository#findAllByOrderByNameAsc`/`#findByPrograms_Id`,
`ProgramRepository#findAllByOrderByNameAsc`.

**Tests** (Package `backend/.../service/`): `UserServiceTest`, `UserGroupServiceTest` +
`UserGroupServiceDeleteGuardTest` (Mockito-Unit-Test für den Randfall „keine andere Gruppe mehr“),
`DeviceServiceTest`, `ProgramServiceTest`, `LocationServiceTest`.

#### Dashboard, Guthaben, UsersDashboard

**Admin-Dashboard** (`AdminDashboardView`): zeigt je Standort dessen Geräte mit „Frei“/„Besetzt“
(direkt aus der laufenden `ExecutionEntity` in der DB abgeleitet, kein Client-Kontakt), bei einer
laufenden Ausführung zusätzlich Programm, Benutzer und Restzeit (Restzeit ist eine zusätzliche
Information, im Alt-Dashboard nicht vorhanden), sowie je Gerät die Ausführungshistorie
(Datum/Benutzer/Dauer/Preis, laufende/abgelaufene Zeile hervorgehoben über
`Grid#setPartNameGenerator`). Die Historie wird lazy paginiert (`CallbackDataProvider` gegen
`ExecutionService.getExecutions(device, Pageable)`/`countExecutions`, stabiler Sortierschlüssel
`start DESC, id DESC`). Je Standort eine Kopfzeile mit Verbindungsstatus
(„Verbunden“/„Nicht verbunden“, aus `TerminalConnectionRegistry`) plus den Knöpfen „Log anzeigen“
(`LogViewerDialog`) und „Neustart“ (beide über `TerminalMaintenanceService`; für einen nicht
verbundenen Standort derselbe Fehlertext wie der Alt-Code). Der Service `DashboardService` kapselt
die gesamte Datenbeschaffung (Standorte → Geräte → Status, `LocationStatus`/`DeviceStatus`) ohne
Vaadin-Abhängigkeit, damit die Live-Updates dieselbe Abfrage wiederverwenden.

**Guthaben aufladen/Historie** (in `AdminUsersView` über zwei Zeilen-Aktionen):
- `CreditTopUpDialog` – Einzahlung/Auszahlung (Radiobuttons, Default „Einzahlung“), Betrag,
  Buchungstext (vorbelegt mit „Einzahlung/Auszahlung vom Waschportal von <Admin>“, 1:1 wie im
  Alt-Fenster). Ruft AUSSCHLIESSLICH `CreditService#inpayment`/`#payout` auf – erzeugt strukturell
  immer nur einen NEUEN Buchungssatz, ändert/löscht nie einen bestehenden. Eine Auszahlung über das
  Guthaben hinaus zeigt dieselbe Fehlermeldung wie im Alt-Portal (`NotEnoughCreditException`). Der
  „Buchen“-Knopf trägt `setDisableOnClick(true)` (Doppelklick-Schutz); die Guthaben-Spalte der
  Benutzerliste bezieht ihre Werte gebündelt über `CreditService.getCredits(List)`.
- `CreditHistoryDialog` – rein lesende Liste (Datum/Betrag/Buchungstext, neueste zuerst) über
  `CreditService#getAccountingEntries` – bietet bewusst KEINE Bearbeitungs-/Löschfunktion.

Zwei `CreditService`-Lesemethoden: `getAccountingEntries` (= `DataManager#getAccountingEntries`)
und `getLastInpayment` (= `DataManager#getLastInpayment`, auch vom UsersDashboard genutzt).

**UsersDashboard** (`UserDashboardView`): zeigt eigenes Guthaben (`CreditService#getCredit`) und
letzte Einzahlung (`getLastInpayment`) als Kacheln sowie die vollständige eigene Buchungshistorie
(`getAccountingEntries`) in einer Tabelle. **Datenisolation**: der angezeigte Benutzer kommt
ausschließlich aus dem `ElwasysUserPrincipal` der Session
(`AuthenticationContext#getAuthenticatedUser`), nicht aus einem Pfad-/Query-Parameter – ein
Nicht-Administrator kann strukturell nur eigene Daten sehen.

**Unveränderlichkeit der Buchungen**: strukturell sichergestellt – `CreditAccountingEntryEntity`
bietet keine Setter für gespeicherte Buchungen; alle drei UI-Bausteine (`CreditTopUpDialog`,
`CreditHistoryDialog`, `UserDashboardView`) rufen ausschließlich lesende Methoden oder die
anfügenden `CreditService#inpayment`/`#payout` auf, nie ein Update/Delete auf `credit_accounting`.

**Tests** (Package `backend/.../service/`): `DashboardServiceTest` (freies/besetztes/abgelaufenes
Gerät, Standort-Gruppierung, Historie), `CreditServiceAccountingHistoryTest` (Sortierung neueste
zuerst, unveränderte Werte bei wiederholtem Abruf, `getLastInpayment` ignoriert Auszahlungen).

#### Dialoge/Funktionen

**Eigenes Passwort ändern** (`ChangePasswordDialog`, erreichbar über das Benutzermenü in
`UserMenuBar`): der Vaadin-freie Service `PasswordService#changeOwnPassword` prüft das aktuelle
Passwort über `PasswordVerificationService` (akzeptiert Argon2id- und SHA1-Bestandshashes) und
setzt das neue Passwort IMMER im Argon2id-Format (`#encodeNew`). `PasswordService` erzwingt zentral
eine Mindestlänge von 8 Zeichen (`MIN_PASSWORD_LENGTH`, sonst `PasswordTooShortException`) – sowohl
beim administrativen `setNewPassword` als auch beim `changeOwnPassword`. **Konsequenz** (in
`PasswordService`-Javadoc dokumentiert): ein über das neue Portal gesetztes Argon2id-Passwort kann
sich in einem SHA1-vergleichenden Alt-Portal nicht mehr anmelden – eine bewusste Einschränkung, die
erst greift, wenn ein Nutzer AKTIV das neue Portal nutzt (siehe
[ADR 0007](../architecture/0007-passwort-hashing-argon2id-mit-rehash.md)).

**UserSettings** (`UserSettingsDialog`): Email, Email-Benachrichtigung (Checkbox), Pushover-Key –
dieselben drei Felder wie im Alt-Fenster, über `UserService#updateOwnSettings`.

**Passwort per Email zurücksetzen**:
- Selbstbedienung: `LoginView`s „Passwort vergessen?“-Knopf öffnet `PasswordForgotDialog`
  (Email-Eingabe → `PasswordResetService#requestReset`).
- Öffentliche Route `ResetPasswordView` (`/reset-password?key=<token>`, `@AnonymousAllowed`) zum
  Setzen des neuen Passworts.
- Admin-seitig: `UserFormDialog` hat die Checkbox „Sende dem Benutzer per Email ein neues
  Passwort“ → `PasswordResetService#resetPasswordByAdminAndNotify`.
- `PasswordResetService`: **Schlüssel-Speicherung** nutzt die bestehenden Bestandsspalten
  `users.password_reset_key`/`password_reset_timeout` (Flyway-Baseline `V1`) statt einer neuen
  Migration. **Format**: 24 `SecureRandom`-Bytes, Base64-URL ohne Padding (32 Zeichen) statt des
  Alt-Formats (`SHA1` über `Math.random()`) – kryptographisch stärker. **Gültigkeit**: 2 Stunden
  (`elwasys.password-reset.token-validity`). **Reset-URL**:
  `<elwasys.password-reset.portal-base-url>/reset-password?key=<token>` – über eine
  Konfigurationseigenschaft gebaut (robuster hinter einem Reverse Proxy). **Schalter**:
  `elwasys.password-reset.enabled` (Default AN). **Härtung**: der öffentliche
  Selbstbedienungs-Pfad ist neutralisiert – eine unbekannte Adresse führt zu einem stillen Abbruch
  mit einer neutralen Einheitsmeldung (keine Existenz-Auskunft nach außen); der Versand teilt sich
  den `RateLimiter`-Singleton als Cooldown (erst NACH erfolgreichem Versand markiert). Die Adresse
  wird über `findByEmailIgnoreCase…` als `List` aufgelöst und ALLE Treffer werden angeschrieben.

**ExpiredExecutions** (`ExpiredExecutionsDialog`): in `AdminUsersView` öffnet ein Warndreieck-Symbol
(nur für nicht gesperrte Benutzer mit `ExecutionService#hasExpiredExecutions`; Gesperrt-Icon geht
vor Warndreieck) den Dialog mit allen abgelaufenen, nicht abgerechneten Ausführungen
(`getExpiredExecutions`). Je Zeile „Abrechnen“ (`ExecutionService#finishExecution`) oder „Löschen“
(`ExecutionService#delete`), zusätzlich „Alle abrechnen“. „Löschen“ verlangt einen
Bestätigungsdialog; „Abrechnen“/„Alle abrechnen“ tragen `setDisableOnClick(true)`
(Doppelklick-Schutz gegen Doppelabrechnung).

**Tests**: `PasswordServiceTest` (inkl. Migration eines SHA1-Bestandshashes beim Ändern),
`PasswordResetServiceTest` (echter SMTP-Mock GreenMail – Token-Erzeugung/-Gültigkeit/
-Einmalverwendung, unbekannte Email, Admin-Reset), Tests in `ExecutionServiceTest`
(`getExpiredExecutions`, `delete`), `RouteAccessAnnotationsTest` (`ResetPasswordView` ist
`@AnonymousAllowed`).

**Live-Update-Tests**: `UiBroadcasterTest` (reiner Unit-Test ohne Spring-Kontext – `UI#access` per
Mockito synchron simuliert: Verteilung an registrierte Listener einzeln + an mehrere gleichzeitig,
Abmelden), `DomainEventsTest` (echter Spring-Kontext über `AbstractBackendIT`/`Fixtures` – je ein
Test pro Service-Ereignisquelle, ein Execution-Lebenszyklus-Test [create→start→finish erzeugt
mindestens 3 `ExecutionChangedEvent`s mit derselben `deviceId`], ein Abmelde-Test über den vollen
Stack). Surefire-Konvention beachten: Testklassen enden auf `*Test` (die Standard-Includes sind
`**/*Test.java`, `**/*IT.java` ist der Failsafe/`mvn verify`-Konvention vorbehalten – in diesem Modul
nur die abstrakten Basisklassen `AbstractBackendIT`/`AbstractApiIT`).

## Historie

- **2026-07-23** — Betriebs-Health-Indicators (`/actuator/health/operational` neben
  `liveness`/`readiness`) und `IdempotencyKeyRetentionScheduler` ergänzt
  ([Worklog AP6](../worklog/2026-07-23-ap6-deployment-betrieb-cutover.md)).
- **2026-07-23** — Portal-Performance/CRUD-Härtung: paginierte Dashboard-Historie,
  `CreditService.getCredits(List)`, `setDisableOnClick`-Doppelklick-Schutz, Löschwächter auf
  `DeviceService`/ExpiredExecutions, regex-freie Kartensuche, classpath-scannender
  `RouteAccessAnnotationsTest`
  ([Worklog AP5](../worklog/2026-07-23-ap5-portal-performance-crud.md)).
- **2026-07-22** — Auth-/Eingabe-Härtung: `RateLimiter`-Singleton, Login-Brute-Force-Sperre,
  case-insensitiver Username-Guard, zentrale Passwort-Mindestlänge, deCONZ-UUID-Validierung,
  gedrosseltes `last_used_at`, `completeIfPending`-Absender-Prüfung, neutralisierter
  Passwort-Reset-Pfad ([Worklog AP4](../worklog/2026-07-22-ap4-umsetzung.md) ·
  [ADR 0018](../architecture/0018-ap4-auth-security-entscheidungen.md)).
- **2026-07-22** — Abrechnungs-Integrität: pessimistische Zeilen-Locks + geräte-/schlüsselweise
  `pg_advisory_xact_lock` (Serialisierung nebenläufiger Buchungen und Idempotenz-Replays),
  Benachrichtigung über `ExecutionNotificationEvent`/`AFTER_COMMIT` entkoppelt, `requirePositive`
  ([Worklog AP3](../worklog/2026-07-22-ap3-abrechnungs-integritaet.md) ·
  [ADR 0017](../architecture/0017-abrechnungs-integritaet-locking.md)).
- **2026-07-22** — Terminal-Stabilität: `OfflineJournal` DSYNC + Dead-Letter, Uhren-Plausibilität,
  selbst-reconnectender `DeconzEventListener`, neustartfester WS-Lifecycle, Doppel-Finish-Schutz,
  RFID-Log-Maskierung, `setup.sh`-CSPRNG
  ([Worklog AP2](../worklog/2026-07-22-ap2-terminal-stabilitaet.md)).
- **2026-07-22** — Offline-Replay-Kern: privilegierter `replay=true`-Nachbuchungs-Pfad,
  teilfehler-robuster `OfflineGateway#replay` mit Dead-Letter, Zeitstempel-Invariante `stop ≥ start`
  ([Worklog AP1](../worklog/2026-07-22-ap1-offline-replay-kern.md) ·
  [ADR 0016](../architecture/0016-offline-replay-haertung.md)).
- **2026-07-22** — `Common`-Modul aufgelöst: die 6 verbliebenen Utility-Klassen liegen im
  Client-Raspi-Modul, das Backend bindet `common` nicht mehr (Auth-Parity über den Test-Helfer
  `LegacySha1`); Schema-Konsolidierung (`database/`-Duplikat entfernt, `V1` als einzige Quelle)
  ([Worklog Phase-5-Nachtrag](../worklog/2026-07-22-phase-5-nachtrag-common-und-schema.md)).
- **2026-07-21** — Alt-Portal-Modul (Vaadin 7) und Alt-DB-Rollen/-Spalten/-Tabellen (`V6`–`V10`)
  aus Repo und Schema entfernt; `MaintenanceServerManager`/`LocationManager`,
  `Common.maintenance.*` und die Config-Keys `smtp.*`/`database.*`/`maintenance.*` fallen weg
  ([Worklog Phase 5](../worklog/2026-07-21-phase-5-aufraeumen.md) ·
  [Änderungslog](05-migration-plan.md)).
- **2026-07-21** — Terminal-Cutover: die Terminals sprechen ausschließlich über REST-API v1
  (`api/`, `model/`), die ausgehende Fernwartungs-WebSocket (`ws/`) und den Offline-Pfad
  (`offline/`); HTTP durchgängig `java.net.http`, Benachrichtigung an das Backend abgegeben
  ([Worklog Phase 4](../worklog/2026-07-21-phase-4-terminal-modernisierung.md) ·
  [ADR 0004](../architecture/0004-terminals-ohne-direkt-db-zugriff.md) ·
  [ADR 0005](../architecture/0005-fernwartung-ueber-ausgehende-websocket-verbindung.md) ·
  [ADR 0010](../architecture/0010-offline-buchungen-am-terminal.md)).
- **2026-07-20/21** — Vaadin-Flow-Admin-Portal aufgebaut (Grundgerüst, Stammdaten-Views,
  Dashboard/Guthaben, Dialoge/Fernwartung, Cross-Session-Live-Updates, Playwright-E2E-Portierung)
  ([Worklog Phase 3](../worklog/2026-07-20-phase-3-portal-neubau.md) ·
  [ADR 0003](../architecture/0003-portal-als-vaadin-flow-im-backend.md)).
- **2026-07-20** — Backend-Gerüst: JPA-Entities/Repositories, Kern-Services, Auth (Argon2id +
  SHA1-Migrationspfad), REST-API v1 + Standort-Token-Auth + WebSocket-Endpunkt,
  Benachrichtigungsdienst hinter `elwasys.notifications.enabled`
  ([Worklog Phase 2](../worklog/2026-07-20-phase-2-backend-geruest.md) ·
  [ADR 0002](../architecture/0002-zentrales-spring-boot-backend.md) ·
  [ADR 0008](../architecture/0008-api-auth-standort-token-und-admin-session.md) ·
  [ADR 0009](../architecture/0009-zentraler-benachrichtigungsdienst-hinter-flag.md)).
