# 2026-07-22 — AP3: Geld-/Abrechnungs-Integrität (Pre-Launch-Fixes)

**Ziel:** Die Geld-/Abrechnungspfade des Mehrbenutzer-Backends gegen Nebenläufigkeits-Races
absichern und die Idempotenz-Naht härten (Epic #66, AP3). Ein PR gegen `master`, Issues
#20, #22, #29, #36, #41. Direkt bearbeitet (kohärente Backend-Naht ExecutionController ↔
IdempotencyService ↔ CreditService), nicht delegiert.

## Erledigt

### Backend
- **#20 – Locking der Geld-/Belegungspfade:** `UserRepository#findWithLockById`
  (`PESSIMISTIC_WRITE`) sperrt die Nutzer-Zeile für Start-Guthabencheck, `payout` und
  `payExecution`; der Finish-Pfad lädt die Ausführung frisch und gesperrt
  (`ExecutionRepository#findWithLockById`) statt der detachten Instanz. Neuer
  `AdvisoryLockService` serialisiert den Start-Pfad je Gerät per
  `pg_advisory_xact_lock(deviceId)` (Belegungsprüfung + Insert atomar). **Bewusst kein
  partieller Unique-Index** (hätte legitime Starts bei abgelaufenen „unfinished"-Executions
  blockiert und mit dem Replay-Pfad aus ADR 0016 kollidiert) – Begründung in ADR 0017.
- **#29 – Idempotenz-Race/Key-Länge:** `IdempotencyService#execute` lehnt Schlüssel > 64
  Zeichen früh mit 400 ab (`InvalidIdempotencyKeyException`) und serialisiert gleiche Schlüssel
  per `pg_advisory_xact_lock` – kein Doppel-Effekt, kein HTTP 500 mehr durch eine an der
  Unique-Constraint vergiftete Transaktion (der `DataIntegrityViolationException`-Notnagel
  entfällt).
- **#41 – Replay-Semantik:** Beim Replay wird der gespeicherte `operation`-Wert geprüft
  (Abweichung → 409, `IdempotencyKeyReusedException`); die Auflösung von `program`/`user`
  wanderte in den „neu"-Zweig, sodass ein Replay nach Löschung einer Referenz-Entität die
  gespeicherte Antwort liefert statt 404 (nur `device` bleibt außen – Standort-Scope/Auth).
- **#22 – Betragsvalidierung:** `CreditService#inpayment`/`#payout` weisen Betrag `<= 0` ab
  (`IllegalArgumentException`); `CreditTopUpDialog` meldet es zusätzlich als Feldfehler
  („Der Betrag muss größer als 0 sein.").
- **#36 – Benachrichtigung nach Commit:** `ExecutionController` publiziert ein
  `ExecutionNotificationEvent`; der neue `ExecutionNotificationListener` versendet erst per
  `@TransactionalEventListener`/`AFTER_COMMIT` (Muster wie `UiBroadcaster`) – Versand außerhalb
  der DB-Transaktion, Rollback unterdrückt die Mail.

### Doku
- Neue **ADR 0017** (Locking-Strategie, Advisory-Lock statt Unique-Index, Idempotenz-Härtung,
  AFTER_COMMIT-Benachrichtigung). ADR-Index, CHANGELOG, KB-„Aktueller Stand" mitgepflegt.

## Entscheidungen
- **Advisory-Lock statt hartem partiellem Unique-Index** für die Doppelbelegung (#20): Der
  Index hätte legitime Starts blockiert (abgelaufene, aber fachlich „unfinished" Executions)
  und mit dem privilegierten Replay-Pfad (ADR 0016) kollidiert; er hätte zudem eine
  Altdaten-Bereinigung erzwungen. Der Auftraggeber hatte hierzu eine 🧩-Entscheidung im Epic
  vermerkt; die Rückfrage wurde abgebrochen mit der Anweisung fortzufahren – umgesetzt wurde
  die empfohlene, schema-risikofreie Variante (User-Row-Lock + Advisory-Lock). Ein harter
  Constraint bleibt als spätere Ausbaustufe möglich.

## Tests
- Backend-Suite grün: **225 Tests, 0 Fehler** (`backend/run-backend-tests.sh`, lokales
  PostgreSQL). Neu: `ExecutionControllerConcurrencyTest` (Doppelstart → genau eine Ausführung;
  Doppel-Finish → einmal gebucht; paralleler Same-Key → alle 200, kein 500),
  `CreditServiceConcurrencyTest` (parallele Auszahlungen überziehen nie),
  `CreditServiceAmountValidationTest` (#22), `ExecutionNotificationListenerTest` +
  `ExecutionNotificationTransactionalTest` (#36 Commit/Rollback), erweiterte
  `ExecutionControllerIdempotencyTest` (#29 Key-Länge, #41 operation-Mismatch) und
  `ExecutionControllerOfflineReplayTest` (#41 Replay nach Löschung). Notification-Tests auf
  Event-Publikation umgestellt.
- Portal-E2E: `admin-crud.spec.ts` P8 um den Negativbetrag-Fall (#22) erweitert – 2 Tests grün
  (Playwright/Chromium, produktiver Backend-Build).

## Offen / nächster Schritt
- Restliche Pre-Launch-Arbeitspakete (AP4 ff. laut Epic #66) je als eigener PR.
- Review-Gate (`code-reviewer`) vor Abschluss.

## Referenzen
- Epic: https://github.com/ullriti/elwasys/issues/66 ; Issues #20, #22, #29, #36, #41
- ADR: [docs/architecture/0017-abrechnungs-integritaet-locking.md](../architecture/0017-abrechnungs-integritaet-locking.md)
- Branch: `claude/ap3-issue-66-a4ehu3`
