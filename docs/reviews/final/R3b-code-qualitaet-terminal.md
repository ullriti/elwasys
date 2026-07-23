# R3b – Code-Qualität Terminal (Client-Raspi/)

> Track R3b der [Spec 0001](../../specs/0001-finale-review.md), Modell Opus, 2026-07-24.
> Beide Hoch-Findings wurden vom Hauptagenten am Code gegenverifiziert
> (`ExecutionManager.java:326` `add` statt `remove`; `DeviceListEntry.java:446-456`
> Fall-Through `DISABLED` → `UNREGISTERED` inkl. `setDisable(false)` – beide bestätigt).

## 1. Gesamturteil

Der Terminal-Code ist über weite Strecken solide und für ein gewachsenes JavaFX-Projekt
überdurchschnittlich gut kommentiert – besonders die neueren Schichten (`offline/`,
`api/`, `ws/`, `devices/deconz/`) sind sauber, defensiv und mit belastbaren
Warum-Kommentaren samt Issue-Referenzen versehen. Die spürbaren Wartbarkeitsrisiken
konzentrieren sich auf den *Altbestand*: die beiden parallelen UI-Bäume `ui/small` und
`ui/medium` mit sehr großen Controllern/State-Managern, den Singleton-Gott `ElwaManager`,
sowie eine Handvoll Vor-Java-21-Idiome (Roh-`Thread`s, `Vector`, Fall-Through-`switch`,
`printStackTrace`). Zwei Befunde sind hoch: eine per Copy-Paste entstandene
Listener-Leak-Stelle und ein Fall-Through im `switch` von `DeviceListEntry` – beide sind
zugleich Qualitäts- *und* latente Verhaltensrisiken (Überschneidung mit R2). Für den
Feldeinsatz ist der Code tragfähig; die Hoch-Findings sollten vorher geprüft, der Rest
als geplanter Refactor eingeplant werden.

## 2. Positives (bewusst hervorgehoben)

- **Offline-Schicht (`offline/OfflineGateway`, `OfflineJournal`, `OfflinePricing`)**:
  durchdachte, defensiv gebaute Logik (Dead-Letter statt Blockade, Paar-Reihenfolge,
  Geister-Execution-Kompensation) mit exzellenten Warum-Kommentaren und Issue-Bezügen.
  Vorbildlich.
- **`api/ApiClient`**: schlank, klar geschichtet, gute Trennung von
  Transport/Parsing/Fehlerübersetzung; die Retry-/`malformedResponse`-Behandlung ist
  präzise begründet.
- **Konsistentes, gehärtetes Reconnect-Muster** in `ws/TerminalWebSocketClient` und
  `devices/deconz/DeconzEventListener` (Stop-Guard, `AtomicBoolean`-Reconnect-
  Serialisierung, exponentielles Backoff) – bewusst identisch gehalten und dokumentiert.
- **`ExecutionFinisher#runGuarded`**: die Zusammenführung von `run()`/`retry()` unter
  einem Lock+`executed`-Wächter ist ein sauberer, gut kommentierter Fix eines
  Nebenläufigkeits-Doppel-Finish.

## 3. Findings

### Aspekt 1 – Duplikate

- **Hoch · `executions/ExecutionManager.java:326`** — Der Listener-Boilerplate (je
  `listenTo…`/`stopListenTo…`-Paar für start/finish/error) ist dreifach dupliziert, und
  genau eine Kopie trägt einen Copy-Paste-Fehler: `stopListenToExecutionStartedEvent`
  ruft `this.startListeners.add(l)` statt `remove(l)`. Ein `DeviceListEntry` (das sich in
  `onTerminate`, Zeile 209, abmelden will) wird damit nie aus der Start-Listener-Liste
  entfernt – über wiederholte Restarts/Re-Inits wächst die Liste an (Listener-Leak,
  mehrfache `onExecutionStarted`-Aufrufe). Klassisches „Duplication is decay".
  **Empfehlung:** `remove(l)` korrigieren *und* die sechs Register/Unregister-Methoden
  über einen kleinen generischen Helfer (`List<L>`-Paar) entdoppeln; Regressionstest
  ergänzen. (Zugleich R2-relevant.)

- **Mittel · `ui/small/MainFormStateManager.java` (Zeilen 269–297, 524–542)** —
  Währungsformatierung wird ~6× inline über
  `NumberFormat.getCurrencyInstance().format(...)` gebaut, obwohl
  `common/FormatUtilities.formatCurrency(...)` existiert und in `ui/medium`
  (`DeviceListEntry`) genutzt wird. Duplizierte und inkonsistente Formatierung.
  **Empfehlung:** durchgehend `FormatUtilities` verwenden.

- **Mittel · `ui/small/MainFormController#onCardDetected` (837–914) vs.
  `ui/medium/MainFormController#onCardDetected` (372–446)** — Der
  Kartenlogin-Fehlerdispatch (`card-not-found`/`user-blocked`/`location-not-allowed` →
  Zustand/Log, inkl. maskiertem Log) ist in beiden UI-Größen nahezu identisch
  reimplementiert. Die zwei parallelen Controller-/State-Manager-Bäume sind laut KB
  bewusst getrennt (320×240 vs. 800×480), aber die reine *Fehler-Slug-→-Aktion*-Abbildung
  ließe sich in einen gemeinsamen Helfer ziehen. **Empfehlung:** geteilte
  `CardLoginOutcome`-Auswertung; UI-Reaktion bleibt je Größe.

- **Niedrig (akzeptabel, benennen) · `api/dto/*` und `offline/OfflinePricing.java`** —
  Die DTO-Records spiegeln 1:1 die Backend-REST-Records, `ProgramType`/`DiscountType`
  und `OfflinePricing` (1:1-Portierung von `backend.service.PricingService`) existieren
  faktisch doppelt zum Backend. Das ist wegen getrennter Deployments und fehlendem
  gemeinsamem Modul **bewusst und vertretbar** (in `03-modules.md` dokumentiert).
  Einziger realer Drift-Punkt: `OfflinePricing` muss bei jeder Änderung an
  `PricingService` von Hand nachgezogen werden – es gibt keinen Parity-Test wie beim
  Alt-SHA1 (`LegacySha1`). **Empfehlung:** einen kleinen Preis-Parity-/Vektortest gegen
  Backend-Erwartungswerte erwägen, damit stiller Drift auffällt.

### Aspekt 2 – Struktur

- **Mittel · `ui/medium/controller/DeviceListEntry.java` (763 Zeilen)** — Gott-Klasse
  einer Gerätekachel: FXML-Binding, 8-Zustands-Enum + `refresh`-Rendering,
  Power-/Registrierungs-Scheduling (eigener `ScheduledExecutorService`),
  Auto-Retry-Scheduling, Fehleranzeige *und* ~30 Property-Accessor-Boilerplate.
  **Empfehlung:** Retry-Scheduling und den Property-Block auslagern; `refresh()`-Stil-
  Setzerei (s. Aspekt 3) in eine Zustandstabelle überführen.

- **Mittel · `ui/small/MainFormController.java` (915) / `ui/small/MainFormStateManager.java`
  (628)** — Sehr groß; der State-Manager baut die komplette Übergangstabelle in tief
  verschachtelten Lambdas in einem Konstruktorpfad auf. Der `updateDevicePane(int)`-Block
  mit `switch(i)` über vier feste Kacheln (633–677) und die hand-gerollte anonyme
  `Runnable`-`init()`-Klasse (430–451) sind schwer zu erweitern. **Empfehlung:** Kacheln
  als Array-getriebene Kollektion statt vier duplizierter `deviceNcontainer`-Felder.

- **Mittel · `application/ElwaManager.java` (593)** — Harter Singleton
  (`public final static instance`) und Gott-Objekt: Konfig-Laden, `ApiClient`, WS-Client,
  kompletter Offline-Stack, Geräte-Identitäts-Cache, `ExecutionManager`-Verdrahtung,
  Close-Listener-Registry, Wiederaufnahme-Scan *und* Geschäftslogik
  (`createExecution`/`cardLogin`-Online/Offline-Fallback). Der Singleton erzwingt bereits
  Test-Workarounds (der `wireToElwaManager=false`-Zweitkonstruktor in
  `ui/medium/MainFormController`, Zeilen 104–160, ist ein direktes Symptom).
  **Empfehlung:** die Online/Offline-Fallback-Methoden in einen eigenen
  `TerminalDataService` ziehen; Singleton-Zugriff langfristig durch Übergabe ersetzen.
  (Kein Quick-Fix – als bewusster Refactor einplanen.)

- **Niedrig · `application/SingleInstanceManager.java:16`** — `extends Thread` statt
  Delegation an einen `Runnable`/Executor; Vor-Migrations-Idiom.

### Aspekt 3 – Stand der Technik (Java 21 / JavaFX)

- **Hoch · `ui/medium/controller/DeviceListEntry.java:446–466`** — `switch(this.state)`
  im alten `case:`-Stil mit Fall-Through: `case DISABLED` hat **kein `break`** und fällt
  in `case UNREGISTERED` durch – die gerade gesetzte „deaktiviert"-Darstellung (Style
  `status-disabled`, Text „deaktiviert", `setDisable(true)`) wird sofort von
  `status-unregistered`/„Keine Steckdose"/`setDisable(false)` überschrieben. Ein
  deaktiviertes Gerät wird damit falsch dargestellt und die Kachel wieder bedienbar.
  Genau die Fehlerklasse, die ein Arrow-`switch` (Java 14+) strukturell ausschließt.
  **Empfehlung:** auf `switch`-Arrow-Form umstellen; damit ist der Fall-Through unmöglich
  und die Style-Reset-Wiederholung lässt sich zentralisieren. (Zugleich R2-relevant.)

- **Niedrig · durchgängig** — Vor-Java-21-Idiome, die die Konvention „Bestand spiegeln"
  teils schon selbst verlässt (neuere Klassen nutzen `var`/Records/Arrow-`switch`):
  - `java.util.Vector` als Listener-Liste an 6 Stellen (`ExecutionManager:70–72`,
    `ElwaManager:158–159`, `CardReader:45`, `BacklightManager:32`, `InactivityJob:23`) –
    synchronisiert-teuer und veraltet; `CopyOnWriteArrayList` passt fachlich besser
    (Iteration während Benachrichtigung).
  - `e.printStackTrace()` in `FhemDevicePowerManager.java:256` und
    `application/Main.java:108` statt Logger.
  - String-Konkatenation im Logging (`"[" + name + "] …"`) in
    `ExecutionManager`/`ExecutionFinisher`/`ElwaManager` statt `{}`-Platzhaltern (die
    neueren Klassen machen es richtig).
  - `common/Utilities.sha1` baut den Hex-String per `result +=` in einer Schleife
    (`HexFormat` seit Java 17 vorhanden).

- **Niedrig · FX-Idiomatik** — `ui/medium` nutzt Properties/Bindings sauber
  (`registeredUserProperty`, Auto-Logout-Listener). `ui/small` verdrahtet dagegen fast
  alles manuell (Sichtbarkeits-Flags, Index-`switch`) – kein Blocker, aber der
  inkonsistente Stil erhöht die Einarbeitungskosten zwischen den beiden UI-Größen.

### Aspekt 4 – Lesbarkeit / Wartbarkeit

- **Mittel · `ui/small/MainFormController.java:358` und `:412`** — In
  `catch (FhemException e1)` wird `this.logger.error("Communication with FHEM-Server
  failed.", e)` geloggt – `e` ist hier der `ActionEvent` des Lambdas, nicht die gefangene
  `FhemException e1`. Der eigentliche Stacktrace geht verloren (bzw. wird über die
  `(String, Object)`-Überladung falsch formatiert). **Empfehlung:** `e1` loggen;
  erschwert sonst die Ferndiagnose per `LOG_REQUEST`.

- **Mittel · `ui/medium/MainFormController.java` (247–285)** — Drei überladene
  `displayError(...)`-Signaturen mit subtil unterschiedlicher Back/Retry-Semantik;
  zusammen mit den zwei Varianten in `ui/small` ist die Fehleranzeige schwer konsistent
  zu halten. **Empfehlung:** auf eine parametrisierte Variante (z. B.
  `ErrorState`-Builder) konsolidieren.

- **Niedrig · `ui/medium/controller/DeviceListEntry.java:58`** — `private final Integer
  LOCK = 0;` als Monitor-Objekt: autoboxter `Integer`-Cache-Konstante als Lock ist fragil
  (und nicht `static`). **Empfehlung:** `private final Object lock = new Object();`.

- **Niedrig · `configuration/WashguardConfiguration.java`** — Inkonsistentes
  Konfig-Handling: `getFhemPort()` (Zeile 99) ruft ungeschütztes `Integer.parseInt`
  (wirft bei Fehlkonfiguration), während alle anderen Getter `NumberFormatException`
  fangen und auf Defaults zurückfallen. Einheitlich behandeln.

- **Niedrig · Magic Numbers** — Feste Kachelzahl `4` mehrfach in
  `ui/small/MainFormController`; Poll-/Delay-Konstanten teils inline. Kosmetisch.

### Aspekt 5 – Konventionstreue (AGENTS.md §5)

- **Positiv** — Bezeichner Englisch, Kommentare Deutsch, Warum-Kommentare: durchgehend
  gut eingehalten, in `offline/`/`api/` vorbildlich.
- **Niedrig · Tippfehler in Bezeichnern** — `upateToolbarState`
  (`ui/medium/state`/`MainFormController.updateToolbar`), `finnishedListener`
  (`InactivityJob:23`), `creditSufficent`/`creditInsufficent`, Enum-Wert
  `CONFIRMATION_CREDIT_INSUFFICENT`. Rein kosmetisch, aber öffentlich sichtbare
  API-Namen; bei Gelegenheit angleichen.
- **Niedrig · `common/ProgramType.java:10`** — nachgestelltes Komma im Enum
  (`FIXED, DYNAMIC, OPEN_DOOR,`) – harmlos, aber unüblich.

## 4. Test-Hinweise (Qualitätsperspektive, Detail in R6)

- Für die beiden Hoch-Findings fehlt jeweils ein Regressionstest:
  (a) `stopListenToExecutionStartedEvent` entfernt tatsächlich (Listener-Liste
  schrumpft); (b) `DeviceListEntry.refresh()` im Zustand `DISABLED` rendert „deaktiviert"
  und *nicht* „Keine Steckdose". Beide sollten mit den Fixes entstehen.
- Die `offline/`-Schicht ist erkennbar gut testabgedeckt; `OfflinePricing` hat jedoch
  keinen Parity-Test gegen das Backend-`PricingService` (s. Aspekt 1).

---

**Priorisierung fürs Gate:** Die zwei Hoch-Findings (`ExecutionManager:326`
Listener-Leak, `DeviceListEntry:446` Fall-Through) vor dem Feldeinsatz beheben – beide
sind kleine, punktuelle Änderungen mit Regressionstest. Der strukturelle Rest
(Gott-Klassen, Singleton, Alt-Idiome) beeinträchtigt die Wartung real, ist aber ein
bewusst zu planender Refactor und kein Launch-Blocker.
