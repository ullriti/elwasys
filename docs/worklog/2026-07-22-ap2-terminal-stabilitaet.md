# 2026-07-22 — AP2: Terminal-Stabilität & Aufräumen (Pre-Launch-Fixes)

**Ziel:** Das Terminal muss deCONZ-Neustarts, portal-ausgelöste Fernwartungs-Neustarts und
nebenläufige Abläufe ohne dauerhafte Ausfälle überstehen; Logging und Aufräumen gehärtet.
Ein PR gegen `master`, Epic #66, AP2 (Issues #19, #27, #28, #51, #52, #53, #55, #56, #57, #58,
#61). Reines Terminal-Paket (`Client-Raspi/`) – bewusst in einem Kontext bearbeitet statt
delegiert, weil die Lebenszyklus-/Concurrency-Fixes (#27/#28/#52) eng ineinandergreifen
(AGENTS.md §4: „direkt bearbeiten, wenn eng gekoppelt").

## Erledigt

### Blocker/Major
- **#19 – deCONZ-Reconnect:** `DeconzEventListener#afterConnectionClosed`/`handleTransportError`
  stoßen jetzt `scheduleReconnect()` an (bisher nur bei fehlgeschlagenem VerbindungsAUFBAU).
  Ohne den Fix fiel nach einem deCONZ-Neustart die Programm-Ende-Erkennung bis zum App-Neustart
  aus (jede Maschine lief bis `maxDuration`, bei DYNAMIC = Maximalpreis) und `setDeviceState`
  lief in Timeouts. Muster identisch zum Backend-`TerminalWebSocketClient`; der Stop-Fall bricht
  über den heruntergefahrenen Scheduler sauber ab (kein Reconnect-Sturm).
- **#27 – Restart-Leak:** `ElwaManager#initiate()` erzeugt den `TerminalWebSocketClient` nur noch
  einmal (Null-Guard) und meldet ihn nach einem `closeListeners.clear()` stets neu am
  Close-Event an – so überlebt die Verbindung den Neustart bewusst, ohne dass zwei Clients
  endlos gegeneinander reconnecten. `CardReader#listenToCardDetectedEvent` ist idempotent
  (contains-Check), `MainFormController#initializeComponents` installiert die additiven
  Klick-/Auswahl-Handler nur einmal (`installComponentHandlersOnce()` + Flag). Ergebnis: ein
  Kartenscan nach mehreren Restarts löst genau einen Login aus, Kacheln haben einfache Handler.
- **#28 – Concurrency/Doppel-Finish:** `executionFinishers`/`plannedStops` sind
  `ConcurrentHashMap` (mehrere Threads: FX, 4er-Scheduler, deCONZ-WS, Backend-WS).
  `ExecutionFinisher#retry()` läuft jetzt über denselben `runGuarded()`-Rumpf wie `run()`
  (Objekt- + Geräte-Lock, `executed`-Prüfung) – ein Retry kann das Finish nicht mehr ein
  zweites Mal ausführen (kein 409/Fehleranzeige). `run()` behält seine Fehler→Listener-Meldung,
  `retry()` reicht Fehler wie bisher an den Aufrufer weiter (Retry-UX unverändert).
- **#51 – Watchdog:** In der Geräteschleife `return` → `continue`; ein nicht erreichbares Gerät
  bricht die Fremdeinschalt-Prüfung der übrigen Geräte nicht mehr ab.

### Minor
- **#52 – FX-Thread:** UI-Mutationen an vier Stellen in `ui/small/MainFormController` in
  `Platform.runLater` gewrappt (Abort-Worker, `door_buttonDone`, `beginWait`,
  `onExecutionFailed`-Retry).
- **#53 – Kaputte 2xx:** Neuer Fehlerzustand `ApiException.malformedResponse(...)`
  (`isCommunicationFailure()==false`); `ApiClient#parse` nutzt ihn bei `JsonSyntaxException`
  auf 2xx. Eine erreichbare-aber-unlesbare Antwort löst nicht mehr fälschlich den Offline-Pfad
  (Offline-Buchung trotz erreichbarem Server) aus.
- **#55 – Journal-fsync:** `OfflineJournal#append` schreibt mit `StandardOpenOption.DSYNC` –
  ein gerade journalierter START/FINISH überlebt einen Stromausfall.
- **#56 – RFID/Log:** `Utilities#maskCardId` (nur letzte 4 Stellen), `CardReader` loggt
  maskiert; `Utilities#getCurrentLogFile` liefert deterministisch den INFO-Appender (`"FILE"`),
  nicht das DEBUG-Log – die Fernwartung (`LOG_REQUEST`) gibt keine Karten-Ids preis.
- **#57 – Resume-NPE:** Null-Check für `findProgram` im Wiederaufnahme-Scan – eine Ausführung
  mit zwischenzeitlich entferntem Programm wird mit Warnung übersprungen, statt das Terminal
  in den Fehlerzustand zu bringen.
- **#58 – deCONZ-Passwort:** `setup.sh` nutzt `openssl rand -hex 16` (CSPRNG) statt eines aus
  dem Installationszeitpunkt ableitbaren `date`-Werts.
- **#61 – Dead Code:** Die sechs `getSmtp*`-Getter im Terminal-`ConfigurationManager` entfernt
  (der Client versendet seit Phase 4 AP4 keine Mails mehr).

## Tests
- Neue, deterministische Unit-Tests (kein Sleep/Zufall):
  `ExecutionFinisherRetryTest` (#28: run()+retry() ⇒ genau ein Finish, virtuelle Ausführung),
  `ApiClientMalformedResponseTest` (#53: kaputte 2xx ist kein Kommunikationsfehler, TCP-Stub),
  `UtilitiesCardMaskingTest` (#56: Maskierung). Alle grün (5 Tests).
- Bestehende infrastrukturfreie Unit-Tests weiter grün (`ApiClientTransientRetryTest`,
  `OfflineGateway*Test`, `TerminalReadinessMarker*`). UI-Suite unter Xvfb grün
  (`HeadlessFxSmokeTest`, `ProgramListEntryFxmlTest`, `MainFormStateManagerTest`).
- Client-E2E/Deconz-E2E bleiben fachlich abgedeckt (laufen in CI mit Backend + Xvfb).

## Review-Gate (code-reviewer)
- **Finding 1 (blockierend) behoben:** #56 war unvollständig – beide `MainFormController`
  (small/medium) loggten die rohe Karten-Id auf WARN („no user associated to card …"), und WARN
  landet im INFO-`FILE`-Appender, der per Fernwartung (`LOG_REQUEST`) abrufbar ist. Beide
  Stellen nutzen jetzt `Utilities.maskCardId(...)`. Damit sind alle Karten-Id-Log-Stellen
  (CardReader-DEBUG + beide WARN) maskiert; `UtilitiesCardMaskingTest` um eine explizite
  Anti-Leak-Zusicherung ergänzt.
- **Finding 2 (should) adressiert:** deCONZ-`stop()` fährt den Scheduler nur mit `shutdown()`
  herunter, ein bereits eingeplanter Reconnect könnte danach eine Zombie-Verbindung aufbauen.
  Abgedeckt durch den zusätzlichen `reconnectScheduler.isShutdown()`-Guard am Anfang von
  `openConnection()` (ein nachlaufender Task kehrt sofort zurück).
- **Finding 3 (suggestion) bewusst vertagt:** Das check-then-put in `onPowerMeasurementAvailable`
  bleibt nicht-atomar (wie im Altzustand, kein Regressionsrisiko). Praktisch entschärft durch
  den `run()`/`retry()`-`executed`-Guard aus #28: ein doppelt eingeplanter Auto-Stop führt beim
  zweiten Feuern nur noch zu einem No-Op statt zu einem Doppel-Finish. Als Known-Issue vermerkt.

## Offen / nächster Schritt
- AP3 ff. des Epics (#66) als nächste PRs.
- Optional (Known-Issue): `onPowerMeasurementAvailable` auf `plannedStops.compute(...)` bzw.
  `synchronized (execution.getDevice())` umstellen, um die Auto-Stop-Planung vollständig atomar
  zu machen.

## Referenzen
- Epic: https://github.com/ullriti/elwasys/issues/66 ; Issues #19, #27, #28, #51, #52, #53,
  #55, #56, #57, #58, #61
- Branch: `claude/ap2-von-66-47mau2`
