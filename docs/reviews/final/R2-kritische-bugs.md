# R2 – Kritische Bugs/Schwachstellen (finale Pre-Launch-Review, frische Augen)

> Track R2 der [Spec 0001](../../specs/0001-finale-review.md), Modell Opus, 2026-07-23.
> Das Hoch-Finding wurde vom Hauptagenten am Code gegenverifiziert
> (`OfflineGateway.java:329/342/345-350/410-415` – bestätigt).

## 1. Gesamturteil

Die vier kritischen Pfade sind insgesamt sorgfältig und wehrhaft gebaut; die AP1–AP6-Fixes
und die Follow-ups #67–#69 halten dem adversarialen Blick weitgehend stand. Die
Geld-/Locking-Pfade (pessimistisches Zeilen-Locking, Advisory-Locks, Lock-Reihenfolge,
AFTER_COMMIT-Benachrichtigung) und die Auth-Härtungen (neutrale Sperr-/Reset-Meldung,
Standort-Validierung der Fernwartung, gedrosseltes Token) sind konsistent und ohne
offensichtliche Umgehung. **Es gibt jedoch einen echten, reproduzierbaren High-Fund im
Offline-Replay-Pfad**: die in ADR 0021 (#68) eingebaute Geister-Execution-Kompensation
greift nur *innerhalb eines Replay-Laufs* – ein Kommunikationsabbruch zwischen dem
nachgemeldeten START und seinem Terminator hebelt sie aus und kann eine bereits fertige
Maschine serverseitig „laufend" hängen lassen und beim nächsten Terminal-Neustart sogar
erneut einschalten. Sonst keine kritischen Findings.

## 2. Geprüfte Bereiche (Abdeckungsnachweis)

- **Abrechnung/Guthaben:** `CreditService` (lockUser/payExecution/payout/inpayment,
  requirePositive #22, getCredit-Reservierungsmodell), `ExecutionService`
  (getForUpdate/finishExecution, Preis-Deckel #18), `ExecutionController`
  (Start-/Finish-Pfad), `AdvisoryLockService`, `IdempotencyService` (#29/#41).
  Lock-Reihenfolge über alle Geldpfade verglichen (Start: idempotency-adv → device-adv →
  user-row; Finish: idempotency-adv → exec-row → user-row; Payout: user-row) – **keine
  zyklische Lock-Ordnung/Deadlock**, Doppelstart/Doppel-Finish/negatives Guthaben sind
  serialisiert. AFTER_COMMIT-Listener (#36) korrekt ohne `fallbackExecution`.
- **Offline-/Replay-Pfad:** `OfflineJournal` (Write-before-Remove #69,
  Fehlversuchszähler, DSYNC), `OfflineGateway` (replay-Ordering, Paar-Reihenfolge,
  Ghost-Kompensation #68, Uhren-Plausibilität #54), `ClientTimestampPolicy`
  (#67 requireValidReplayTimestamp/resolve), `ExecutionFinisher`/`ExecutionManager`
  (Stufe-A-Fallback). Kanten Uhrensprünge, Doppel-Replay, START/FINISH-Reihenfolge,
  Absturz zwischen Schritten durchgespielt.
- **Auth:** `ElwasysAuthenticationProvider` (Brute-Force #25, neutrale Meldung, Zähler nur
  für existierende Nutzer), `RateLimiter` (synchronized), `PasswordResetService`
  (#24 Neutralisierung/#47 Mehrfachadresse/Cooldown), `CardLoginController` (#21),
  `TerminalMaintenanceService` (#26 Standort-Validierung der Antwort).
  Enumeration-/Race-Fälle geprüft.
- **Terminal-Nebenläufigkeit & WebSocket:** `ExecutionManager`/`ExecutionFinisher`
  (ConcurrentHashMap + `executed`-Retry-Guard #28), `DeconzEventListener` (Reconnect nach
  Abbruch #19, isReconnectRunning-CAS), `TerminalWebSocketClient` (Reconnect/stopped-Flag,
  Restart-Überleben #27), FX-Thread-Disziplin stichprobenartig.

## 3. Findings

---

**Schwere: hoch**
**Fundstelle:** `Client-Raspi/src/main/org/kabieror/elwasys/raspiclient/offline/OfflineGateway.java:341-342`
(Entfernen des START direkt nach Erfolg) i. V. m. `:344-351` (Kommunikationsfehler →
`return false`) und `:403-415` (`compensateGhostExecutionIfNeeded`,
`ghostExecutionId == null` → No-Op); Folgewirkung
`Client-Raspi/.../application/ElwaManager.java:270-291` (Wiederaufnahme energetisiert die
Steckdose erneut, via `ExecutionManager.startExecution` → `setDevicePowerState(..., ON)`).

**Beschreibung:** Die Geister-Execution-Kompensation aus ADR 0021 (#68) beruht auf der
In-Memory-Map `resolvedStartKeys`, die **nur für die Dauer eines einzelnen
`replay()`-Laufs** existiert. Ein Stufe-B-START wird nach erfolgreichem Nachmelden sofort
einzeln aus dem Journal entfernt (`removeEntry`, Zeile 342), *bevor* sein zugehöriger
FINISH/ABORT nachgemeldet ist. Reißt die Verbindung genau zwischen diesen beiden Schritten
ab (Backend war beim START noch erreichbar), bricht der Lauf über den
Kommunikationsfehler-Zweig mit `return false` ab (Zeile 350) – der START ist bereits
entfernt, der Terminator bleibt im Journal, und die im RAM gelernte echte Backend-Id ist
verloren. Im nächsten Lauf ist der Terminator ein Waise: `replayOne` findet keine
`executionId` (weder im Eintrag noch in `resolvedStartKeys`), wirft
`IllegalStateException`, und `compensateGhostExecutionIfNeeded` kehrt wegen
`ghostExecutionId == null` **wirkungslos** zurück (Zeile 410–415). Die serverseitig
angelegte Execution bleibt damit dauerhaft „laufend", bis sie über `isExpired`
(Maximaldauer) herausfällt – exakt der Zustand, den #68 verhindern sollte, nur über die
Lauf-Grenze hinweg nicht abgedeckt.

**Fehlerszenario (Ausgangszustand → Ablauf → falsches Ergebnis):**
1. Ausgangszustand: Terminal war offline, hat eine Wäsche komplett offline gebucht
   (Stufe B). Journal enthält START(k1) und FINISH(k1, startKey=k1). Backend kehrt zurück.
2. Replay-Lauf 1: START(k1) → `replayCreateExecution` erfolgreich, Backend legt
   Execution X (finished=false, running) an, `resolvedStartKeys[k1]=X`, START wird aus dem
   Journal entfernt (Zeile 342).
3. Unmittelbar danach: `finishExecution(X, ...)` trifft auf einen kurzen
   Netz-/Backend-Aussetzer (Rolling-Deploy, WLAN-Blip) →
   `ApiException.isCommunicationFailure()` → `return false` (Zeile 350). START ist weg,
   FINISH bleibt.
4. Replay-Lauf 2 (Backend wieder da): FINISH(k1) ist Waise → `executionId == null` und
   `resolvedStartKeys` leer → `IllegalStateException` → Ghost-Kompensation No-Op (kein
   Abort) → FINISH wandert ins Dead-Letter.
5. Falsches Ergebnis: Execution X bleibt serverseitig „laufend" und reserviert das
   Guthaben bis `isExpired` (bis zu `maxDuration`); die Wäsche wird nie abgerechnet
   (Umsatzverlust, da der lokale Debit mit dem gelöschten Journal-Eintrag ebenfalls
   verschwindet). **Schwerwiegender:** Meldet die Geräteübersicht X in diesem Fenster als
   „laufend", energetisiert die Wiederaufnahme-Logik (C13, `ElwaManager` Zeile 272–290)
   beim nächsten Terminal-Neustart die Steckdose der bereits fertigen Maschine erneut –
   genau die im Code-Kommentar (`OfflineGateway.java:296-302`) selbst benannte Gefahr.

**Empfehlung:** Den START-Eintrag **nicht** entfernen, solange sein Terminator noch nicht
erfolgreich nachgemeldet ist (Paar-Atomizität über Lauf-Grenzen hinweg) – z. B. START erst
nach erfolgreichem Terminator-Replay entfernen, oder die vom START gelernte Backend-Id in
den zugehörigen FINISH-Journal-Eintrag persistieren, bevor der START entfernt wird. Wegen
der serverseitigen Idempotenz (gleicher `Idempotency-Key` liefert dieselbe Execution-Id)
genügt es bereits, den START im Journal zu belassen: ein erneuter START-Replay im
Folgelauf gäbe dieselbe Id zurück und würde den Terminator wieder auflösbar machen.
Regressionstest: Replay mit erzwungenem Kommunikationsfehler zwischen START-Erfolg und
FINISH, danach zweiter Lauf – erwartet: kein Waisen-Dead-Letter, Execution serverseitig
beendet.

---

## Hinweise (unterhalb der Meldeschwelle, nur zur Einordnung)

Diese wurden geprüft und **nicht** als hoch/kritisch eingestuft – keine
Handlungsaufforderung, nur damit die Abdeckung nachvollziehbar ist:

- **Brute-Force-Limiter Check-then-act (`ElwasysAuthenticationProvider.java:104-122`):**
  `currentCount(...) >= max` und späteres `increment` sind nicht atomar; ein
  gleichzeitiger Burst kann das Fenster-Limit um wenige Versuche überschreiten. Für die
  geschlossene Admin-Nutzerbasis mit Fixed-Window-Limiter unkritisch (bewusst schlank,
  Einzelinstanz).
- **`ExecutionManager.stopListenToExecutionStartedEvent` (Zeile 325)** ruft
  `startListeners.add(l)` statt `remove(l)` – ein potentieller Listener-Leak, aber
  außerhalb der vier kritischen Pfade und ohne Geld-/Sicherheitswirkung.
- **`ExecutionNotificationListener`** greift nach AFTER_COMMIT auf `user`/`device` zu;
  falls Benachrichtigungen aktiviert werden und ein Lazy-Feld nachgeladen würde, bliebe
  die Wirkung auf „Mail nicht versendet, geloggt" beschränkt (Transaktion bereits
  committed) – keine Daten-/Geldintegritätsfrage.

Bewusst **nicht erneut gemeldet**: die in `docs/kb/05-migration-plan.md` („Restrisiken …")
und ADR 0018/0019 dokumentierten akzeptierten Restrisiken (Timing-Enumeration
Login/Reset, case-only-Username-TOCTOU #23, Session-Invalidierung #48,
Standort-Token-Blast-Radius #43, Klartext-Admin-Passwort-Mail #46, Geister-Execution #59
im create-plug-fail-Fall, Vaadin-Lizenz #33, Terminal-Totalausfall #60, Uhren-Drift
vorwärts #54).
