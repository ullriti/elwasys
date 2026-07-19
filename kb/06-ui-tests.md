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

## Portal (Vaadin 7) – Playwright E2E ✅ (umgesetzt)

Entscheidung (Auftraggeber): **Playwright (Node/TypeScript)**. Projekt unter `Portal/e2e/`.

- **Browser**: vorinstalliertes Chromium (`/opt/pw-browsers/chromium`) via
  `executablePath` – kein `playwright install` nötig.
- **Orchestrierung**: `scripts/start-portal.sh` (idempotent) startet PostgreSQL, seedet die
  `elwasys`-DB aus `database-init.sql`, schreibt `/etc/elwaportal/elwaportal.properties`,
  installiert Common und startet `mvn jetty:run`. Playwright `webServer` wartet auf
  `:8080`, führt die Tests aus und fährt Jetty wieder herunter. Mit `E2E_NO_WEBSERVER=1`
  gegen einen bereits laufenden Server testbar.
- **Vaadin-7-Selektoren**: keine stabilen IDs → Lokatoren über Vaadin-CSS-Klassen
  (`input.v-textfield`, `.v-button`) und sichtbare Captions.

**Tests (grün, 2/2):** `tests/login.spec.ts`
- Login-Seite rendert (Titel „Waschportal", Benutzer-/Passwortfeld, Login-Button).
- Seed-Admin (`admin`/`admin`) meldet sich an und erreicht das Admin-Dashboard
  (Menüpunkte „Benutzergruppen", „Geräte").

Verifiziert kompletter Stack: PostgreSQL → DB-Seed → Jetty/Vaadin → DB-Verbindung
(DataManager) → Login → Dashboard.

### Nicht weiter verfolgt
- **Vaadin TestBench** (kommerziell/lizenzabhängig) – verworfen.

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
- [ ] Weitere Client-E2E-Flows (Karten-Login simulieren, Geräteliste, Programmstart)
- [ ] Weitere isolierte Charakterisierungstests (Toolbar-Zustände) – benötigt
      Entkopplung von ElwaManager
- [x] Portal-E2E mit Playwright + Test-DB (Login-Smoke-Test grün)
- [ ] Weitere Portal-E2E-Flows (CRUD: Benutzer/Geräte/Programme anlegen)
