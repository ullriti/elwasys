# 2026-07-27 — PR-Template aus der Baseline übernommen

**Ziel:** Die PR-Beschreibung nicht länger je Arbeitspaket neu erfinden. Das
Template entsteht in `agentic-baseline` aus den gemergten PRs dieses Repos und
aus adb-formularsystem; hier wird es auf Deutsch und auf den Projektzuschnitt
übernommen.

## Erledigt

- `.github/pull_request_template.md` angelegt (deutsch, Doku-Sprache nach
  AGENTS.md §0): Was · Warum · Nutzersichtbare Änderungen · Wie getestet ·
  Review-Gate · Sicherheitsrelevant · Offene Punkte · Verknüpfte Issues ·
  Checkliste. Die Anleitung steckt in HTML-Kommentaren, ein leerer PR rendert
  nur die neun Überschriften.
- `AGENTS.md` §5: Regel ergänzt, dass der PR-**Titel** derselben
  Conventional-Commits-Konvention folgt wie ein Commit-Subject (englisch, mit
  Typ), plus der Verweis auf das Template. Beide Punkte fehlten bisher ganz —
  §5 sagte nichts über PR-Beschreibungen.
- `AGENTS.md` §6: `.github/`-Zeile im Verzeichnis-Guide (PR-Template und die
  bestehenden Workflows `ci.yml`/`maven-publish.yml`).

## Entscheidungen

- **Projektspezifisch angepasst, nicht bloß übersetzt.** „Wie getestet" nennt die
  Suiten dieses Repos namentlich (`run-ui-tests.sh`, `run-client-e2e.sh`,
  `run-cross-component-e2e.sh`, `run-backend-tests.sh`, `backend/e2e`) und
  empfiehlt die Tabelle „Suite | Ergebnis | vorher", die sich in #74/#99 bewährt
  hat. „Sicherheitsrelevant" nennt Terminal-Token und Standort-Bindung, den
  Fernwartungskanal und die DB-Rollenhärtung statt einer generischen Liste.
- **Die Negativbeweis-Regel steht im Template-Text.** Bei Geld-/Abrechnungs-
  integrität, Nebenläufigkeit, Auth oder Offline-Replay zählt nicht der grüne
  Test, sondern der absichtliche Verstoß, den man das Gate rot machen sah. Das
  ist die ausformulierte Fassung von AGENTS.md §5 („jeder Bugfix einen
  Regressionstest, der ohne den Fix fehlschlägt") — in #99 fiel dabei auf, dass
  der `id DESC`-Tiebreak effektiv ungetestet war, weil die Tests mit
  Zeitstempeln aus verschiedenen Jahren arbeiteten und auch ohne den Fix grün
  gewesen wären.
- **„Verhalten bewahren" bewusst NICHT ins Template aufgenommen.** In einer
  ersten Fassung stand der Abschnitt „Nutzersichtbare Änderungen" unter der
  Rahmenbedingung des Auftraggebers, Verhalten dürfe sich nicht ändern. Das war
  eine Bedingung der **Migration** und ist mit deren Abschluss erledigt; als
  Dauerregel im Template hätte sie künftige Weiterentwicklung fälschlich unter
  Rechtfertigungsdruck gesetzt. Der Abschnitt fragt jetzt neutral nach dem, was
  Nutzerin, Betrieb und API-Aufrufer merken.

## Offen / nächster Schritt

- Issue-Templates (`.github/ISSUE_TEMPLATE/`: Bug · Arbeitspaket/Epic ·
  Review-Finding) sind angedacht, aber nicht Teil dieses Arbeitspakets. Für
  dieses Repo wäre der Review-Finding-Typ der wertvollste — Pre-Launch- und
  finale Review haben zusammen rund 40 Issues im gleichen, von Hand
  wiederholten Schema erzeugt.

## Referenzen

- Quelle des Musters: PRs #72, #73, #74, #76, #99 dieses Repos sowie
  `ullriti/adb-formularsystem` #2, #3, #5
- `agentic-baseline` PR #1 (Ursprung des Templates)
- `AGENTS.md` §4 (Arbeitsregeln), §5 (Konventionen), §7 (Guardrails)
