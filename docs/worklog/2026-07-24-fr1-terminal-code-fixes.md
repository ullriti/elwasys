# 2026-07-24 — FR-1: Terminal-Code-Fixes (H1–H3, #80/#81/#82)

**Ziel:** Die drei Hoch-Findings der finalen Review (Spec 0001, Epic #94) im Terminal
beheben – Arbeitspaket FR-1 aus der [SYNTHESE.md](../reviews/final/SYNTHESE.md), jeweils
mit einem Regressionstest, der ohne den Fix rot ist.

## Erledigt

- **H1 Offline-Replay-Paar-Atomizität über Lauf-Grenzen (#80)**
  (`Client-Raspi/.../offline/OfflineGateway.java`, `replay()`): ein erfolgreich
  nachgemeldeter `START` wurde bisher sofort aus dem Journal entfernt, BEVOR sein
  Terminator (`FINISH`/`ABORT`) nachgemeldet war. Riss die Verbindung genau dazwischen ab
  (Kommunikationsfehler), fehlte der `START` im Folgelauf – der Terminator wurde zum
  Waisen und fälschlich dead-lettert, obwohl die Ausführung serverseitig nie beendet
  wurde. Fix: ein erfolgreicher `START` landet in einer lauflokalen
  `pendingStartRemoval`-Map statt sofort entfernt zu werden; erst wenn sein Terminator das
  Journal verlässt (Erfolg ODER Dead-Letter, neuer Helfer `removePairedStartIfPending`)
  wird auch der `START` entfernt. Bricht der Lauf per Kommunikationsfehler vorher ab, bleibt
  der `START` unverändert im Journal – der nächste Lauf meldet ihn erneut (Replay-Endpunkt
  ist idempotent). Regressionstest `aStartAndItsFinishStayPairedAcrossACommunicationFailureBetweenThem`
  in `OfflineGatewayReplayTest` (`RecordingApiClient` um einen einmaligen
  Kommunikationsfehler bei `finishExecution` erweitert); die bestehenden #68-Geister-
  Kompensations-/Poison-Tests bleiben grün.
- **H2 Listener-Leak durch Copy-Paste (#81)**
  (`Client-Raspi/.../executions/ExecutionManager.java`): `stopListenToExecutionStartedEvent`
  rief versehentlich `add` statt `remove` auf. Beim Code-Fix zeigte sich, dass **derselbe
  Copy-Paste-Fehler unabhängig auch in `stopListenToExecutionErrorEvent` steckte** (im
  Finding nicht genannt, beim Umbau mitgefunden und mitbehoben). Fix: die sechs Register-/
  Unregister-Methoden nutzen jetzt zwei generische private Helfer (`register`/`unregister`),
  die die Fehlerklasse strukturell ausschließen. Regressionstest
  `ExecutionManagerListenerLeakTest` (neu): nach `listenTo…`+`stopListenTo…` bleibt ein
  `onExecutionStarted`-Broadcast beim Listener aus.
- **H3 Fall-Through `DISABLED`→`UNREGISTERED` (#82)**
  (`Client-Raspi/.../ui/medium/controller/DeviceListEntry.java`, `refresh()`): der
  `DISABLED`-Zweig des `switch` hatte kein `break` und fiel in `UNREGISTERED` durch – ein
  deaktiviertes Gerät zeigte sofort wieder „Keine Steckdose" und war (`setDisable(false)`)
  bedienbar. Fix: `switch` auf Arrow-Form (Java 14+) umgestellt, die wiederholte
  Style-Reset-Boilerplate (FREE/FREE_AVAILABLE/FREE_BLOCKED/DISABLED/UNREGISTERED) in einen
  Helfer `resetStatusStyleClasses()` zentralisiert. Regressionstest
  `DeviceListEntryFxmlTest` (neu, TestFX/FXML wie `ProgramListEntryFxmlTest`): `refresh()`
  im `DISABLED`-Zustand setzt `status-disabled`, NICHT `status-unregistered`, Text
  „deaktiviert", Kachel `isDisable()==true`.
- Alle drei Regressionstests gegen den Vor-Fix-Stand verifiziert (gezielt per
  `git stash` je Quelldatei zurückgesetzt, Test lief rot, danach wieder grün) – siehe
  Abschnitt „Referenzen" für die Testkommandos. Volle Nicht-E2E-Suite (47 Tests,
  Unit + headless TestFX via `xvfb-run`) grün; `mvn -f Client-Raspi/pom.xml package`
  baut sauber.

## Entscheidungen

- Den in der SYNTHESE.md unter FR-1 zusätzlich erwähnten „FHEM-Log-Bug (`e1`)" NICHT
  mitbehoben – er war nicht Teil der drei zugewiesenen Findings (#80/#81/#82) und hatte
  keine verifizierte Codestelle im Auftrag; bleibt offen für ein eigenes AP.
- H2-Fix bewusst über die im Finding empfohlene generische Helfer-Variante gelöst (statt
  Minimal-Fix), weil sie die bestehende Struktur nicht umkrempelt und den zusätzlich
  gefundenen zweiten Bug (`stopListenToExecutionErrorEvent`) strukturell mit schließt.
- Die vollen E2E-Suiten (`run-client-e2e.sh`, `run-cross-component-e2e.sh`, kompletter
  `run-ui-tests.sh`-Lauf) wurden in dieser Session nicht erneut vollständig durchlaufen
  (nicht Teil des Auftrags); stattdessen die drei neuen/geänderten Testklassen gezielt
  über `run-ui-tests.sh <Klasse>` bzw. `mvn test` verifiziert, dazu die volle
  Nicht-E2E-Suite.

## Offen / nächster Schritt

- Code-Review-Gate (Hauptagent) vor dem Push; danach FR-2 (Betrieb, H4–H7) angehen, dann
  Generalprobe nach Spec 0001 und Live-Gang.

## Referenzen

- Issues #80/#81/#82 (Epic #94), [SYNTHESE.md](../reviews/final/SYNTHESE.md) FR-1,
  [R3b-code-qualitaet-terminal.md](../reviews/final/R3b-code-qualitaet-terminal.md)
- `Client-Raspi/src/main/org/kabieror/elwasys/raspiclient/offline/OfflineGateway.java`,
  `Client-Raspi/src/main/org/kabieror/elwasys/raspiclient/executions/ExecutionManager.java`,
  `Client-Raspi/src/main/org/kabieror/elwasys/raspiclient/ui/medium/controller/DeviceListEntry.java`
- Tests: `Client-Raspi/src/test/.../offline/OfflineGatewayReplayTest.java`,
  `Client-Raspi/src/test/.../executions/ExecutionManagerListenerLeakTest.java` (neu),
  `Client-Raspi/src/test/.../ui/medium/controller/DeviceListEntryFxmlTest.java` (neu)
