# 2026-07-23 — Finale Review vor dem Feldeinsatz, Teil A (Korrektheit)

**Ziel:** Abschließende Review nach der Modernisierung, Teil A laut
[Spec 0001](../specs/0001-finale-review.md): Zielerreichung (R1), kritische
Bugs/Schwachstellen mit frischen Augen (R2), Testabdeckung (R6).

## Erledigt

- Spec 0001 angelegt: Review-Plan mit 9 Tracks, Modell-Zuordnung (Hauptagent Fable,
  Tiefen-Tracks Opus, Breiten-Tracks Sonnet) und Generalprobe-Checkliste vor dem
  Feldeinsatz (`docs/specs/0001-finale-review.md`).
- **R1 Zielerreichung** (Report: `docs/reviews/final/R1-zielerreichung.md`): alle Ziele/
  Rahmenbedingungen erreicht, code-verifiziert; nur 3 niedrige Doku-/Kosmetik-Findings
  (2× nie geschlossene „Offene Fragen" im Migrationsplan, `SQLException`-Restspur im
  Terminal).
- **R2 Kritische Bugs** (Report: `docs/reviews/final/R2-kritische-bugs.md`): Geld-/
  Locking-, Auth- und Terminal-Concurrency-Pfade halten stand (keine Deadlocks, keine
  Umgehungen). **1 Hoch-Finding, vom Hauptagenten gegenverifiziert:** die
  Geister-Execution-Kompensation (#68/ADR 0021) greift nicht über Replay-Lauf-Grenzen –
  Kommunikationsabbruch zwischen nachgemeldetem START (sofort aus dem Journal entfernt)
  und FINISH macht den FINISH im Folgelauf zum Waisen → Execution bleibt serverseitig
  „laufend", Wiederaufnahme kann die fertige Maschine wieder einschalten
  (`OfflineGateway.java:329/342/345-350/410-415`).
- **R6 Testabdeckung** (Report: `docs/reviews/final/R6-testabdeckung.md`): Abdeckung
  insgesamt angemessen, kritische Pfade gründlich getestet; 1 Hoch-Finding (kein
  Regressionstest für deCONZ-WS-Reconnect #19), dazu mittel/niedrig: `Thread.sleep` in
  `CreditServiceAccountingHistoryTest`, Portal-E2E ohne DYNAMIC-Programmanlage,
  Flakiness-Risiko `InactivitySchedulerTest`, fehlender #68-Kompensations-Testfall.

## Entscheidungen

- Modell-Mix statt „alles Opus" (Budget: Wochenlimit ~60 % verbraucht) – in Spec 0001
  als Auftraggeber-Entscheidung dokumentiert.
- Findings werden erst nach Teil B gesammelt priorisiert und behoben (Spec: keine Fixes
  in den Review-Sessions).

## Offen / nächster Schritt

- **Teil B** (R3a–c Code-Qualität, R4 Doku, R5 Betrieb, R7 Repo-Hygiene) in einer
  Folge-Session (Prompt in Spec 0001), danach Synthese (`SYNTHESE.md`) + Arbeitspakete.
- Das R2-Hoch-Finding (Replay-Paar-Atomizität über Lauf-Grenzen) sollte vor dem
  Feldeinsatz behoben werden – Empfehlung und Regressionstest-Skizze stehen im Report.

## Referenzen

- docs/specs/0001-finale-review.md, docs/reviews/final/ (R1, R2, R6)
- ADR 0016/0017/0018/0021, docs/kb/05-migration-plan.md
