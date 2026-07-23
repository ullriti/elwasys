# 2026-07-23 — Offline-Replay-Härtung II (#67/#68/#69)

**Ziel:** Die drei bewusst aus AP1 ausgelagerten Defense-in-Depth-/Datenintegritäts-Follow-ups
des Offline-/Replay-Pfads beheben (Code-Review zu Epic #66, ADR 0016): Replay-Zeitstempel-
Pflicht (#67), Geister-Execution-Kompensation (#68), Dead-Letter-Datenintegrität (#69).

## Erledigt

### Backend (#67 – Replay-Zeitstempel-Pflicht mit Ablehnung)
- `ExecutionController#start` verlangt im Replay-Zweig jetzt einen plausibel in der
  Vergangenheit liegenden `clientTimestamp` und **auditiert** jede angenommene Nachbuchung
  (INFO-Log: Nutzer/Gerät/Programm/Standort/Original-Zeitstempel).
- Neuer Guard `ClientTimestampPolicy#requireValidReplayTimestamp`: lehnt fehlende/„jetzt"/
  Zukunfts-Zeitstempel mit neuer `InvalidReplayTimestampException` (`422
  invalid-replay-timestamp`) ab. Bewusst **Ablehnung** statt stiller Serverzeit-Ersetzung (wie
  bei `resolve`) – beim Replay ist der fehlende/„jetzt"-Zeitstempel das verdächtige Signal. Ein
  **zu alter** Zeitstempel wird bewusst **nicht** abgelehnt (Review-Fix): ein Stufe-B-START wird
  erst nach dem Waschgang-Ende nachgemeldet, sein Alter kann das Offline-Fenster legitim
  überschreiten (langer Waschgang) – `resolve` fängt absurd alte Werte per Serverzeit-Ersatz ab,
  ohne die Buchung zu verlieren.
- Neuer Config-Knopf `OfflineProperties.replayMinBackdating` (`elwasys.offline.
  replay-min-backdating`, Default 60 s): Mindestabstand in die Vergangenheit; groß genug, um
  „jetzt" abzuweisen, klein genug, um den kürzesten realen Waschgang nie fälschlich abzulehnen.
- #16-Invariante bleibt: `422` ist ein fachlicher Fehler → das Terminal dead-lettert einen so
  abgelehnten Eintrag, das Journal verklemmt nicht.

### Client/Terminal (#68 – Geister-Execution-Kompensation)
- `OfflineGateway#compensateGhostExecutionIfNeeded`: Wird beim Replay ein Terminator
  (`FINISH`/`ABORT`) dead-lettert, dessen `START` in **diesem** Lauf bereits angelegt wurde
  (`resolvedStartKeys`), wird laut alarmiert (statt stumm) und ein kompensierender `abort` der
  frisch angelegten Execution versucht (best effort, eigener Schlüssel `<finish-key>-ghost-abort`).
  Stufe-A-Terminatoren (echte `executionId`, kein `startIdempotencyKey`) unverändert.

### Client/Terminal (#69 – Dead-Letter-Datenintegrität)
- `OfflineJournal#moveToDeadLetter` auf **Write-before-Remove** umgestellt: `removeEntry` nur
  nach erfolgreichem Dead-Letter-Write; bei Write-Fehler bleibt der Eintrag im aktiven Journal
  (kein Totalverlust).
- Neustartfester Fehlversuchszähler je Idempotenz-Schlüssel (In-Memory autoritativ +
  best-effort-Persistenz in Sidecar-Datei `offline-journal.jsonl.deadletter-failures`): nach
  `MAX_DEAD_LETTER_WRITE_ATTEMPTS` (5) wird der (ohnehin fachlich abgelehnte) Eintrag endgültig
  aufgegeben → kein unendlicher Busy-Loop bei defektem/vollem Datenträger.

### Doku
- Neue **ADR 0021** (Offline-Replay-Härtung II) inkl. Auftraggeber-Entscheidungen
  (Timestamp-Pflicht *mit Ablehnung*, Auto-Abort-Variante für #68); ADR-Index, CHANGELOG und
  KB-„Aktueller Stand" mitgepflegt.

## Entscheidungen
- **#67:** Timestamp-Pflicht **mit Ablehnung** (422), nicht nur Audit – Auftraggeber-Wahl.
- **#68:** Die **umfangreichere Variante mit kompensierendem Auto-Abort** (nicht nur Alarm) –
  Auftraggeber-Wahl.
- **#69:** Write-before-Remove + neustartfester Zähler wie im Issue vorgeschlagen.

## Tests
- Backend: `ExecutionControllerOfflineReplayTest` um 5 #67-Fälle erweitert (Replay ohne/„jetzt"/
  Zukunft → abgelehnt; plausible Vergangenheit → angenommen; **langer Waschgang älter als das
  Offline-Fenster → weiterhin angenommen**, Regression zu Review-Finding 1). Klasse grün:
  **16 Tests**. (Die `@SpringBootTest`-DB-Integrationstests brauchen Docker/Testcontainers und
  laufen nur in CI – hier nicht ausführbar.)
- Client: `OfflineGatewayReplayTest` um 3 #68-Fälle erweitert (Kompensations-Abort bei
  Geister-Execution; Gegenprobe Stufe-A ohne Abort; **fehlschlagender Kompensations-Abort reißt
  den Replay-Lauf nicht ab**, Regression zu Review-Finding 2); neuer `OfflineJournalTest` (3
  #69-Fälle: Write-Fehler → Eintrag bleibt; Aufgabe nach N Versuchen; Zähler übersteht Neustart).
  Offline-Paket grün: **14 Tests**. Deterministisch, kein Sleep/Zufall (Fake-`ApiClient`,
  Schreibfehler via blockierendem Verzeichnis).

## Review
- Blockierendes `code-reviewer`-Gate durchlaufen; 4 Findings behoben: **(1, Must)** die zunächst
  eingebaute „zu alt"-Ablehnung im Replay-Guard hätte legitime lange Waschgänge (Alter >
  Offline-Fenster) fälschlich abgelehnt (Umsatzverlust + Verhaltensänderung) → entfernt, `resolve`
  fängt alte Werte ab; **(2, Should)** Kompensations-Abort fing nur `ApiException` → jetzt auch
  `RuntimeException`, damit er den Replay-Lauf nie abreißt; **(3, Should)** `removeEntry` meldet
  jetzt Erfolg (`boolean`), `moveToDeadLetter` wertet ein fehlgeschlagenes Entfernen nach
  erfolgreichem Dead-Letter-Write nicht mehr als Erfolg (kein Dead-Letter-Duplikat/Busy-Loop),
  plus Kurzschluss für endgültig aufgegebene Einträge; **(4, Should)** `DSYNC` beim
  Dead-Letter-Write und `removeEntry` (konsistent zu #55).

## Offen / nächster Schritt
- Live-Gang/Betrieb unverändert der nächste Schritt (siehe KB „Aktueller Stand").

## Referenzen
- Issues #67, #68, #69 (Follow-up zu Epic #66) ; ADR
  [0016](../architecture/0016-offline-replay-haertung.md),
  [0021](../architecture/0021-offline-replay-haertung-ii.md)
- Branch: `claude/issues-67-68-69-u3558p`
