# 21. Offline-Replay-Härtung II: Replay-Zeitstempel-Pflicht, Geister-Execution-Kompensation, Dead-Letter-Datenintegrität

- **Status:** accepted
- **Datum:** 2026-07-23

## Kontext

Die Code-Review zu AP1 (Epic #66, ADR 0016) hatte im gehärteten Offline-/Replay-Pfad drei
bewusst als Restrisiken ausgelagerte Follow-ups notiert (Issues #67, #68, #69). Nach
Auftraggeber-Festlegung werden sie jetzt behoben:

1. **#67 – Replay-Flag rein client-gesteuert (Defense-in-Depth):** Der mit #16 eingeführte
   privilegierte Nachbuchungs-Pfad (`POST /api/v1/executions` mit `replay=true`) überspringt
   **alle** fachlichen Wächter. Das `replay`-Flag, die `userId` und der `clientTimestamp`
   stammen frei aus dem Request-Body, ohne Korrelation zu einer echten vorherigen
   Offline-Buchung. Innerhalb des Vertrauensmodells (Standort-Token, Single-Writer, ADR 0010/
   0016) akzeptabel, aber eine Aufweichung der Tiefenverteidigung: ein kompromittiertes/
   fehlerhaftes Terminal-Token hätte mehr Wirkung als nötig.
2. **#68 – Geister-Execution bei Stufe-B-Replay:** Gelingt beim Replay einer offline gebuchten
   Ausführung der `START` (Backend legt die Execution an), scheitert danach aber sein
   `FINISH`/`ABORT` fachlich und wandert ins Dead-Letter, bleibt die serverseitig angelegte
   Execution unbeendet – das Gerät wirkt beim Backend belegt, bis es über `isExpired` (Ablauf
   der Maximaldauer) herausfällt. Eng verwandt mit dem in ADR 0016 dokumentierten Restrisiko
   #59.
3. **#69 – Dead-Letter-Datenverlust:** `OfflineJournal#moveToDeadLetter` entfernte den
   Poison-Eintrag **immer** aus dem aktiven Journal – auch wenn das Schreiben in die
   Dead-Letter-Datei mit einer `IOException` scheiterte (Platte voll/defekt). Der Eintrag war
   dann weder im Journal noch im Dead-Letter, also spurlos verloren.

## Entscheidung

- **Replay-Zeitstempel-Pflicht mit Ablehnung (#67):** Eine echte Nachmeldung trägt immer den
  **Original-Zeitpunkt** der offline gebuchten Ausführung, der deutlich in der Vergangenheit
  liegt (ein Stufe-B-`START` wird ohnehin erst nachgemeldet, wenn sein Ende im Journal liegt,
  die Maschine also fertig ist). Der Replay-Pfad **verlangt** deshalb einen plausiblen
  Vergangenheits-Zeitstempel und **lehnt ab** (`422 invalid-replay-timestamp`), wenn er fehlt
  oder „jetzt"/in der Zukunft liegt. Grenze „jetzt": ein konfigurierbarer Mindestabstand
  `elwasys.offline.replay-min-backdating` (Default 60 s) – groß genug, um „jetzt" sicher
  abzuweisen, klein genug, um selbst den kürzesten realen Waschgang nie fälschlich abzulehnen.
  Ein **zu ALTER** Zeitstempel wird bewusst **nicht** abgelehnt: Ein Stufe-B-`START` wird laut
  Paar-Reihenfolge erst nachgemeldet, wenn sein Ende im Journal liegt – sein Alter beim Replay
  ist `Waschdauer + Wiederverbindungszeit` und kann das Offline-Fenster des Standorts
  überschreiten (langer Waschgang bei frischem Snapshot). Eine harte „zu alt"-Grenze würde
  diesen legitimen Fall ins Dead-Letter schieben und die Buchung nie abrechnen (Umsatzverlust);
  absurd alte Werte ersetzt stattdessen `ClientTimestampPolicy#resolve` durch die Serverzeit,
  ohne die Buchung zu verlieren. Jede angenommene Nachbuchung wird zusätzlich
  **auditiert** (INFO-Log mit Nutzer/Gerät/Programm/Standort/Original-Zeitstempel), damit
  anomale Muster sichtbar werden. Bewusst **Ablehnung statt stiller Serverzeit-Ersetzung**
  (wie in `ClientTimestampPolicy#resolve` für reguläre Aufrufe): beim Replay ist der
  fehlende/„jetzt"-Zeitstempel gerade das verdächtige Signal, keine bloße Uhren-Drift. Die
  #16-Invariante „kein Journal-Verklemmen" bleibt erhalten, weil `422` ein **fachlicher**
  Fehler ist – das Terminal schiebt einen so abgelehnten Eintrag ins Dead-Letter (ADR 0016
  #17), statt ihn ewig zu wiederholen.
- **Geister-Execution-Kompensation (#68):** Wird beim Replay ein Terminator
  (`FINISH`/`ABORT`) dead-lettert, dessen zugehöriger `START` **in diesem Lauf** bereits
  erfolgreich angelegt wurde (`resolvedStartKeys` kennt die echte Backend-Id), wird (a) **laut
  alarmiert** (statt stumm wie ein gewöhnlicher Poison-Entry) und (b) ein **kompensierender
  `abort`** der frisch angelegten Execution versucht (best effort, eigener abgeleiteter
  Idempotenz-Schlüssel `<finish-key>-ghost-abort`). Scheitert der Abort selbst (Backend wieder
  weg), bleibt es beim Alarm, und die Execution fällt spätestens über `isExpired` heraus. Ein
  Stufe-A-Terminator (echte `executionId`, kein `startIdempotencyKey`) hat keine vom Replay
  erzeugte Geister-Execution und wird unverändert behandelt.
- **Dead-Letter-Datenintegrität (#69):** `moveToDeadLetter` nutzt **Write-before-Remove** –
  `removeEntry` läuft nur nach **nachweislich erfolgreichem** Dead-Letter-Schreiben. Bei
  Write-Fehler bleibt der Eintrag im aktiven Journal (kein Totalverlust). Damit ein dauerhaft
  defekter Datenträger keinen Busy-Loop erzeugt (alle ~20 s ein erneut scheiternder
  Backend-Aufruf + Write), begrenzt ein **neustartfester Fehlversuchszähler** je
  Idempotenz-Schlüssel (In-Memory autoritativ für die laufende Sitzung, best-effort in eine
  Sidecar-Datei `offline-journal.jsonl.deadletter-failures` persistiert) die Versuche: nach
  `MAX_DEAD_LETTER_WRITE_ATTEMPTS` (5) wird der Eintrag endgültig aufgegeben und laut
  protokolliert. Der verlorene Eintrag ist dann ohnehin ein dauerhaft fachlich abgelehnter
  Poison-Entry, der nie verbucht worden wäre.

## Konsequenzen

- **#67:** Ein legitimer Replay (Original-Zeitstempel, deutlich in der Vergangenheit) ist
  unverändert unauffällig – der Nutzer merkt nichts. Ein Replay ohne/„jetzt"-Zeitstempel wird
  abgelehnt (und beim Terminal dead-lettert, nicht verklemmend). Der Admin erhält über das
  Audit-Log Sichtbarkeit über jede privilegierte Nachbuchung. Restrisiko: ein Angreifer mit
  gültigem Standort-Token kann weiterhin backdaten – die Härtung erschwert Missbrauch und macht
  ihn sichtbar, sie ersetzt nicht das Vertrauensmodell (Standort-Token/Single-Writer).
- **#68:** Eine fertige, aber serverseitig „hängende" Maschine wird beim seltenen Fehlerfall
  sofort per Kompensations-Abort freigegeben (wieder buchbar) statt bis zum `maxDuration`-
  Ablauf belegt zu bleiben; scheitert der Abort, ist der Vorfall zumindest laut alarmiert statt
  stumm.
- **#69:** Bei einem Datenträger-Defekt bleibt eine Diagnose-Spur erhalten (kein stiller
  Totalverlust) und es entsteht kein unendlicher Busy-Loop. Für den Nutzer nicht sichtbar –
  reine Betriebs-/Datenintegritäts-Vorsorge.
- Grenze der Zähler-Persistenz (#69): kann auf einem vollständig defekten Datenträger auch die
  winzige Sidecar-Datei nicht geschrieben werden, überlebt der Zähler keinen Neustart; er
  bricht den Busy-Loop dann aber weiterhin innerhalb der laufenden Sitzung (In-Memory
  autoritativ). Bewusst akzeptiert.

Herkunft: Code-Review zu AP1 (Issues #67, #68, #69), Follow-up zu Epic #66; baut auf ADR 0010
und [ADR 0016](0016-offline-replay-haertung.md) auf.
