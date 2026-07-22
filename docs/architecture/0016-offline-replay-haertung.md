# 16. Offline-Replay-Härtung: privilegierter Nachbuchungs-Pfad, Zeitstempel-Invariante, Preis-Deckel

- **Status:** accepted
- **Datum:** 2026-07-22

## Kontext

Die Offline-Buchungen am Terminal (ADR 0010) sind die Naht, die das neue Mehrbenutzer-System
vom Alt-System (ein Client je Gerät, direkter DB-Zugriff) unterscheidet. Die Pre-Launch-Review
(Issues #16–#18) hat im Offline-/Replay-Pfad drei zusammenhängende Blocker gefunden:

1. **#16 – Wächter verklemmen das Journal:** Der Replay nachgemeldeter Offline-Buchungen rief
   dieselben Endpunkte mit denselben fachlichen Wächtern auf wie eine Live-Buchung
   (`isBlocked`, `isUserAllowedAtLocation`, `canAfford`, Device-Occupied). Der Idempotenz-Key
   wird aber nur bei Erfolg abgelegt – ein fachlich abgelehnter Replay-Eintrag (Sperrung
   während des Offline-Fensters, standortübergreifende Guthabenänderung, zweiter Offline-Start)
   scheiterte bei jedem Versuch erneut und verklemmte das gesamte Journal dauerhaft.
2. **#17 – Client-Replay ohne Robustheit:** Poison-Entry (fachlicher Fehler) brach den ganzen
   Replay ab; ein `clear()` am Ende löschte während des Replays neu hinzugekommene Einträge;
   ein `FINISH` ohne auflösbaren `START` lief in eine Unboxing-NPE.
3. **#18 – Zeitstempel-Ersetzung:** Start- und Stop-Zeitstempel wurden unabhängig gegen „jetzt"
   plausibilisiert, nie gegeneinander – so konnte `stop < start` (0-€-Waschgang, negative
   Dauer in der DB) entstehen; der Preis beendeter Ausführungen war zudem ungedeckelt (langer
   Backend-Ausfall → Überberechnung).

Die zugrunde liegenden Auftraggeber-Festlegungen stehen in
[`../kb/05-migration-plan.md`](../kb/05-migration-plan.md) („Festlegungen zu den
Offline-Detailfragen") und ADR 0010: **Snapshot-Stand gilt bei Sperrungen**, **kein
Sicherheitsabschlag**, **negativ gewordene Salden werden beim Replay normal verbucht**.

## Entscheidung

- **Privilegierter Nachbuchungs-Pfad (#16):** Eine Nachmeldung ist ein **Fakt, keine Anfrage**.
  Der Terminal-Client kennzeichnet Replay-Starts über ein `replay`-Flag im Request
  (`POST /api/v1/executions`); der Server überspringt dann die fachlichen Wächter und verbucht
  das Ereignis, auch wenn dadurch ein negatives Guthaben entsteht. Live-Buchungen (ohne Flag)
  durchlaufen die Wächter unverändert.
- **Client-Replay-Robustheit (#17):** (a) **Paar-Reihenfolge** – ein `START` einer offline
  gebuchten Ausführung wird erst nachgemeldet, wenn seine Terminierung (`FINISH`/`ABORT`)
  ebenfalls im Journal liegt; (b) **Dead-Letter** – ein dauerhaft fachlich abgelehnter Eintrag
  wird in eine separate Datei verschoben und der Replay fährt fort, statt abzubrechen; (c) nur
  bei **Kommunikationsfehlern** wird der Lauf abgebrochen und später fortgesetzt; (d) Einträge
  werden **einzeln** nach Erfolg entfernt (kein pauschales `clear()` mehr, das parallel
  hinzugekommene Einträge verschluckt); (e) eine nicht auflösbare Backend-Id führt zum
  Dead-Letter statt zu einer NPE.
- **Zeitstempel-Invariante + Preis-Deckel (#18):** Beim Speichern des Stop-Zeitstempels wird
  `stop ≥ start` erzwungen (keine negative Dauer, kein `stop < start` in der DB). Die
  **Abrechnungsdauer** beendeter Ausführungen wird auf `min(stop − start, maxDuration)`
  gedeckelt – der Preis kann so nie über den beim Start geprüften `maxPrice` hinausgehen. Der
  **reale End-Zeitstempel bleibt** als Audit-Record erhalten (nur der Preis ist gedeckelt);
  diese bewusste Abweichung vom Alt-System (dort durch Einzelplatz-Client + Direkt-DB
  unerreichbar) ist im Änderungslog von `05-migration-plan.md` dokumentiert.
- **Uhren-Plausibilität (#54):** Liegt die Terminalzeit VOR dem Snapshot-Zeitpunkt
  (`now < generatedAt`), gilt der Snapshot als unbrauchbar (Raspberry Pi ohne RTC nach
  Stromausfall) – Offline-Buchungen werden dann abgelehnt statt auf Basis einer falschen Uhr
  falsch abgerechnet.

## Konsequenzen

- Der Offline-/Replay-Pfad verklemmt weder das Journal noch verliert er Buchungen; ein
  einzelner Giftzahn (gelöschtes Gerät/Programm, nicht auflösbares Ende) blockiert die übrigen,
  gültigen Einträge nicht mehr.
- Nachmeldungen sind bewusst privilegiert – die Sicherheit ruht auf der Single-Writer-Annahme
  (ein Terminal je Gerät, ADR 0010) und dem Standort-Token; das `replay`-Flag ist nur über den
  authentifizierten Terminal-Kanal setzbar.
- **Restrisiko Geister-Execution (#59):** Erreichte der Online-`create` den Server real, ging
  aber nur die Antwort verloren, und schlug danach das Einschalten der Steckdose fehl, entfernt
  der Client nur seinen Journal-Eintrag – die serverseitig angelegte, laufende Execution wird
  nicht zurückgesetzt und das Gerät bleibt dort belegt. Bewusst als Restrisiko akzeptiert
  (seltene Verkettung); eine spätere Ausbaustufe könnte den Idempotenz-Schlüssel nutzen, um
  einen evtl. angelegten Start gezielt per `finish`/`abort` abzuräumen.
- **Restrisiko Uhren-Drift ohne RTC (#54):** Der Plausibilitätscheck fängt eine offensichtlich
  zurückstehende Uhr ab; eine falsch VORgehende Uhr bleibt betrieblich über NTP/`fake-hwclock`
  abzusichern (siehe Deployment-Hinweis in `05-migration-plan.md`).

Herkunft: Pre-Launch-Review AP1 (Issues #16, #17, #18, #54, #59), Auftraggeber-Festlegungen
siehe ADR 0010 und `docs/kb/05-migration-plan.md`.
