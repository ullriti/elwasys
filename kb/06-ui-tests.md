# 06 – UI-Test-Strategie

Ziel: Das **bestehende** Verhalten der Software durch UI-Tests festhalten
(Characterization Tests), bevor umgebaut wird – und dabei die UI besser verstehen.

## Client (JavaFX) – TestFX (Fokus zuerst)

Der Raspi-Client ist eine JavaFX-Anwendung mit FXML-Views und einer klaren State-Machine
(`MainFormState`). Das ist gut testbar mit **TestFX**.

### Werkzeuge
- **TestFX** (`org.testfx:testfx-core`, `org.testfx:testfx-junit5`) – FX-UI-Interaktion &
  Assertions.
- **Monocle** (`org.testfx:openjfx-monocle`) – **headless** Rendering (kein Display nötig),
  ideal für Remote/CI: `-Djava.awt.headless=true -Dtestfx.robot=glass
  -Dtestfx.headless=true -Dprestige.order=sw -Dglass.platform=Monocle
  -Dmonocle.platform=Headless`.
- **JUnit 5** als Test-Runner (Vereinheitlichung vorbereiten).

### Herausforderung: harte Abhängigkeiten
`Main`/`ElwaManager` initialisieren beim Start DB-Verbindung, deCONZ, CardReader,
Maintenance-Server. Für UI-Tests brauchen wir **Isolation**:
- Views/Controller einzeln laden (FXMLLoader auf einzelne `*.fxml`), statt die ganze App.
- Abhängigkeiten (DataManager, ExecutionManager, DevicePowerManager, CardReader) über
  Interfaces/Fakes ersetzen. Einige Interfaces existieren bereits
  (`IDevicePowerManager`, `IDeviceRegistrationService`, `IMainFormStateManager`, …).
- Ggf. einen Test-Modus/`-dry` nutzen (PowerManager ohne Funktion ist bereits vorgesehen).

### Erste Testkandidaten (Client)
1. **State-Machine**: Übergänge in `MainFormStateManager` (SELECT_DEVICE → CONFIRMATION →
   …), inkl. Fehler-/Wait-Zustände. Teilweise ohne echtes Rendering testbar.
2. **Startup-View**: lädt, zeigt korrekten Initialzustand.
3. **DeviceView/ProgramList**: Rendering einer Geräteliste aus Fake-Daten, Auswahl löst
   `onDeviceSelected` → Zustandswechsel aus.
4. **InactivityScheduler**: Auto-Logout/Backlight (es gibt bereits `InactivitySchedulerTest`).
5. **Toolbar-Zustände**: Unknown card / blocked user / location-not-allowed Visualisierung.

## Client (JavaFX) – echtes E2E ✅ (umgesetzt)

Zusätzlich zu den isolierten FXML-Charakterisierungstests gibt es jetzt einen **echten
End-to-End-Test**, der die **komplette Anwendung** (`Main`) headless hochfährt und ihren
realen Startup-Pfad durchläuft: Config-Laden → DB-Verbindung → Gateway-Verbindung →
JavaFX-State-Machine bis `SELECT_DEVICE`.

- **Test**: `ClientAppE2ETest` (`src/test/.../application/`), grün.
- **Gateway**: der projekteigene **`FhemSimulator`** (fake fhem über Telnet) — start-/
  stoppbar gemacht (`start(port)`/`stop()`). Der Client nutzt den fhem-Pfad, weil kein
  deCONZ-Server konfiguriert ist.
- **DB**: lokale PostgreSQL, geseedet aus `database-init.sql` (Location „Default").
- **Config**: temporäres `elwasys.properties` (via `user.dir`-Property); stabile Client-UID
  (`.client-uid`) + Reset der Standort-Registrierung im Setup → idempotent, reihenfolge-
  unabhängig.
- **Runner**: `Client-Raspi/run-client-e2e.sh` (startet PG, seedet DB, `xvfb-run mvn test`).

**Behobener Blocker (Produktions-Bugfix):** `WashguardConfiguration.getDeconzServer()`
machte aus einem leeren Wert `"http://"` (nicht blank) → `ElwaManager.initiate()` wählte
**immer** deCONZ, der dokumentierte fhem-Fallback war toter Code (zudem NPE bei fehlendem
Key). Fix: leer bleibt leer. Damit ist der fhem-Pfad wieder erreichbar und testbar.

## Client (JavaFX) – deCONZ-Simulator + beide Gateways im E2E ✅ (Phase 4 AP1, 2026-07-21)

Bis Phase 4 AP1 lief die komplette Client-E2E-Suite ausschließlich gegen den fhem-Pfad
(`FhemSimulator`). Da laut Auftraggeber-Entscheidung (2026-07-20, siehe kb/05
„Entscheidungen") **beide Gateways** (fhem und deCONZ) im Einsatz bleiben, braucht die
Testharness einen deCONZ-Gegenpart und muss die wichtigsten Szenarien mit **beiden**
Simulatoren nachweisen.

### `DeconzSimulator` (`src/test/.../application/deconzsimulator/`)

Fachliches Gegenstück zu `FhemSimulator`, bildet aber statt eines Telnet-Protokolls die
**REST+WebSocket**-Architektur von deCONZ nach (siehe `Client-Raspi/.../devices/deconz/`
und `doc/deconz`):

- **REST-API** über `com.sun.net.httpserver.HttpServer` (Teil der JDK, keine neue
  Abhängigkeit): `POST /api` (Authentifizierung, liefert ein festes Fake-Token),
  `GET/PUT /api/{token}/config` (WebSocket-Port melden bzw. Pairing/Registrierung –
  Letzteres nur als Stub, siehe unten), `GET/PUT /api/{token}/lights/{id}[/state]`
  (Zustand lesen/schalten). Die JSON-(De-)Serialisierung nutzt bewusst direkt die
  **Produktions-Model-Records** aus `devices/deconz/model/` (statt eigener Test-DTOs), damit
  das Wire-Format garantiert mit dem echten Client-Code übereinstimmt.
- **WebSocket-Server** (`DeconzWebSocketServer`, package-privat): eine minimale, selbst
  geschriebene RFC-6455-Implementierung (Handshake + unmaskierte Text-Frames), **keine neue
  Abhängigkeit** – geprüft wurde, dass für einen reinen WebSocket-*Client* (den der
  Produktionscode über `StandardWebSocketClient`/Tomcats eingebettete WS-Client-
  Implementierung nutzt, transitiv über `spring-boot-starter-websocket` →
  `tomcat-embed-websocket` vorhanden) kein zusätzlicher Server-Unterbau im Projekt existiert;
  da der Client auf dieser Verbindung nie selbst sendet, genügt Handshake + Senden.
- **Geräte**: vier vorregistrierte Lampen `wm1`..`wm4` (Namens-Analogie zu `FhemSimulator`s
  `wm1sw`..`wm4sw`), deren Id zugleich der in `devices.deconz_uuid` zu seedende Wert ist.
- **Leistungsmessung**: nicht automatisch – ein Test ruft `sendPowerMeasurement(uuid, watt)`
  gezielt auf (sendet ein `sensors`-WebSocket-Event mit `uniqueid = uuid + "-power"`, passend
  zum `startsWith`-Präfixvergleich in `DeconzDevicePowerManager`).
- **Geräte-Registrierung**: laut Auftrag nur so weit modelliert, wie es der Buchungs-/
  Auto-Ende-Ablauf braucht – das ist **gar nicht**, da Registrierung ein separater,
  manueller Admin-Vorgang ist (`DeviceListEntry`-Zahnrad), der in keinem der vier
  Pflichtszenarien auftritt. `PUT config` (Pairing) ist daher nur ein Stub (200 OK, kein
  Geräte-Scan); `sendDeviceAddedEvent(uuid)` steht für einen künftigen, dedizierten Test von
  `DeconzRegistrationService` bereit, wird aber von AP1 nicht genutzt.

### Drei neue E2E-Testklassen (deCONZ-Pendants zu bestehenden fhem-Tests)

**Organisationsentscheidung**: statt die bestehenden fhem-Tests zu parametrisieren, gibt es
**eigene Testklassen** je Pflichtszenario – dieselbe reale Anwendungslogik
(`ElwaManager`/`DeconzApiAdapter`/`DeconzEventListener`/`DeconzDevicePowerManager`, gewählt
von `ElwaManager.initiate()`, sobald `deconz.server` gesetzt ist) wird durchlaufen, aber die
bewährte fhem-Suite bleibt unangetastet und ein gatewayspezifischer Regressionsfall schlägt
gezielt nur in einer der beiden Klassen fehl:

| Test | fhem-Pendant | Testplan | Deckt |
|---|---|---|---|
| `ClientUsageDeconzE2ETest` | `ClientUsageE2ETest` | C2–C5 | Karten-Login, Geräteliste, Gerät buchen, Programmstart – inkl. Assertion, dass die simulierte deCONZ-Lampe tatsächlich eingeschaltet wurde |
| `ClientAutoEndDeconzE2ETest` | `ClientAutoEndE2ETest` | C11 | Auto-Ende – geht über das fhem-Pendant hinaus: statt sich nur auf den eingebauten „0 W beim Start"-Fallback zu verlassen, schickt der Test einen echten **Über-Schwellwert**-Messwert (muss den geplanten Auto-Stopp abbrechen – verifiziert durch Warten über die Wartezeit hinaus, Ausführung muss noch laufen) und danach einen echten **Unter-Schwellwert**-Messwert (muss das tatsächliche Ende auslösen) – beweist die volle `DeconzEventListener → DeconzDevicePowerManager → ExecutionManager`-Pipeline für „sensors"-Events |
| `ClientAbortExecutionDeconzE2ETest` | `ClientAbortExecutionE2ETest` | C12 | Abbruch – inkl. Assertion, dass die simulierte Lampe beim Abbruch wieder ausgeschaltet wird |

Alle drei grün, siehe „Fortschritt“ unten für die Gesamt-Testzahlen.

### `ClientSmallUiSmokeE2ETest` – erste Abdeckung für `ui/small` (320×240)

Bis AP1 deckte die E2E-Suite ausschließlich `ui/medium` (800×480) ab. `ui/small` bleibt laut
Auftraggeber im Einsatz (kb/05) und bekommt jetzt eine Smoke-Test-Klasse.

- **UI-Größenwahl**: `Main#start` prüft `Main.applicationInterfaceType` (falls nicht via
  CLI-Schalter `-xsDisplay`/`-mdDisplay` gesetzt, wird die Bildschirmbreite der primären
  `Screen` herangezogen: `< 500px` → klein). Der Test setzt das **statische Feld direkt**
  vor `FxToolkit.setupApplication(Main.class)`, um unabhängig von der tatsächlichen
  Xvfb-Auflösung zuverlässig die kleine UI zu erzwingen.
- **Getestet**: App startet mit 320×240-Szene und erreicht `SELECT_DEVICE`; ein Gerät lässt
  sich anwählen; ein Karten-Scan schließt den Login ab und erreicht
  `CONFIRMATION_READY`.
- **Befund (dokumentiert, kein Bug, keine Code-Änderung)**: `ui/small` hat einen
  **umgekehrten Ablauf** gegenüber `ui/medium`: dort meldet man sich per Karte an und wählt
  danach ein Gerät; in `ui/small` wählt man **zuerst** ein Gerät (`onDeviceSelected` prüft
  nur `Device#isEnabled`, keinen angemeldeten Benutzer) und scannt **danach** die Karte auf
  der Bestätigungsseite (`onCardDetected` reagiert nur in den
  `CONFIRMATION_*`-Zwischenzuständen). Der Test bildet diesen tatsächlichen Ablauf ab, statt
  ihn an `ui/medium` anzugleichen.
- **Fallstrick beim Schreiben des Tests**: `MainForm.fxml` (small) setzt bei zwei Panes ein
  explizites `id="..."` (`confirmation-pane`, `program-pane`), das vom sonst aus `fx:id`
  automatisch abgeleiteten CSS-Id **überschrieben** wird – `lookup("#confirmationPane")`
  liefert dadurch `null`, richtig ist `lookup("#confirmation-pane")`. Betrifft nur diese
  beiden Panes; alle anderen `fx:id`s ohne expliziten `id=`-Override funktionieren wie
  erwartet als Lookup-Selektor.

## Client (JavaFX) – Testharness startet seit Phase 4 AP4 ein echtes Backend

Seit dem Terminal-Cutover (Phase 4 AP4, 2026-07-21, siehe kb/05-migration-plan.md) sprechen
die `*E2ETest`-Klassen über die REST-API v1 statt direkt mit der Datenbank. Alle drei
Client-Harness-Skripte (`run-ui-tests.sh`, `run-client-e2e.sh`, `run-cross-component-e2e.sh`)
bringen dafür jetzt ein echtes, laufendes Backend mit:

- **`Client-Raspi/ci-support/start-test-backend.sh`** (neu, `source`d von allen drei
  Skripten): baut den Backend-Jar, startet EIN Backend-Prozess für den gesamten Testlauf
  (Flyway migriert dieselbe Test-Datenbank, die `database-init.sql` initialisiert hat),
  seedet per `token-cli` genau einen Standort-Token für „Default" und exportiert
  `ELWASYS_TEST_BACKEND_URL`/`ELWASYS_TEST_BACKEND_TOKEN` (gelesen von
  `application.TestBackend`, siehe dortiger Klassen-Javadoc). Ein `EXIT`-Trap stoppt das
  Backend und löscht dessen Log wieder, wenn das Skript endet.
- **Kernproblem gefunden und behoben**: das Skript baute den Test-Backend-Jar ursprünglich mit
  einem einfachen `mvn package -DskipTests` (ohne `-Pproduction`). Damit lief Vaadin im
  Entwicklungsmodus, dessen `VaadinServlet#init()` einen ONLINE-Lizenzcheck gegen vaadin.com
  auslöst (siehe kb/04-build-and-run.md „Achtung seit Phase 3 AP1" für die Grundlagen dieses
  Sandbox-Befunds). Der Check hängt hier mangels Netzwerkzugriffs fest und wirft nach **~60
  Sekunden** eine `LicenseException`, die den kompletten Spring-Kontext (Tomcat-Connector +
  Hikari-Pool) einreißt – **ca. 60-70 Sekunden, NACHDEM** der Actuator-Health-Endpunkt schon
  „UP" gemeldet hatte. Das erklärte exakt das beobachtete Symptom: `run-client-e2e.sh` (28
  Tests in 16 Klassen) brach ab der 7. Testklasse (`ClientSmallUiSmokeE2ETest`, kumulierte
  Laufzeit knapp unter der 60s-Marke) deterministisch mit `ApiException: Das Backend ist
  nicht erreichbar` ab – jede Klasse davor lief noch gegen das (kurzzeitig) lebende Backend,
  jede danach traf auf ein bereits abgestürztes. **Kein Speicherleck, kein Ressourcenleck,
  keine Reihenfolge-Abhängigkeit** – eine reine Zeitbombe. Fix: `start_test_backend()` baut
  jetzt mit `-Pproduction` (dieselbe bereits für `backend/e2e/scripts/start-backend.sh`
  etablierte Umgehung – im Produktionsmodus loggt Vaadin beim Start nur eine
  `MissingLicenseKeyException`, bricht aber nicht ab). Verifiziert: `run-client-e2e.sh` 2×
  hintereinander vollständig grün (28/28).
- **`run-cross-component-e2e.sh`** brauchte einen eigenen Nachzieh-Fix: `ElwaManager#initiate()`
  verdrahtet seit dem Cutover IMMER einen `ApiClient` (auch wenn `ClientMaintenanceConnectionE2ETest`
  inhaltlich nur den transitional auf dem Alt-DB-Pfad verbliebenen Maintenance-Kanal prüft) –
  das Skript seedete aber nie einen Token. Nachgezogen (`source
  ci-support/start-test-backend.sh` wie in den beiden anderen Skripten).
- **Bekannter, isoliert reproduzierbarer Timing-Flake**: `InactivitySchedulerTest` (Timer-
  basierte Charakterisierungstests, unabhängig vom Backend-Cutover) ist in einem vollen
  `run-ui-tests.sh`-Lauf vereinzelt mit einem `testMultipleExecutions`-Fehlschlag aufgetreten;
  im isolierten Einzellauf (`./run-ui-tests.sh InactivitySchedulerTest`) sofort wieder 4/4
  grün. Kein Zusammenhang mit dem Backend-Cutover, keine Regression – bereits vor AP4 als
  gelegentlicher Timing-Flake bekannt (siehe Phase-4-AP2-Änderungslog-Eintrag).

**Testzahlen Phase 4 AP4 (2026-07-21, historisch)**: `run-client-e2e.sh` **28/28** (2×
reproduziert), `run-ui-tests.sh` **46/46** (18 Unit-/Charakterisierungstests + 28 E2E),
`run-cross-component-e2e.sh` (Alt-TCP-Protokoll, `ClientMaintenanceConnectionE2ETest`) **3/3**.

## Client (JavaFX) – Cross-Component-/Fernwartungs-Suite umgestellt (Phase 4 AP5, 2026-07-21)

Mit der Fernwartungs-Umkehr (siehe kb/05-migration-plan.md „Arbeitspakete Phase 4" AP5,
kb/03-modules.md „Ausgehende Fernwartungs-Verbindung") spricht der Client-Raspi das Alt-TCP-
Protokoll nicht mehr – `application/MaintenanceServerManager` und `configuration/
LocationManager` sind aus `Client-Raspi/src/main` entfernt, damit auch der bisherige
Cross-Component-Test `ClientMaintenanceConnectionE2ETest` (verband sich gegen einen echten
`Common.MaintenanceServer` als „Portal"): **entfernt**, ersetzt durch
`backend/src/test/java/.../ws/TerminalMaintenanceRealClientE2ETest.java` (siehe
kb/03-modules.md für die volle Beschreibung des Testaufbaus – Backend-Spring-Kontext mit
echter `TerminalMaintenanceService`-Bean als „Portal", ein echter, gepackter Client-Raspi-Jar
als Subprozess als „Terminal", Status/Log/Restart-Roundtrips über den echten WS-Kanal).

**Auswirkung auf die bestehenden Client-Zahlen**: da die 3 alten Cross-Component-Tests
(Log/Status/Restart) aus `Client-Raspi/src/test` (Klassenname endete auf `E2ETest`, daher
sowohl in `run-ui-tests.sh`s ungefiltertem `mvn test` als auch in `run-client-e2e.sh`s
`-Dtest='*E2ETest'`-Filter mitgezählt) ersatzlos entfernt sind (ihr Nachfolger lebt jetzt im
`backend`-Modul, einem eigenen Testlauf), sinken beide Zahlen um 3:
- `run-ui-tests.sh`: **46 → 43/43** grün (keine sonstige Änderung – 18 Unit-/
  Charakterisierungstests + 25 E2E).
- `run-client-e2e.sh`: **28 → 25/25** grün (2× reproduziert).
- Neue, eigenständige Suite: `Client-Raspi/run-cross-component-e2e.sh` (gleicher Dateiname/
  Pfad, komplett neuer Inhalt – siehe unten) treibt jetzt `TerminalMaintenanceRealClientE2ETest`
  im `backend`-Modul an: **3/3** grün (Status/Log/Restart), 2× reproduziert.

**`run-cross-component-e2e.sh` (Phase 4 AP5, neuer Inhalt)**: installiert Common, baut den
Client-Raspi-Jar (`mvn package -DskipTests`, KEIN Backend-Jar-Build/-Start nötig – der
Backend-Spring-Kontext wird vom JUnit-Test selbst über `@SpringBootTest(webEnvironment=
RANDOM_PORT)` gestartet, siehe kb/03-modules.md), löst die JavaFX-Plattform-Module aus dem
lokalen Maven-Repo auf (`--module-path`/`--add-modules javafx.controls,javafx.fxml,
javafx.web` – ein Standard-JDK kann ein `java -jar` dieses Application-Subclass-Fat-Jars sonst
nicht starten: `Error: JavaFX runtime components are missing`, siehe Skript-Kommentar für die
volle Begründung), bereitet eine frische, leere Postgres-Testdatenbank vor (analog zu
`backend/run-backend-tests.sh` – Flyway migriert sie über den Testkontext, kein
`database-init.sql`-Seeding nötig, der Test legt Standort/Token selbst über die echten
Repositories an) und startet die Suite unter `xvfb-run` (`mvn -f backend/pom.xml test
-Dtest=TerminalMaintenanceRealClientE2ETest`) – der reale Client-Subprozess braucht ein
Display, `-Dtest=...` überschreibt gezielt den in `backend/pom.xml` konfigurierten
Standard-Ausschluss dieser Testklasse aus dem normalen `mvn test`-Lauf (siehe
kb/03-modules.md).

## Offline-Robustheit (Phase 4 AP6, 2026-07-21)

Neue Tests für die C15-Nachfolger-Szenarien (Backend nicht erreichbar, laufende Ausführung
lokal beenden + nachmelden, Offline-Buchungen, Zeitfenster, Replay-Idempotenz), siehe
kb/05-migration-plan.md „Konzeptskizze: Offline-Buchungen am Terminal“ und
kb/03-modules.md „Offline-Robustheit (AP6)“.

- **Neuer Test-Helfer `BackendProxy`** (`Client-Raspi/src/test/.../application/`, reine
  JDK-TCP-Weiterleitung, analog `FhemSimulator`): der Client zeigt statt direkt auf
  `TestBackend.url()` auf diesen lokalen Proxy; `goOffline()`/`goOnline()` machen „Backend
  nicht erreichbar“ für einen einzelnen Test gezielt simulierbar, ohne das für die ganze
  Suite gemeinsam genutzte Test-Backend anzufassen. `goOffline()` schließt zusätzlich
  bereits offene Verbindungen aktiv (inkl. eines vom `java.net.http`-Verbindungspool
  warmgehaltenen Keep-Alive-Sockets) – sonst könnte ein Test durch eine zufällig noch
  funktionierende Altverbindung flaken.
- **`ClientOfflineRobustnessE2ETest`** (TestFX, 3 geordnete Tests):
  1. laufende Ausführung übersteht einen Backend-Ausfall (Abbruch schlägt am
     Kommunikationsfehler fehl → lokal abgeschlossen + im Journal hinterlegt → nach
     Wiederverbindung repliziert, Backend-DB zeigt danach `finished=true`).
  2. eine neue Buchung wird offline (Kartenlogin + Berechtigungs-/Guthabenprüfung gegen den
     Snapshot) akzeptiert, existiert beim Backend zunächst NICHT, wird nach Wiederverbindung
     repliziert (inkl. Verbuchung über `credit_accounting`).
  3. nach testweisem Setzen von `locations.offline_max_duration_minutes=0` +
     `OfflineGateway#refreshSnapshot()` wird eine neue Buchung mit demselben Fehlerbild wie
     C15 abgelehnt (`MainFormState.ERROR`) – kein neu erfundenes Fehlerbild.
- **`ClientOfflineReplayIdempotencyE2ETest`** (bewusst KEIN TestFX – kleinste belastbare
  Testform, siehe Klassen-Javadoc): repliziert dasselbe Journal zweimal gegen das echte
  Test-Backend (simuliert einen Terminal-Absturz zwischen erfolgreichem Netzwerkaufruf und
  Journal-Löschung bzw. einen sich überschneidenden zweiten Replay-Versuch) und beweist über
  `executions`-/`credit_accounting`-Zeilenzahlen, dass dabei weder eine zweite Ausführung
  noch eine zweite Guthabenbuchung entsteht.
- **Backend**: `ExecutionControllerOfflineReplayTest` (8 reine Mockito-Unit-Tests, gleiches
  Muster wie `ExecutionControllerNotificationTest` – kein zusätzlicher Spring-Testkontext),
  deckt die Zeitstempel-Toleranz (inkl. standortspezifischer `offline.max-duration`) und die
  Notification-Unterdrückung für zu alte Ereignisse ab.

**Testzahlen Phase 4 AP6 (2026-07-21)**: `run-ui-tests.sh` **43/43 → 47/47**,
`run-client-e2e.sh` **25/25 → 29/29** (2× hintereinander reproduziert), Cross-Component
unverändert **3/3**, Backend-Suite **199/199 → 207/207**, Portal-E2E-Suite weiterhin
**20/20** (P14 inkl. neuem Feld „Offline-Maximaldauer“, unveränderter Save-Roundtrip).

## Alt-Portal (Vaadin 7) – Playwright E2E ⚠️ STILLGELEGT (Phase 3 AP6) und ENTFERNT (Phase 5 AP1)

> **Historisch – existiert nicht mehr.** Diese Suite (`Portal/e2e/`) war der Maßstab für
> P1–P20 und lief bis Phase 3 AP6 in der PR-CI. Sie wurde in Phase 3 AP6 durch
> [`backend/e2e/`](#backend-vaadin-flow--playwright-e2e--umgesetzt-phase-3-ap6) (Vaadin Flow,
> dasselbe Testplan-Inventar P1–P20) fachlich abgelöst und lief seither nicht mehr in der CI
> (`.github/workflows/ci.yml`, Job `portal-legacy-build` baute das Alt-Portal-Modul nur noch,
> ohne Playwright). Mit dem gesamten Alt-Portal-Modul ist auch `Portal/e2e/` in **Phase 5 AP1
> vollständig aus dem Repository entfernt** – dieser Abschnitt bleibt als historische
> Dokumentation stehen (siehe kb/05-migration-plan.md, kb/03-modules.md), ist aber nicht mehr
> lokal ausführbar. Die maßgebliche, aktuelle Portal-E2E-Suite ist ausschließlich
> `backend/e2e/` (siehe unten).

Entscheidung (Auftraggeber, historisch): **Playwright (Node/TypeScript)**. Projekt war unter
`Portal/e2e/` (entfernt, siehe oben).

- **Browser**: vorinstalliertes Chromium (`/opt/pw-browsers/chromium`) via
  `executablePath` – kein `playwright install` nötig.
- **Orchestrierung**: `scripts/start-portal.sh` (idempotent) startet PostgreSQL, seedet die
  `elwasys`-DB aus `database-init.sql`, schreibt `/etc/elwaportal/elwaportal.properties`,
  installiert Common und startet `mvn jetty:run`. Playwright `webServer` wartet auf
  `:8080`, führt die Tests aus und fährt Jetty wieder herunter. Mit `E2E_NO_WEBSERVER=1`
  gegen einen bereits laufenden Server testbar.
- **Vaadin-7-Selektoren**: keine stabilen IDs → Lokatoren über Vaadin-CSS-Klassen
  (`input.v-textfield`, `.v-button`) und sichtbare Captions.

**Letzter Stand vor der Stilllegung: 18/18 grün** (`tests/login.spec.ts`, `admin.spec.ts`,
`admin-crud.spec.ts`, `dashboard.spec.ts`, `user-portal.spec.ts` – deckten P1–P10, P12–P20 ab;
P11 „Gerät aktiv/inaktiv schalten" wurde hier nie umgesetzt, siehe kb/08-test-plan.md).

### Nicht weiter verfolgt
- **Vaadin TestBench** (kommerziell/lizenzabhängig) – verworfen.

## Backend (Vaadin Flow) – Playwright E2E ✅ (umgesetzt, Phase 3 AP6)

Fachlicher Nachfolger der Alt-Suite oben, gegen das neue, ins Backend eingebettete Portal
(`backend/.../ui/`, Vaadin Flow). Projekt unter `backend/e2e/`, analog zu `Portal/e2e/`
aufgebaut (Playwright/Node/TS, gleiches `package.json`-Muster, gleiche Chromium-Bereitstellung).

### Aufbau

- **`backend/e2e/playwright.config.ts`**: `baseURL`/`webServer.url` zeigen auf
  `http://localhost:${E2E_BACKEND_PORT}` (Default **8081** – bewusst NICHT 8080, damit die
  Suite nicht mit einem manuell laufenden Alt-`Portal/e2e`-Server auf demselben Host
  kollidiert). `fullyParallel: false`/`workers: 1` (wie die Alt-Suite – die Tests teilen sich
  Login-Sessions/globalen Zustand). `globalSetup: ./global-setup.ts`.
- **`backend/e2e/scripts/start-backend.sh`** (Playwright `webServer.command`, fachlicher
  Nachfolger von `Portal/e2e/scripts/start-portal.sh`): startet PostgreSQL, legt eine
  **frische, dedizierte** Datenbank an (`elwasys_backend_e2e`, bei jedem Lauf gedroppt+neu
  angelegt – anders als die Alt-Suite, die eine dauerhafte `elwasys`-DB wiederverwendet und
  E2E-Fixtures per Namenspräfix aufräumt; hier ist „frische DB pro Lauf" einfacher UND
  robuster für den Stabilitätsnachweis), baut Common + das Backend-Jar im
  **Produktionsmodus** (`mvn package -Pproduction` – der einzige in dieser Sandbox
  lizenzcheck-freie Build-Weg, siehe kb/05-migration-plan.md „Phase 3 AP2") und startet den
  Jar im Vordergrund auf `SERVER_PORT`. Der Flyway-Baseline-Lauf beim ersten Start seedet
  bereits `admin`/`admin`, die Gruppe „Default" und den Standort „Default" (1:1 aus
  `database-init.sql` übernommen, siehe `V1__baseline_schema_0_4_0.sql`) – kein separates
  Seed-SQL-Skript nötig wie bei der Alt-Suite.
- **`backend/e2e/global-setup.ts`**: seedet die zwei zusätzlichen Nicht-Admin-Testnutzer
  (`e2e_portal_user`, `e2e_pwchange_user`, Passwort „test", SHA1-Alt-Hash – wird vom neuen
  Backend im Parallelbetrieb unverändert akzeptiert, siehe Phase 2 AP3) für P15–P19. Läuft
  **garantiert nach** `webServer`s Bereitschaftsprüfung und **vor** jedem Test (Playwright-
  Ausführungsreihenfolge) – wichtig, weil das Schema erst existiert, sobald die Anwendung
  hochgefahren ist und Flyway migriert hat; ein Seeding aus `start-backend.sh` selbst würde
  mit Playwrights eigenem Bereitschafts-Poll auf denselben Port um dieselbe Ressource
  wettlaufen (Details in den Kommentaren der Datei).
- **`backend/e2e/tests/helpers.ts`**: gemeinsame Lokatoren-Helfer (Login, Navigation,
  ComboBox-Auswahl, Grid-Zeilen-Zugriff, Dialog-Handling) für alle Spec-Dateien.
- **Spec-Dateien** (1:1 zur Alt-Suite benannt): `login.spec.ts` (P1/P2), `admin.spec.ts`
  (P3–P5), `admin-crud.spec.ts` (P6–P14, inkl. dem neu ergänzten P11), `dashboard.spec.ts`
  (P20), `user-portal.spec.ts` (P15–P19).

### Vaadin-Flow-Selektoren (wichtige Erkenntnisse)

- Formularfelder haben – anders als Vaadin 7 – über `<label for="...">` echte, mit dem
  internen `<input>` verknüpfte Labels → **`page.getByLabel('Feldname')`** funktioniert
  zuverlässig (Playwright pierct dabei transparent die internen Shadow-Roots der
  Vaadin-Komponenten). Bei mehrdeutigen Präfixen (z. B. „Name" vs. „Username") `{ exact:
  true }` verwenden.
- Login: `vaadin-login-form` rendert echte `<input name="username">`/`<input
  name="password">` (Spring-Security-Standardnamen) – direkt per
  `input[name="username"]` ansprechbar, kein Component-spezifisches Wissen nötig.
- Dialoge (`Dialog`) rendern als **ein** `<vaadin-dialog-overlay>` pro offenem Dialog, Titel
  in `h2[slot="title"]`.
- RadioButtonGroup-Optionen und Checkboxen haben echte ARIA-Rollen
  (`getByRole('radio', { name })`, `.check()`/`.uncheck({ force: true })`).
- ComboBox-Auswahl: klicken, Text tippen (filtert), `Enter` drücken, Wert verifizieren
  (`pickCombo()` in `helpers.ts`) – funktional identisch zum alten `pickCombo()` für
  Vaadin 7s `v-filterselect`, nur die Selektoren sind neu.
- **`vaadin-grid`-Zellen/Zeilen (wichtigster Fallstrick)**: Zellinhalte werden als
  **Light-DOM**-`<vaadin-grid-cell-content slot="...">`-Elemente gerendert, die Kinder von
  `<vaadin-grid>` selbst sind – NICHT Nachkommen der zugehörigen `<tr>` (sie werden nur über
  das `slot`-Attribut in einen `<td><slot></td>` innerhalb der Zeilen-Shadow-DOM
  „hineingerendert"). `row.locator(...)` von einer per `getByRole('row', { name })`
  gefundenen Zeile aus liefert deshalb **still und leise nichts** – `getByRole('row')`
  funktioniert trotzdem, weil die Accessibility-Tree-Berechnung dem „geflatteten"
  Rendering-Baum folgt, DOM-Traversal aber nicht. Lösung (`gridRowCells()`/
  `gridRowActions()`/`rowActionButton()` in `helpers.ts`): die `slot`-NAMEN aus den
  echten Shadow-DOM-Kindern der Zeile (`row.locator('td slot')`) auslesen und die
  passenden `vaadin-grid-cell-content`-Elemente global über diesen Namen erneut
  lokalisieren.
- Icon-Buttons in Grid-Zeilen (Bearbeiten/Löschen/…) tragen ihren Hinweistext nur als
  `vaadin-tooltip` (`aria-describedby` – trägt zur ARIA-**Beschreibung**, nicht zum
  ARIA-**Namen** bei) → `getByRole('button', { name: 'Bearbeiten' })` findet dort **nichts**.
  `rowActionButton()` adressiert sie deshalb bewusst über ihre Quellcode-Reihenfolge
  (`actionButtons()`-Methode der jeweiligen View), genau wie die Alt-Suite es für Vaadin 7
  tat.

### Test-für-Test-Status (P1–P20, letzter Lauf 2026-07-21)

Alle 20 Testfälle grün, mehrfach (≥ 5 vollständige Läufe, 3 davon über den echten
`webServer`-Pfad inkl. Produktions-Build+Jar-Neustart, 2 gegen einen bereits laufenden
Server) ohne einen einzigen Fehlschlag oder Retry – `retries: 0` in der Config macht Flakes
sofort sichtbar statt sie zu verschlucken.

| Test | Datei | Status |
|---|---|---|
| P1 Login-Seite rendert | login.spec.ts | grün |
| P2 Admin-Login → Dashboard | login.spec.ts | grün |
| P3 Falsches Passwort | admin.spec.ts | grün |
| P4 Logout | admin.spec.ts | grün |
| P5 Navigation aller Admin-Sektionen | admin.spec.ts | grün (inkl. neuem „Standorte"-Punkt) |
| P6 Benutzer anlegen | admin-crud.spec.ts | grün |
| P7 Benutzer sperren | admin-crud.spec.ts | grün |
| P8 Guthaben aufladen | admin-crud.spec.ts | grün |
| P9 Benutzergruppe anlegen | admin-crud.spec.ts | grün |
| P10 Gerät anlegen | admin-crud.spec.ts | grün |
| P11 Gerät aktiv/inaktiv schalten | admin-crud.spec.ts | grün – **neu ergänzt** (in der Alt-Suite nie umgesetzt) |
| P12 Programm anlegen | admin-crud.spec.ts | grün |
| P13 Benutzergruppe löschen | admin-crud.spec.ts | grün |
| P14 Standort bearbeiten | admin-crud.spec.ts | grün – **angepasst**: eigener „Standorte"-Menüpunkt statt Dashboard-Dialog (dokumentierte, gewünschte UX-Änderung, keine Funktionsänderung, siehe kb/05) |
| P15 Nicht-Admin-Dashboard | user-portal.spec.ts | grün |
| P16 Eigenes Passwort ändern | user-portal.spec.ts | grün – **angepasst**: nur der Neu-Portal-Teil (erneuter Login mit neuem Passwort) ist Testgegenstand, siehe kb/05 „Entscheidungen" (Argon2id) |
| P17 Benutzereinstellungen | user-portal.spec.ts | grün |
| P18 Berechtigungen (kein Admin-Zugriff) | user-portal.spec.ts | grün – zusätzlich direkter URL-Zugriffsversuch auf eine Admin-Route geprüft |
| P19 „Passwort vergessen?"-Dialog | user-portal.spec.ts | grün – zusätzlich Fehlerfall (unbekannte Email, kein SMTP konfiguriert) durchgespielt: Dialog bleibt offen, zeigt Fehlermeldung, **stürzt nicht ab** |
| P20 Dashboard-Gerätestatus | dashboard.spec.ts | grün |

**Nebenbefund/Bugfix**: beim Aufbau dieser Suite fiel auf, dass der „Passwort vergessen?"-
Knopf auf der Login-Seite trotz sonst durchgehend eingedeutschter Formulartexte beim
Vaadin-Default „Forgot password" (Englisch) hängengeblieben war (`LoginI18n.Form` hat ein
eigenes `forgotPassword`-Feld, das `LoginView#buildGermanI18n` schlicht nicht gesetzt hatte).
Gefixt (`form.setForgotPassword("Passwort vergessen?")`) – ein winziger, aber echter
1:1-Verhaltensbruch zum Alt-Portal, der ohne den Blick auf die reale Portal-Seite nicht
aufgefallen wäre.

### Kommandos

```bash
cd backend/e2e
npm install                 # einmalig
npx playwright test         # baut Common+Backend (-Pproduction), startet frische DB+Jar, testet
E2E_NO_WEBSERVER=1 npx playwright test   # gegen einen bereits laufenden Server (:8081)
npx playwright show-report  # letzten HTML-Report öffnen
```

Details/Begründungen siehe die Kommentare in `backend/e2e/playwright.config.ts`,
`backend/e2e/scripts/start-backend.sh` und `backend/e2e/global-setup.ts`.

## Ausführung headless (Remote/CI)
- Client-TestFX mit Monocle → **kein** X-Server nötig.
- Falls doch ein Display gebraucht wird: **Xvfb** (virtuelles Framebuffer-Display) als
  Fallback (siehe Cloud-Init).

## Umsetzung (Ist-Stand)

**Harness steht (headless via Xvfb, nicht Monocle).** Entscheidung: Xvfb statt Monocle,
weil Monocle-Versionen fragil zur JavaFX-Version passen müssen; Xvfb ist robust und in der
Umgebung vorhanden.

- `Client-Raspi/pom.xml`: Test-Deps `junit-jupiter 5.10.2`, `testfx-core`/`testfx-junit5`
  `4.0.18`; `maven-surefire-plugin 3.2.5` mit System-Properties
  (`testfx.robot=glass`, `prism.order=sw`, …).
- Tests laufen headless via `xvfb-run mvn test`.
- Convenience-Skript: `Client-Raspi/run-ui-tests.sh [TestKlasse]`.

**Tests (grün, 2/2):**
- `HeadlessFxSmokeTest` – reines FX (keine elwasys-Klassen); beweist die headless-Pipeline.
- `ProgramListEntryFxmlTest` – lädt echtes App-FXML (`ProgramListEntry`, eine der wenigen
  ElwaManager-freien Views) und prüft Controller-Wiring, `#detailBox`, Default-Preisformat.

**Wichtige Erkenntnis (Testbarkeits-Blocker):** `ElwaManager.instance` ist ein eager
Singleton, dessen Konstruktor eine Konfigurationsdatei lädt und bei Fehlen `System.exit(1)`
aufruft (+ startet Maintenance-Server). Jede View, die `ElwaManager` (direkt/transitiv)
berührt, ist daher aktuell **nicht** isoliert testbar. → In Phase 1 sollte diese Kopplung
über Dependency-Injection/Interfaces aufgebrochen werden (bereits vorhandene Interfaces
nutzen). Bis dahin: ElwaManager-freie Views zuerst testen.

## Fortschritt
- [x] TestFX + JUnit5 als Test-Dependencies (Client) ergänzen (Xvfb statt Monocle)
- [x] Ersten headless-Smoke-Test (FX-Startup) zum Laufen bringen
- [x] Erster Charakterisierungstest für echtes App-FXML (ProgramListEntry)
- [x] Echtes Client-E2E (`Main` headless → SELECT_DEVICE, fhem-Simulator + Test-DB)
- [x] Weitere Client-E2E-Flows (Karten-Login, Geräteliste, Programmstart, Login-Varianten,
      Execution-Lifecycle) – C1–C16, siehe kb/08-test-plan.md, Stand der Umsetzung
- [x] Isolierte Charakterisierungstests der State-Machine nach `ElwaManager`-DI-Entkopplung
      (Phase 1, `MainFormStateManager`-Tests)
- [x] Alt-Portal-E2E mit Playwright + Test-DB – P1–P20 (bis auf P11), siehe „Alt-Portal
      (Vaadin 7)" oben; **seit Phase 3 AP6 (2026-07-21) stillgelegt**, durch die Backend-Suite
      unten abgelöst
- [x] **Backend-(Vaadin-Flow-)Portal-E2E mit Playwright** – P1–P20 vollständig (inkl. neu
      ergänztem P11), siehe „Backend (Vaadin Flow)" oben (Phase 3 AP6, 2026-07-21,
      Abnahmekriterium für Phase 3 erfüllt)
- [x] Cross-Component-E2E (P21/P22, Wartungsverbindung) – lief gegen das Alt-TCP-Protokoll bis
      Phase 4 AP4; **seit Phase 4 AP5 (2026-07-21) ersetzt** durch
      `TerminalMaintenanceRealClientE2ETest` (Backend-Modul, echter WS-Kanal), siehe „Client
      (JavaFX) – Cross-Component-/Fernwartungs-Suite umgestellt" oben
- [x] **Phase 4 AP1**: `DeconzSimulator` (REST+WebSocket, siehe „deCONZ-Simulator + beide
      Gateways im E2E" oben) + drei deCONZ-Pendants zu bestehenden fhem-Kernszenario-Tests
      (`ClientUsageDeconzE2ETest`/`ClientAutoEndDeconzE2ETest`/
      `ClientAbortExecutionDeconzE2ETest`, C2–C5/C11/C12) sowie `ClientSmallUiSmokeE2ETest`
      (erste E2E-Abdeckung für `ui/small`, 320×240) – Client-Suite **37/37 → 46/46**
      (`run-ui-tests.sh`), **19/19 → 28/28** (`run-client-e2e.sh`), Cross-Component
      unverändert 3/3
- [x] **Phase 4 AP4**: Client-Cutover auf die REST-API abgeschlossen, Testharness aller drei
      Skripte startet jetzt ein echtes Backend mit (`ci-support/start-test-backend.sh`, siehe
      „Testharness startet seit Phase 4 AP4 ein echtes Backend" oben) – dabei den eigentlichen
      Grund für das anfängliche Wegbrechen der vollen `run-client-e2e.sh`-Suite gefunden und
      behoben (Vaadin-Dev-Modus-Lizenzcheck ohne `-Pproduction`, siehe dort). Client-Suite
      weiterhin **46/46**/**28/28**/**3/3**, Backend-Suite **198/198**, Root-Reactor-Build
      grün
- [x] **Phase 4 AP5**: Fernwartung umgedreht (ausgehende Terminal-WebSocket-Verbindung,
      `MaintenanceServerManager`/`LocationManager` entfernt), Cross-Component-Suite auf den
      echten Backend-WS-Kanal umgestellt (siehe „Client (JavaFX) – Cross-Component-/
      Fernwartungs-Suite umgestellt" oben). Client-Suite **46/46 → 43/43**
      (`run-ui-tests.sh`), **28/28 → 25/25** (`run-client-e2e.sh`, 2× reproduziert) – Rückgang
      um jeweils 3 durch den Wegfall von `ClientMaintenanceConnectionE2ETest`, siehe oben für
      die Begründung. Neue, eigenständige Cross-Component-Suite (`backend`-Modul) **3/3** grün,
      2× reproduziert. Backend-Suite **198/198 → 199/199** (1 neuer `requestStatus`-Unit-Test
      in `TerminalWebSocketTest`; `TerminalMaintenanceRealClientE2ETest` läuft separat, siehe
      oben, und zählt NICHT in dieser Zahl). Root-Reactor-Build grün.
