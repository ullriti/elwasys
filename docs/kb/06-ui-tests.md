# 06 – UI-Test-Strategie

Die Frontends beider Anwendungen sind durch UI-/E2E-Tests abgesichert, die das
nutzer-sichtbare Verhalten festhalten (Characterization Tests): der JavaFX-Terminal-Client
über **TestFX + Xvfb**, das Vaadin-Flow-Portal über **Playwright**. Grundlage sind reale
Anwendungspfade (echte App, echte Simulatoren für die Gateways, echtes Backend, Test-DB).

## Demo-Daten fürs visuelle UI-Prüfen (Profil `demo`)

Ergänzend zu den automatisierten Suiten gibt es einen wiederverwendbaren **Demo-Datenbestand**
zum **visuellen** Prüfen von Portal-/Dashboard-Änderungen, ohne die Daten jedes Mal von Hand
anzulegen.

- **`DemoDataSeeder`** (`backend/.../demo/`, `@Profile("demo")`, `ApplicationRunner`): legt
  beim Start einen zusammenhängenden Beispielbestand an – 4 Benutzergruppen (alle Rabattarten
  `NONE`/`FIX`/`FACTOR`), 3 Standorte, 5 Programme (`FIXED`/`DYNAMIC`), 6 Geräte (fhem/deCONZ,
  eines deaktiviert), 5 Benutzer (inkl. gesperrtem Gast) mit Guthaben, abgeschlossener
  Ausführungshistorie und **laufenden** Ausführungen (Dashboard zeigt „Besetzt" inkl.
  Restzeit). Nutzt durchgängig die echten Repositories/Services (Argon2id-Passwörter,
  `CreditService`-Buchungen, `PricingService`-Preise) → Guthabenstände/Preise sind konsistent.
  Bewusst **keine** Flyway-Migration (Demo-Daten gehören nicht ins Produktivschema);
  **idempotent** über einen Marker-Benutzer (`anna`).
- **Warum ein Seeder statt SQL-Fixture**: die Daten durchlaufen exakt dieselben Wege wie
  produktive Daten und bleiben dadurch konsistent; Login funktioniert mit bekannten
  Argon2id-Passwörtern (admin/admin bzw. `<benutzer>`/`demo`).
- **Start**: `backend/run-demo.sh` (PostgreSQL + Demo-DB `elwasys_demo` + Backend Profil `demo`
  auf :8080, `-Pproduction` wie alle länger laufenden Sandbox-Backends). Portal:
  <http://localhost:8080>. `RESET_DEMO_DB=1` erzwingt einen frischen Bestand. Siehe
  docs/kb/04-build-and-run.md „Demo-Modus".
- **Regressionstest**: `DemoDataSeederTest` (extends `AbstractBackendIT`, `@ActiveProfiles("demo")`)
  prüft Marker-Benutzer/Gruppe/Guthaben, gesperrten Gast, deaktiviertes Gerät, ein „besetztes"
  Dashboard-Gerät und die Idempotenz eines zweiten Laufs (5 `@Test`, Teil von
  `backend/run-backend-tests.sh`). `DemoDataSeederGuardTest` (2 `@Test`) sichert ab, dass der
  Seeder nur unter Profil `demo` läuft.

## Client (JavaFX) – TestFX + Xvfb

Der Raspi-Client ist eine JavaFX-Anwendung mit FXML-Views und einer klaren State-Machine
(`MainFormState`), gut testbar mit **TestFX**.

### Werkzeuge
- **TestFX** (`org.testfx:testfx-core`, `org.testfx:testfx-junit5`, `4.0.18`) – FX-UI-Interaktion
  & Assertions.
- **JUnit 5** (`junit-jupiter 5.10.2`) als Test-Runner.
- **Headless-Ausführung über Xvfb** (virtuelles Framebuffer-Display), getrieben von
  `xvfb-run mvn test`. Xvfb statt Monocle, weil Monocle-Versionen fragil zur JavaFX-Version
  passen müssen; Xvfb ist robust und in der Umgebung vorhanden. `Client-Raspi/pom.xml` setzt im
  `maven-surefire-plugin 3.2.5` die passenden System-Properties (`testfx.robot=glass`,
  `prism.order=sw`, …).
- Convenience-Skript: `Client-Raspi/run-ui-tests.sh [TestKlasse]` (ganze Suite bzw. einzelne
  Klasse).

### Isolation
`Main`/`ElwaManager` initialisieren beim Start DB-/Backend-Verbindung, deCONZ, CardReader,
Maintenance-Kanal. Die `ElwaManager`-Kopplung ist per Dependency-Injection/Interfaces
aufgebrochen (`IDevicePowerManager`, `IDeviceRegistrationService`, `IMainFormStateManager`, …),
sodass Views/Controller einzeln über FXMLLoader geladen und die State-Machine isoliert getestet
werden können. Beispiele:
- `HeadlessFxSmokeTest` – reines FX (keine elwasys-Klassen); beweist die headless-Pipeline.
- `ProgramListEntryFxmlTest` – lädt echtes App-FXML (`ProgramListEntry`) und prüft
  Controller-Wiring, `#detailBox`, Default-Preisformat.
- `MainFormStateManager`-Tests – isolierte Charakterisierung der Zustandsübergänge.
- `InactivitySchedulerTest` – Auto-Logout/Backlight (timer-basiert).

## Client (JavaFX) – echtes E2E

Neben den isolierten FXML-Charakterisierungstests fährt eine E2E-Suite die **komplette
Anwendung** (`Main`) headless hoch und durchläuft ihren realen Startup-Pfad (Config-Laden →
Backend-Verbindung → JavaFX-State-Machine bis `SELECT_DEVICE`). Die `*E2ETest`-Klassen sprechen
über die **REST-API v1** mit einem echten Backend (nicht mehr direkt mit der Datenbank).

- **Basis-E2E**: `ClientAppE2ETest` (`src/test/.../application/`) – Startup bis `SELECT_DEVICE`.
- **Gateway-Simulatoren**: der projekteigene **`FhemSimulator`** (fake fhem über Telnet,
  start-/stoppbar) und der **`DeconzSimulator`** decken beide produktiv genutzten Gateways ab.
- **Test-Backend-Harness**: `Client-Raspi/ci-support/start-test-backend.sh` (von allen drei
  Client-Skripten `source`d) baut den Backend-Jar, startet EIN Backend für den gesamten Testlauf
  (Flyway migriert die Test-DB), seedet per `token-cli` genau einen Standort-Token für „Default"
  und exportiert `ELWASYS_TEST_BACKEND_URL`/`ELWASYS_TEST_BACKEND_TOKEN` (gelesen von
  `application.TestBackend`). Ein `EXIT`-Trap stoppt das Backend und löscht dessen Log. Der Jar
  wird mit **`-Pproduction`** gebaut: im Entwicklungsmodus löst Vaadins `VaadinServlet#init()`
  einen ONLINE-Lizenzcheck gegen vaadin.com aus, der ohne Netzzugriff nach ~60 s eine
  `LicenseException` wirft und den kompletten Spring-Kontext einreißt (Details siehe
  docs/kb/04-build-and-run.md); im Produktionsmodus loggt Vaadin beim Start nur eine
  `MissingLicenseKeyException` und läuft weiter.
- **Config/Idempotenz**: temporäres `elwasys.properties` (via `user.dir`-Property); stabile
  Client-UID (`.client-uid`) + Reset der Standort-Registrierung im Setup → reihenfolge-unabhängig.
- **Runner**: `Client-Raspi/run-client-e2e.sh` (startet PG, seedet DB, baut+startet Backend,
  `xvfb-run mvn test` mit `-Dtest='*E2ETest'`).

`WashguardConfiguration.getDeconzServer()` liefert bei leerem Wert bewusst leer (nicht
`"http://"`), damit `ElwaManager.initiate()` bei fehlendem deCONZ-Server auf den fhem-Pfad
zurückfällt.

### `DeconzSimulator` (`src/test/.../application/deconzsimulator/`)

Fachliches Gegenstück zu `FhemSimulator`, bildet aber statt Telnet die **REST+WebSocket**-
Architektur von deCONZ nach (siehe `Client-Raspi/.../devices/deconz/`):

- **REST-API** über `com.sun.net.httpserver.HttpServer` (JDK, keine neue Abhängigkeit):
  `POST /api` (Authentifizierung, liefert festes Fake-Token), `GET/PUT /api/{token}/config`
  (WebSocket-Port melden bzw. Pairing – Letzteres nur Stub), `GET/PUT
  /api/{token}/lights/{id}[/state]` (Zustand lesen/schalten). Die JSON-(De-)Serialisierung nutzt
  direkt die **Produktions-Model-Records** aus `devices/deconz/model/`, damit das Wire-Format
  garantiert mit dem echten Client-Code übereinstimmt.
- **WebSocket-Server** (`DeconzWebSocketServer`, package-privat): minimale, selbst geschriebene
  RFC-6455-Implementierung (Handshake + unmaskierte Text-Frames), **keine neue Abhängigkeit** –
  da der Client auf dieser Verbindung nie selbst sendet, genügt Handshake + Senden.
- **Geräte**: vier vorregistrierte Lampen `wm1`..`wm4` (Namens-Analogie zu `FhemSimulator`s
  `wm1sw`..`wm4sw`), deren Id zugleich der in `devices.deconz_uuid` zu seedende Wert ist.
- **Leistungsmessung**: nicht automatisch – ein Test ruft `sendPowerMeasurement(uuid, watt)`
  gezielt auf (sendet ein `sensors`-WebSocket-Event mit `uniqueid = uuid + "-power"`, passend zum
  `startsWith`-Präfixvergleich in `DeconzDevicePowerManager`).
- **Geräte-Registrierung**: nur so weit modelliert, wie es der Buchungs-/Auto-Ende-Ablauf
  braucht (Registrierung ist ein separater, manueller Admin-Vorgang). `PUT config` (Pairing) ist
  ein Stub (200 OK); `sendDeviceAddedEvent(uuid)` steht für einen künftigen dedizierten
  `DeconzRegistrationService`-Test bereit.

Für jedes Gateway-Pflichtszenario gibt es **eigene Testklassen** (statt die fhem-Tests zu
parametrisieren) – dieselbe reale Anwendungslogik
(`ElwaManager`/`DeconzApiAdapter`/`DeconzEventListener`/`DeconzDevicePowerManager`) wird
durchlaufen, ein gatewayspezifischer Regressionsfall schlägt gezielt nur in einer der beiden
Klassen fehl:

| Test | fhem-Pendant | Testplan | Deckt |
|---|---|---|---|
| `ClientUsageDeconzE2ETest` | `ClientUsageE2ETest` | C2–C5 | Karten-Login, Geräteliste, Gerät buchen, Programmstart – inkl. Assertion, dass die simulierte deCONZ-Lampe eingeschaltet wurde |
| `ClientAutoEndDeconzE2ETest` | `ClientAutoEndE2ETest` | C11 | Auto-Ende – schickt einen echten **Über-Schwellwert**-Messwert (muss den geplanten Auto-Stopp abbrechen) und danach einen echten **Unter-Schwellwert**-Messwert (muss das Ende auslösen) – beweist die volle `DeconzEventListener → DeconzDevicePowerManager → ExecutionManager`-Pipeline für „sensors"-Events |
| `ClientAbortExecutionDeconzE2ETest` | `ClientAbortExecutionE2ETest` | C12 | Abbruch – inkl. Assertion, dass die simulierte Lampe beim Abbruch wieder ausgeschaltet wird |

### `ClientSmallUiSmokeE2ETest` – Abdeckung für `ui/small` (320×240)

Neben `ui/medium` (800×480) ist `ui/small` durch eine Smoke-Test-Klasse abgedeckt.

- **UI-Größenwahl**: `Main#start` prüft `Main.applicationInterfaceType` (falls nicht via
  CLI-Schalter `-xsDisplay`/`-mdDisplay` gesetzt, entscheidet die Bildschirmbreite: `< 500px` →
  klein). Der Test setzt das **statische Feld direkt** vor `FxToolkit.setupApplication(Main.class)`,
  um unabhängig von der Xvfb-Auflösung zuverlässig die kleine UI zu erzwingen.
- **Getestet**: App startet mit 320×240-Szene und erreicht `SELECT_DEVICE`; ein Gerät lässt sich
  anwählen; ein Karten-Scan schließt den Login ab und erreicht `CONFIRMATION_READY`.
- **Umgekehrter Ablauf gegenüber `ui/medium`**: in `ui/small` wählt man **zuerst** ein Gerät
  (`onDeviceSelected` prüft nur `Device#isEnabled`, keinen angemeldeten Benutzer) und scannt
  **danach** die Karte auf der Bestätigungsseite (`onCardDetected` reagiert nur in den
  `CONFIRMATION_*`-Zwischenzuständen). Der Test bildet diesen tatsächlichen Ablauf ab.
- **Selektor-Fallstrick**: `MainForm.fxml` (small) setzt bei zwei Panes ein explizites
  `id="..."` (`confirmation-pane`, `program-pane`), das den sonst aus `fx:id` abgeleiteten CSS-Id
  **überschreibt** – richtig ist `lookup("#confirmation-pane")`, nicht `lookup("#confirmationPane")`.
  Betrifft nur diese beiden Panes.

### Offline-Robustheit

Tests für die Offline-Szenarien (Backend nicht erreichbar, laufende Ausführung lokal beenden +
nachmelden, Offline-Buchungen, Zeitfenster, Replay-Idempotenz), siehe
docs/kb/03-modules.md „Offline-Robustheit".

- **Test-Helfer `BackendProxy`** (`Client-Raspi/src/test/.../application/`, reine JDK-TCP-
  Weiterleitung, analog `FhemSimulator`): der Client zeigt statt direkt auf `TestBackend.url()`
  auf diesen lokalen Proxy; `goOffline()`/`goOnline()` machen „Backend nicht erreichbar" pro Test
  gezielt simulierbar, ohne das gemeinsam genutzte Test-Backend anzufassen. `goOffline()`
  schließt zusätzlich bereits offene Verbindungen aktiv (inkl. eines vom
  `java.net.http`-Verbindungspool warmgehaltenen Keep-Alive-Sockets).
- **`ClientOfflineRobustnessE2ETest`** (TestFX, 3 geordnete Tests): (1) laufende Ausführung
  übersteht einen Backend-Ausfall (lokal abgeschlossen + im Journal hinterlegt → nach
  Wiederverbindung repliziert, `finished=true` beim Backend); (2) neue Buchung wird offline
  (Kartenlogin + Berechtigungs-/Guthabenprüfung gegen den Snapshot) akzeptiert, existiert beim
  Backend zunächst NICHT, wird nach Wiederverbindung repliziert (inkl. `credit_accounting`);
  (3) nach `locations.offline_max_duration_minutes=0` + `OfflineGateway#refreshSnapshot()` wird
  eine neue Buchung mit demselben Fehlerbild wie C15 abgelehnt (`MainFormState.ERROR`).
- **`ClientOfflineReplayIdempotencyE2ETest`** (bewusst KEIN TestFX): repliziert dasselbe Journal
  zweimal gegen das echte Test-Backend und beweist über `executions`-/`credit_accounting`-
  Zeilenzahlen, dass weder eine zweite Ausführung noch eine zweite Guthabenbuchung entsteht.
- Netzwerkfreie Absicherung derselben Logik als Unit-Tests: `OfflineGatewayReplayTest`
  (5 `@Test`: Paar-Reihenfolge/NPE, Dead-Letter, `clear()`-Race), `OfflineGatewayClockPlausibilityTest`
  (3 `@Test`, Uhren-Plausibilität), Backend `ExecutionControllerOfflineReplayTest` (11 `@Test`,
  Zeitstempel-Toleranz inkl. standortspezifischer `offline.max-duration`, Notification-
  Unterdrückung für zu alte Ereignisse, Replay nach Löschung).

## Client (JavaFX) – Cross-Component / Fernwartung

Die Fernwartung ist eine ausgehende Terminal-WebSocket-Verbindung (siehe
docs/kb/03-modules.md „Ausgehende Fernwartungs-Verbindung"). Der Cross-Component-Nachweis läuft
über **`TerminalMaintenanceRealClientE2ETest`** (`backend/src/test/.../ws/`): der
Backend-Spring-Kontext hält eine echte `TerminalMaintenanceService`-Bean als „Portal", ein
echter, gepackter Client-Raspi-Jar läuft als Subprozess als „Terminal"; Status/Log/Restart-
Roundtrips gehen über den echten WS-Kanal (5 `@Test`).

- **Runner**: `Client-Raspi/run-cross-component-e2e.sh` installiert die Aggregator-Parent-POM
  (`mvn -N install -DskipTests`), baut den Client-Raspi-Jar (`mvn package -DskipTests`; KEIN
  Backend-Jar-Build nötig – der Backend-Kontext wird vom JUnit-Test selbst über
  `@SpringBootTest(webEnvironment=RANDOM_PORT)` gestartet), löst die JavaFX-Plattform-Module aus
  dem lokalen Maven-Repo auf (`--module-path`/`--add-modules javafx.controls,javafx.fxml,
  javafx.web` – ein Standard-JDK startet dieses Application-Subclass-Fat-Jar sonst nicht:
  `Error: JavaFX runtime components are missing`), bereitet eine frische, leere Postgres-Testdatenbank
  vor (Flyway migriert über den Testkontext, der Test legt Standort/Token selbst über die echten
  Repositories an) und startet die Suite unter `xvfb-run` (`mvn -f backend/pom.xml test
  -Dtest=TerminalMaintenanceRealClientE2ETest`) – der reale Client-Subprozess braucht ein
  Display, `-Dtest=...` überschreibt gezielt den `backend/pom.xml`-Standardausschluss dieser
  Testklasse aus dem normalen `mvn test`-Lauf.

## Client – Testfall-Übersicht (C1–C16)

Die Client-E2E-Fälle decken den Kern-Nutzungsablauf, Login-Varianten und den Execution-Lifecycle
ab (Detail-Beschreibung und Fixtures: docs/kb/08-test-plan.md).

| ID | Testfall | Kern-Assertion |
|----|----------|----------------|
| C1 | Startup → Geräteauswahl | State `SELECT_DEVICE` |
| C2 | Geräteliste rendert geseedetes Gerät des Standorts | Gerätename sichtbar |
| C3 | Karten-Login gültiger Benutzer | `registeredUser` gesetzt |
| C4 | Gerät wählen → Programmliste | State `CONFIRMATION`, Programm sichtbar |
| C5 | FIXED-Programm bestätigen (Start) | laufende Execution in DB (Gateway geschaltet) |
| C6 | Unbekannte Karte | `#userInfo` Style `card-unknown`, kein Login |
| C7 | Gesperrter Benutzer | `#userInfo` Style `user-blocked`, kein Login |
| C8 | Benutzergruppe am Standort nicht erlaubt | `#userInfo` Style `location-disallowed` |
| C9 | Zu wenig Guthaben | `#confirmationPane` Style `credit-insufficient`, Start disabled |
| C10 | Auto-Logout nach Inaktivität | `registeredUser` wird null |
| C11 | Auto-Ende: keine Leistung → Ende nach `auto_end_wait_time` | Execution automatisch beendet |
| C12 | Laufende Execution abbrechen | Execution gestoppt |
| C13 | Unterbrochene Execution beim Start fortsetzen | Execution als laufend übernommen |
| C14 | DYNAMIC-Programm: Preisanzeige | „Grundgebühr"/„Zeitpreis" sichtbar |
| C15 | Backend beim Start nicht erreichbar (ohne nutzbaren Offline-Snapshot) → Fehlerzustand | State `ERROR`, Retry möglich (`ClientDatabaseErrorE2ETest`) |
| C16 | Standortfremdes Gerät | erscheint **nicht** in der Liste |

Offline-Nachfolger zu C15 (bei nutzbarem Snapshot startet der Client im Offline-Modus statt in
`ERROR`):

| ID | Testfall | Testklasse |
|----|----------|------------|
| C15a | Backend fällt während laufender Ausführung aus → lokal beendet + Journal → nach Reconnect repliziert (`finished=true`) | `ClientOfflineRobustnessE2ETest` |
| C15b | Backend aus, neue Buchung innerhalb `offline.max-duration` → offline akzeptiert, erst nach Replay beim Backend | `ClientOfflineRobustnessE2ETest` |
| C15c | Snapshot/Zeitfenster abgelaufen, Backend aus → neue Buchung abgelehnt (`ERROR`, Fehlerbild wie C15) | `ClientOfflineRobustnessE2ETest` |
| C15d | Journal-Replay wiederholt → keine Doppelbuchung (Backend dedupliziert über Idempotenz-Schlüssel) | `ClientOfflineReplayIdempotencyE2ETest` |

## Backend-Portal (Vaadin Flow) – Playwright E2E

Die maßgebliche Portal-E2E-Suite läuft gegen das ins Backend eingebettete Vaadin-Flow-Portal
(`backend/.../ui/`). Projekt unter `backend/e2e/` (Playwright/Node/TS).

### Aufbau

- **`backend/e2e/playwright.config.ts`**: `baseURL`/`webServer.url` zeigen auf
  `http://localhost:${E2E_BACKEND_PORT}` (Default **8081**). `fullyParallel: false`/`workers: 1`
  (die Tests teilen sich Login-Sessions/globalen Zustand), `retries: 0` (Flakes sofort sichtbar),
  `globalSetup: ./global-setup.ts`.
- **`backend/e2e/scripts/start-backend.sh`** (Playwright `webServer.command`): startet
  PostgreSQL, legt eine **frische, dedizierte** Datenbank an (`elwasys_backend_e2e`, bei jedem
  Lauf gedroppt+neu angelegt), baut das Backend-Jar über den Root-Reactor (`mvn package -pl
  backend`, löst die Aggregator-Parent-POM mit auf) im **Produktionsmodus** (`-Pproduction` – der
  einzige in dieser Sandbox lizenzcheck-freie Build-Weg) und startet den Jar auf `SERVER_PORT`.
  Der Flyway-Baseline-Lauf seedet bereits `admin`/`admin`, die Gruppe „Default" und den Standort
  „Default" (Seed-Daten aus `V1__baseline_schema_0_4_0.sql`) – kein separates Seed-SQL nötig.
- **`backend/e2e/global-setup.ts`**: seedet die zwei zusätzlichen Nicht-Admin-Testnutzer
  (`e2e_portal_user`, `e2e_pwchange_user`, Passwort „test", SHA1-Alt-Hash – vom Backend
  akzeptiert) für die Benutzer-Portal-Fälle. Läuft garantiert **nach** der `webServer`-
  Bereitschaftsprüfung (Schema existiert erst nach Flyway-Migration) und **vor** jedem Test.
- **`backend/e2e/tests/helpers.ts`**: gemeinsame Lokatoren-Helfer (Login, Navigation,
  ComboBox-Auswahl, Grid-Zeilen-Zugriff, Dialog-Handling).
- **Spec-Dateien**: `login.spec.ts`, `admin.spec.ts`, `admin-crud.spec.ts`, `dashboard.spec.ts`,
  `user-portal.spec.ts`.

### Vaadin-Flow-Selektoren (Selektor-Strategie)

- Formularfelder haben über `<label for="...">` echte, mit dem internen `<input>` verknüpfte
  Labels → **`page.getByLabel('Feldname')`** funktioniert zuverlässig (Playwright pierct die
  internen Shadow-Roots). Bei mehrdeutigen Präfixen (z. B. „Name" vs. „Username") `{ exact: true }`.
- Login: `vaadin-login-form` rendert echte `<input name="username">`/`<input name="password">`
  (Spring-Security-Standardnamen) – direkt per `input[name="username"]` ansprechbar.
- Dialoge (`Dialog`) rendern als **ein** `<vaadin-dialog-overlay>` pro offenem Dialog, Titel in
  `h2[slot="title"]`.
- RadioButtonGroup-Optionen und Checkboxen haben echte ARIA-Rollen (`getByRole('radio', { name })`,
  `.check()`/`.uncheck({ force: true })`).
- ComboBox-Auswahl: klicken, Text tippen (filtert), `Enter`, Wert verifizieren (`pickCombo()` in
  `helpers.ts`).
- **`vaadin-grid`-Zellen/Zeilen (wichtigster Fallstrick)**: Zellinhalte werden als
  **Light-DOM**-`<vaadin-grid-cell-content slot="...">`-Elemente gerendert, die Kinder von
  `<vaadin-grid>` selbst sind – NICHT Nachkommen der zugehörigen `<tr>` (sie werden nur über das
  `slot`-Attribut in einen `<td><slot></td>` innerhalb der Zeilen-Shadow-DOM „hineingerendert").
  `row.locator(...)` von einer per `getByRole('row', { name })` gefundenen Zeile aus liefert
  deshalb **still und leise nichts** – `getByRole('row')` funktioniert trotzdem, weil die
  Accessibility-Tree-Berechnung dem „geflatteten" Rendering-Baum folgt, DOM-Traversal aber nicht.
  Lösung (`gridRowCells()`/`gridRowActions()`/`rowActionButton()` in `helpers.ts`): die
  `slot`-NAMEN aus den echten Shadow-DOM-Kindern der Zeile (`row.locator('td slot')`) auslesen und
  die passenden `vaadin-grid-cell-content`-Elemente global über diesen Namen erneut lokalisieren.
- Icon-Buttons in Grid-Zeilen (Bearbeiten/Löschen/…) tragen ihren Hinweistext nur als
  `vaadin-tooltip` (`aria-describedby` – ARIA-**Beschreibung**, nicht ARIA-**Name**) →
  `getByRole('button', { name: 'Bearbeiten' })` findet dort **nichts**. `rowActionButton()`
  adressiert sie über ihre Quellcode-Reihenfolge (`actionButtons()`-Methode der jeweiligen View).
- **Aktiver Navigationspunkt**: Vaadin markiert das gerade ausgewählte `vaadin-side-nav-item` mit
  dem Attribut **`[current]`** (nicht `[active]`).

### Portal-Design zur Laufzeit (kein kompiliertes Theme)

Das Portal liefert den AdminLTE-Look aus (blauer Header, dunkle Sidebar, gerahmte
Zebra-Tabellen, Login als Karte). Das Styling ist **bewusst kein kompiliertes Vaadin-Theme**,
sondern ein zur Laufzeit in den `<head>` injiziertes Stylesheet (`ElwasysAppShell#configurePage`
→ `backend/src/main/resources/portal-theme.css`) – ein echtes `@Theme`/`@CssImport` würde einen
Frontend-Bundle-Build und damit den Vaadin-Lizenzcheck erzwingen, der in dieser Umgebung
abbricht (siehe docs/kb/05-migration-plan.md). Für die E2E-Suite ist das rein kosmetisch: nur
Farben/Rahmen, keine Texte/Struktur/Selektoren.

### Testfall-Übersicht (P1–P26)

`backend/e2e/tests/` enthält **23 `test()`** (login 2 / admin 3 / admin-crud 12 / dashboard 1 /
user-portal 5); P15/P18 teilen sich ein `test()`.

| ID | Datei | Testgegenstand |
|----|-------|----------------|
| P1 | login.spec.ts | Login-Seite rendert (Titel/Felder/Button) |
| P2 | login.spec.ts | Admin-Login → Dashboard, Admin-Menü sichtbar |
| P3 | admin.spec.ts | Login mit falschem Passwort, bleibt auf Login |
| P4 | admin.spec.ts | Logout → zurück auf Login-Seite |
| P5 | admin.spec.ts | Navigation aller Admin-Sektionen (inkl. „Standorte") |
| P6 | admin-crud.spec.ts | Benutzer anlegen → erscheint in Liste |
| P7 | admin-crud.spec.ts | Benutzer sperren → „Gesperrt" persistent |
| P8 | admin-crud.spec.ts | Guthaben aufladen → Liste aktualisiert |
| P9 | admin-crud.spec.ts | Benutzergruppe anlegen |
| P10 | admin-crud.spec.ts | Gerät anlegen |
| P11 | admin-crud.spec.ts | Gerät aktiv/inaktiv schalten |
| P12 | admin-crud.spec.ts | Programm anlegen (FIXED) |
| P13 | admin-crud.spec.ts | Benutzergruppe löschen (mit Bestätigung) |
| P14 | admin-crud.spec.ts | Standort über den „Standorte"-Menüpunkt bearbeiten, Save-Roundtrip |
| P15 | user-portal.spec.ts | Nicht-Admin-Dashboard („Guthaben"/„Übersicht") |
| P16 | user-portal.spec.ts | Eigenes Passwort ändern → erneuter Login mit neuem Passwort |
| P17 | user-portal.spec.ts | Benutzereinstellungen (E-Mail/Benachrichtigung) persistent |
| P18 | user-portal.spec.ts | Kein Admin-Zugriff (auch direkter URL-Zugriff auf Admin-Route geprüft) |
| P19 | user-portal.spec.ts | „Passwort vergessen?"-Dialog, inkl. Fehlerfall (unbekannte Email, kein SMTP → Dialog bleibt offen, Fehlermeldung, kein Absturz) |
| P20 | dashboard.spec.ts | Dashboard-Gerätestatus „Frei/Besetzt" aus laufender Execution |
| P23 | admin-crud.spec.ts (~Z. 122) | Guthaben-Aufladung lehnt nicht-positiven Betrag ab (#22) |
| P24 | admin-crud.spec.ts (~Z. 147) | Auszahlung > Guthaben blockiert (#50) |
| P25 | admin-crud.spec.ts (~Z. 194) | Benutzer löschen → verschwindet aus Liste (#50) |
| P26 | user-portal.spec.ts (~Z. 57) | Öffentlicher Reset-Link lehnt ungültigen Key ab (#50) |

### Kommandos

```bash
cd backend/e2e
npm install                 # einmalig
npm test                    # baut das Backend (-Pproduction), startet frische DB+Jar, testet
E2E_NO_WEBSERVER=1 npx playwright test   # gegen einen bereits laufenden Server (:8081)
npx playwright show-report  # letzten HTML-Report öffnen
```

Details/Begründungen: die Kommentare in `backend/e2e/playwright.config.ts`,
`backend/e2e/scripts/start-backend.sh` und `backend/e2e/global-setup.ts`.

### Post-Deploy-Smoke-Teilmenge (Rollout-Gate)

Zusätzlich zur vollen Suite gibt es eine **schlanke, strikt READ-ONLY** Teilmenge, die NICHT die
eigene Umgebung hochfährt, sondern gegen eine **bereits laufende, extern deployte** Umgebung
läuft – das Rollout-Gate nach einem Deployment (siehe `deploy/smoke/README.md`).

- **`backend/e2e/playwright.smoke.config.ts`** – eigene Config: **KEIN** `webServer`, **KEIN**
  `globalSetup`, `baseURL` aus `E2E_BASE_URL` (Default `http://localhost:8080`), `testDir:
  './tests-smoke'`, Chromium via `executablePath`, `workers:1`, `retries` per `SMOKE_RETRIES`
  (Default 0).
- **`backend/e2e/tests-smoke/smoke.spec.ts`** – **4** Liveness-Checks, **KEINE** Mutationen,
  keine Annahmen über konkrete Seed-/Produktivdaten: (1) Login-Seite rendert; (2) Admin-Login
  (`SMOKE_ADMIN_USER`/`SMOKE_ADMIN_PASSWORD`, Default admin/admin) → Dashboard/Admin-Side-Nav;
  (3) Kern-Admin-Sektionen Benutzer/Geräte/Programme rendern (nur lesende Navigation,
  `current`-Attribut); (4) Dashboard rendert. Nutzt `login()` aus `tests/helpers.ts`.
- Aufruf: `cd backend/e2e && E2E_BASE_URL=<url> SMOKE_ADMIN_*=… npm run smoke`, oder – mit
  vorgeschaltetem Health-Check als Gesamt-Gate – `deploy/smoke/post-deploy-smoke.sh`
  (`GET /actuator/health` = `status:UP` UND Playwright grün → Exit 0).

## Ausführung headless (Remote/CI)

- Client-TestFX läuft headless via **Xvfb** (`xvfb-run mvn test`); ein X-Server wird über das
  virtuelle Framebuffer-Display bereitgestellt.
- Die Chromium-Bereitstellung für Playwright nutzt das vorinstallierte Chromium
  (`/opt/pw-browsers/chromium`) via `executablePath` – kein `playwright install` nötig.

## Test-Inventar (Gesamtzahlen)

Am Code gezählt:

- **Backend (JUnit)**: **265 `@Test` in 51 Klassen** (57 Testdateien). Die separat gehaltene
  `TerminalMaintenanceRealClientE2ETest` (5 `@Test`, per `backend/pom.xml`-Exclude, eigener
  Harness) läuft nicht in der Standard-Suite → diese umfasst rund **260** Tests
  (`backend/run-backend-tests.sh`).
- **Portal-E2E (Playwright)**: **23 `test()`** in `backend/e2e/tests/` (login 2 / admin 3 /
  admin-crud 12 / dashboard 1 / user-portal 5) zzgl. **4** READ-ONLY-Smoke-`test()` in
  `tests-smoke/`.
- **Client (TestFX/JUnit)**: **71 `@Test`** in 28 Testklassen (40 Testdateien inkl.
  Simulatoren/Helfer).

## Historie

- **2026-07-23** — Test-Inventar code-verifiziert (Backend 265 `@Test`/51 Klassen, Portal-E2E
  23 + 4 Smoke, Client 71 `@Test`); Backend-Standard-Suite zuletzt 259 grün, Portal-E2E grün
  ([Worklog AP6](../worklog/2026-07-23-ap6-deployment-betrieb-cutover.md) ·
  [Worklog AP7](../worklog/2026-07-23-ap7-kb-ueberarbeitung.md)).
- **2026-07-23** — Pre-Launch AP5: `DemoDataSeederGuardTest`, `RouteAccessAnnotationsTest`
  gehärtet, 3 #50-Portal-E2E-Fälle → `backend/e2e/tests/` auf 23 `test()`
  ([Worklog AP5](../worklog/2026-07-23-ap5-portal-performance-crud.md)).
- **2026-07-22/23** — Pre-Launch AP1–AP6 bauten die Suiten aus (Offline-Replay,
  Terminal-Stabilität, Abrechnungs-Integrität, Brute-Force-Schutz, Health-Indikatoren);
  Backend-Suite über die Pakete von 209 auf 259 grün gewachsen
  ([Worklog AP1](../worklog/2026-07-22-ap1-offline-replay-kern.md) ·
  [AP2](../worklog/2026-07-22-ap2-terminal-stabilitaet.md) ·
  [AP3](../worklog/2026-07-22-ap3-abrechnungs-integritaet.md) ·
  [AP4](../worklog/2026-07-22-ap4-umsetzung.md) ·
  [AP6](../worklog/2026-07-23-ap6-deployment-betrieb-cutover.md)).
- **2026-07-22** — Demo-Datenbestand (`DemoDataSeeder`, `run-demo.sh`) für visuelle UI-Checks
  ([Worklog](../worklog/2026-07-22-demo-daten-ui-checks.md)); Portal-Design (AdminLTE-Look) zur
  Laufzeit wiederhergestellt ([Worklog](../worklog/2026-07-22-portal-design.md)).
- **2026-07-21** — Phase 4: `DeconzSimulator` + beide-Gateways-E2E (AP1), Client-Cutover auf die
  REST-API mit echtem Test-Backend inkl. `-Pproduction`-Fix (AP4), Cross-Component auf den
  WS-Kanal umgestellt (`TerminalMaintenanceRealClientE2ETest`, AP5), Offline-Robustheit (AP6);
  laut Worklog Client 47/47 & Backend-Suite 207/207 grün
  ([Worklog Phase 4](../worklog/2026-07-21-phase-4-terminal-modernisierung.md) ·
  [Änderungslog](05-migration-plan.md)).
- **2026-07-21** — Alt-Portal-E2E (`Portal/e2e`, Vaadin 7, letzter Stand 18/18) in Phase 3 AP6
  durch `backend/e2e` (Vaadin Flow) abgelöst und in Phase 5 AP1 mit dem Alt-Portal-Modul aus dem
  Repo entfernt ([Worklog Phase 5](../worklog/2026-07-21-phase-5-aufraeumen.md) ·
  [Änderungslog](05-migration-plan.md)).
- **2026-07-21** — Phase 3 AP6: Portal-E2E P1–P20 gegen das Vaadin-Flow-Portal umgesetzt (inkl.
  neu ergänztem P11 und der Eindeutschung „Passwort vergessen?")
  ([Worklog Phase 3](../worklog/2026-07-20-phase-3-portal-neubau.md)).
- **2026-07-20** — Client-TestFX/Xvfb-Harness + erste Charakterisierungs-/E2E-Tests aufgebaut
  ([Worklog Phase 0/1](../worklog/2026-07-20-phase-0-und-1-fundament.md)).
