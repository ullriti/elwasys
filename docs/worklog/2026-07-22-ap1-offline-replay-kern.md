# 2026-07-22 — AP1: Offline-/Replay-Kern (Pre-Launch-Fixes)

**Ziel:** Den zentralen Blocker-Cluster der Pre-Launch-Review beheben (Epic #66, AP1): der
Offline-/Replay-Pfad darf weder das Journal verklemmen noch Buchungen verlieren oder falsch
abrechnen. Ein PR gegen `master`, Issues #16, #17, #18, #54, #59.

## Erledigt

### Backend
- **#16 – Privilegierter Nachbuchungs-Pfad:** `ExecutionStartRequest` um ein `replay`-Flag
  ergänzt; `ExecutionController#start` überspringt bei einer Nachmeldung die fachlichen Wächter
  (Sperrung/Standort/Nutzbarkeit/Belegung/Guthaben). Eine Nachmeldung ist ein Fakt – negative
  Salden sind laut Auftraggeber-Festlegung (ADR 0010) zulässig. Ohne den Fix scheiterte ein
  fachlich abgelehnter Replay-Eintrag bei jedem Versuch und verklemmte das Journal dauerhaft
  (Idempotenz-Key wird nur bei Erfolg abgelegt). Live-Buchungen unverändert.
- **#18 – Zeitstempel-Invariante + Preis-Deckel:** `ExecutionService#stopExecution` erzwingt
  `stop ≥ start` (kein `stop < start`/0-€-Waschgang mehr in der DB); `getPrice` deckelt die
  Abrechnungsdauer beendeter Ausführungen auf `min(stop − start, maxDuration)` (keine
  Überberechnung bei langem Ausfall). Der reale End-Zeitstempel bleibt als Audit-Record
  erhalten – bewusst nur der Preis gedeckelt, nicht der gespeicherte Stop (hält den
  bestehenden Charakterisierungstest grün).

### Client (Terminal)
- **#17 – Replay-Robustheit:** `OfflineGateway#replay` neu: (a) Paar-Reihenfolge – ein START
  wird erst mit vorliegender Terminierung nachgemeldet (verhindert Verlust der gelernten
  Backend-Id und die Reaktivierung einer längst fertigen Maschine beim Neustart); (b)
  Dead-Letter – dauerhaft fachlich abgelehnte Einträge wandern in
  `offline-journal.jsonl.deadletter`, der Replay fährt fort; (c) Abbruch nur bei
  Kommunikationsfehler; (d) einzelnes `removeEntry` statt `clear()` (kein Verlust parallel
  hinzugekommener Enden); (e) NPE-Absicherung beim Auflösen der Id. Nachmeldung der Starts
  über den neuen `ApiClient#replayCreateExecution` (setzt `replay=true`).
- **#54 – Uhren-Plausibilität:** `OfflineGateway` wertet einen Snapshot als unbrauchbar, wenn
  `now < generatedAt` (Terminaluhr steht vor dem Snapshot-Zeitpunkt – Pi ohne RTC nach
  Stromausfall). Greift für alle Offline-Pfade (Login/Geräte/Buchung) und `hasUsableSnapshot`.

### Doku
- **#59** und die Restrisiken (#54) als Restrisiko in **ADR 0016** festgehalten (neue ADR für
  die Replay-Semantik, Zeitstempel-Invariante und den Preis-Deckel).
- ADR-Index, CHANGELOG, KB-„Aktueller Stand" und der Änderungslog in `05-migration-plan.md`
  (inkl. Deployment-Hinweis NTP/`fake-hwclock` für #54) mitgepflegt.

## Tests
- Backend: `ExecutionControllerOfflineReplayTest` um 2 #16-Fälle erweitert (Replay überspringt
  Wächter / Gegenprobe Live), `ExecutionServiceTest` um 2 #18-Fälle (Stop-Klemmung,
  Preis-Deckel). Volle Backend-Suite grün: **209 Tests, 0 Fehler**.
- Client: neuer deterministischer `OfflineGatewayReplayTest` (Paar-Reihenfolge/NPE,
  Dead-Letter + gültige Einträge, `clear()`-Race) und `OfflineGatewayClockPlausibilityTest`
  (#54). Fake-`ApiClient` ohne Netzwerk – kein Sleep/Zufall. 5 Tests grün.

## Offen / nächster Schritt
- AP2 des Epics (#19 deCONZ-Reconnect, #20 Locking …) als nächster PR.
- Client-E2E-Suiten (`ClientOfflineRobustnessE2ETest`, `ClientOfflineReplayIdempotencyE2ETest`)
  bleiben durch die Änderungen fachlich abgedeckt; in CI laufen sie mit Backend + Xvfb.

## Referenzen
- Epic: https://github.com/ullriti/elwasys/issues/66 ; Issues #16, #17, #18, #54, #59
- ADR: [docs/architecture/0016-offline-replay-haertung.md](../architecture/0016-offline-replay-haertung.md)
- Branch: `claude/issue-66-ap1-1azzkt`
