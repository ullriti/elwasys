# 17. Geld-/Abrechnungs-Integrität: pessimistisches Locking, Advisory-Locks, Idempotenz-Härtung

- **Status:** accepted
- **Datum:** 2026-07-22

## Kontext

Im Alt-System war je Gerät genau ein Einzelplatz-Client aktiv; Geld- und Belegungs-
entscheidungen konnten strukturell nicht nebenläufig auftreten. Das neue Mehrbenutzer-Backend
(Portal + n Terminals, gemeinsame REST-API) macht aus jeder ungeschützten
Read-then-Write-Sequenz eine reale Race. Die Pre-Launch-Review (Epic #66, AP3) hat fünf
zusammenhängende Befunde auf den Geld-/Abrechnungspfaden gefunden:

1. **#20 – kein Locking:** Guthaben-/Belegungsprüfungen waren ungeschützte Read-then-Write-
   Sequenzen unter READ_COMMITTED (kein `@Lock`/`@Version`/`FOR UPDATE`/Advisory-Lock im
   ganzen Backend). Folge: Doppelstart/Überbuchung eines Geräts, Doppelabrechnung beim
   parallelen `finish`, negatives Guthaben bei parallelen Auszahlungen.
2. **#29 – Idempotenz-Race → 500 / überlanger Key → Dauerschleife:** Zwei gleichzeitige
   Anfragen mit demselben Schlüssel konnten beide die Aktion ausführen; der zweite
   `saveAndFlush` vergiftete die Transaktion (→ HTTP 500 statt der behaupteten Antwort). Ein
   Schlüssel > `VARCHAR(64)` ließ erst `saveAndFlush` scheitern → die Operation blieb dauerhaft
   nicht persistierbar.
3. **#41 – Replay ignoriert `operation`/Ressourcen-Id:** Ein versehentlich wiederverwendeter
   Schlüssel lieferte die Fremd-Antwort und übersprang die neue Aktion; ein Replay nach
   Löschung von `user`/`program` scheiterte fälschlich mit 404 statt der gespeicherten Antwort.
4. **#22 – keine Betragsvalidierung:** Ein negativer/0-Betrag in `inpayment`/`payout` kehrte
   die Buchung um bzw. erzeugte einen leeren Buchungssatz in einem unveränderlichen Journal.
5. **#36 – Benachrichtigung in der Finish-Transaktion:** SMTP-/Pushover-Versand lief innerhalb
   der DB-Transaktion (blockierender SMTP-Server → gehaltene Locks; Rollback nach Versand →
   „fertig"-Mail zu einer nicht verbuchten Ausführung).

## Entscheidung

- **Pessimistisches Zeilen-Locking der Geldpfade (#20):** `UserRepository#findWithLockById`
  (`PESSIMISTIC_WRITE`) sperrt die Nutzer-Zeile in derselben Transaktion, in der geprüft und
  gebucht wird – für den Start-Guthabencheck, `CreditService#payout` und
  `CreditService#payExecution`. Der Finish-Pfad lädt die Ausführung frisch und gesperrt
  (`ExecutionRepository#findWithLockById`) statt die zuvor detacht geladene Instanz zu prüfen,
  sodass zwei parallele `finish` nicht beide `finished=false` sehen.
- **Advisory-Lock statt harter DB-Constraint für die Doppelbelegung (#20):** Der Start-Pfad
  serialisiert Belegungsprüfung + Insert per transaktionsgebundenem
  `pg_advisory_xact_lock(deviceId)` (`AdvisoryLockService`). **Bewusst kein partieller
  Unique-Index** `ON executions(device_id) WHERE finished=false AND start IS NOT NULL`: der
  hätte legitime Starts blockiert, solange eine abgelaufene, aber fachlich „unfinished"
  Alt-Execution auf dem Gerät liegt (die als *nicht* laufend gilt, siehe
  `getRunningExecution`), und er kollidierte mit dem privilegierten Replay-Pfad (ADR 0016), der
  einen zweiten, noch nicht gebuchten Offline-Start ausdrücklich zulässt. Der Advisory-Lock
  serialisiert nur, ohne persistente Bremse und ohne Altdaten-Bereinigung.
- **Idempotenz-Härtung (#29, #41):** `IdempotencyService#execute` (a) lehnt einen Schlüssel
  > 64 Zeichen früh mit 400 ab, **bevor** die Aktion läuft; (b) serialisiert gleiche Schlüssel
  per `pg_advisory_xact_lock` – von zwei gleichzeitigen Anfragen führt nur eine die Aktion aus,
  die zweite liefert die gespeicherte Antwort (kein Doppel-Effekt, kein 500; der frühere
  `catch (DataIntegrityViolationException)`-Notnagel entfällt); (c) prüft beim Replay, ob der
  gespeicherte `operation`-Wert passt (sonst 409). Die Auflösung von `program`/`user` wandert in
  den „neu"-Zweig, sodass ein Replay nach Löschung einer Referenz-Entität die gespeicherte
  Antwort liefert statt 404 (nur `device` bleibt außen – es liefert den Standort-Scope und ist
  die Authentifizierung des Aufrufs).
- **Betragsvalidierung (#22):** `CreditService#inpayment`/`#payout` weisen einen Betrag `<= 0`
  ab (`IllegalArgumentException`); der `CreditTopUpDialog` meldet es zusätzlich als Feldfehler.
  So sind UI-, REST- und Terminal-Pfade geschützt.
- **Benachrichtigung nach Commit (#36):** Der `ExecutionController` publiziert ein
  `ExecutionNotificationEvent`; der `ExecutionNotificationListener` versendet erst per
  `@TransactionalEventListener`/`AFTER_COMMIT` – dasselbe Muster wie `UiBroadcaster`. Der
  Versand liegt damit außerhalb der DB-Transaktion; ein Rollback unterdrückt die Mail.

## Konsequenzen

- Die Geld-/Belegungspfade sind gegen die konkreten Races abgesichert: genau ein Start je
  Gerät, genau eine Abrechnung je Ausführung, kein negatives Guthaben durch parallele
  Auszahlungen, kein Idempotenz-500. Belegt durch Nebenläufigkeits-ITs mit `CountDownLatch`.
- Die Sicherheit ruht weiterhin auch auf der **Single-Writer-Annahme** (ein Terminal je Gerät,
  ADR 0010): der Advisory-Lock schützt alle Code-Pfade, die ihn nehmen (Portal + Terminal-API),
  ist aber kein Ersatz für eine ausschließlich über den authentifizierten Terminal-Kanal
  setzbare Belegung. Ein harter DB-Constraint bleibt eine mögliche spätere Ausbaustufe, sobald
  der Altbestand nachweislich frei von „ewig unfinished" Executions ist.
- Advisory-Locks sind PostgreSQL-spezifisch. Die Testsuite läuft ohnehin gegen echtes
  PostgreSQL (Testcontainers bzw. lokaler Cluster, siehe `docs/kb/06-ui-tests.md`), das Backend
  setzt PostgreSQL/Flyway voraus (ADR 0006) – kein zusätzlicher Portabilitätsverlust.
- Kein Schema-Änderungsbedarf (Advisory-Locks sind Laufzeit) – keine neue Flyway-Migration.

Herkunft: Pre-Launch-Review AP3 (Issues #20, #22, #29, #36, #41), Auftraggeber-Festlegungen
siehe ADR 0010/0016 und `docs/kb/05-migration-plan.md`.
