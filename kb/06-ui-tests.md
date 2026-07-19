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

## Portal (Vaadin 7) – später

- Option A: **Vaadin TestBench** (kommerziell/lizenzabhängig) – eher nicht.
- Option B: **Playwright/Selenium** End-to-End gegen laufende Jetty-Instanz + Test-DB
  (PostgreSQL via Docker/Testcontainers). Realistischer, aber schwergewichtig (GWT-
  Widgetset-Build nötig).
- Empfehlung: Portal-UI-Tests **nach** dem Client angehen, sobald ein reproduzierbarer
  Portal-Build in der Remote-Umgebung steht.

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
- [ ] Weitere Charakterisierungstests (State-Machine, Toolbar-Zustände) – benötigt
      Entkopplung von ElwaManager
- [ ] Portal-E2E mit Playwright + Test-DB
