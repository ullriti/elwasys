<!--
Vorlage für die PR-Beschreibung — die Konventionen dahinter stehen in AGENTS.md §5.

Zur Benutzung:
- TITEL: dieselbe Conventional-Commits-Konvention wie ein Commit-Subject
  („fix(backend): …"), auf Englisch wie die Commit-Messages. Beim Squash-Merge
  wird der Titel zur Commit-Message.
- Ausfüllen, was zutrifft, den Rest LÖSCHEN. Eine leere Überschrift ist Rauschen.
- Der Text ist das, was ein Reviewer STATT des Diffs liest — so schreiben, dass
  die Änderung beurteilbar ist, ohne sie aus dem Code zu rekonstruieren.
- Fakten statt Adjektive: „Backend 315/315 (vorher 287)" schlägt „Tests grün".
-->

## Was

<!--
Was ist nach diesem PR im Repo anders — ein paar Sätze.
Bei einem größeren Arbeitspaket trägt eine Tabelle je Issue/Bereich besser als
Fließtext:
| Issue | Umsetzung |
|---|---|
-->

## Warum

<!--
Welches Arbeitspaket, welches Review-Finding, welcher Bug oder welche
Auftraggeber-Entscheidung das einlöst. Entscheidungen des Auftraggebers gehören
zusätzlich als ADR nach docs/architecture/ — hier dann verlinken.
-->

## Nutzersichtbare Änderungen

<!--
Alles, was Nutzerin, Betrieb oder API-Aufrufer merkt: Verhalten am Terminal und
im Portal, REST-API, Konfiguration, nötige Migrations- oder Deployment-Schritte,
alles was das Cutover-Runbook berührt. Neue Pflicht-Umgebungsvariablen und
geänderte Endpunkte gehören hierher.

„Keine — interner Umbau" ist eine gültige Antwort, aber sie gehört hingeschrieben
statt weggelassen. Was NICHT verhaltensneutral ist, steht hier ausdrücklich —
auch dann, wenn es eine Verbesserung ist, und auch dann, wenn es nur unter
seltenen Umständen sichtbar wird (anderes Locale, zweiter Fehler im Dialog,
Terminal statt Portal). Eine Abweichung in einer Fußnote ist eine übersehene
Abweichung.
-->

## Wie getestet

<!--
Welche Suiten gelaufen sind, mit Zahlen (AGENTS.md §3):
mvn install · Client-Raspi/run-ui-tests.sh · Client-Raspi/run-client-e2e.sh ·
Client-Raspi/run-cross-component-e2e.sh · backend/run-backend-tests.sh ·
backend/e2e (Playwright) · scripts/check-ai-docs.sh

Eine Tabelle „Suite | Ergebnis | vorher" macht den Zugewinn sichtbar.

Zwei Regeln, die sich hier verdient haben:

- Eine Suite, die NICHT laufen konnte, wird als nicht gelaufen BENANNT, mit
  Grund. Nie stillschweigend auslassen — CI läuft bei jedem PR, ein lokaler Lauf
  ersetzt sie nicht.
- Bei Zusagen zu Geld-/Abrechnungsintegrität, Nebenläufigkeit, Auth, Offline-
  Replay oder Standort-Isolation belegt ein grüner Test für sich genommen nichts.
  Berichte den absichtlichen Verstoß, den du das Gate ROT hast machen sehen —
  jeder Bugfix braucht ohnehin einen Regressionstest, der OHNE den Fix
  fehlschlägt (AGENTS.md §5). Ein Test, der auch gegen den zurückgedrehten Fix
  grün bleibt, sichert nichts ab.
- Tests sind deterministisch: kein `sleep`, kein Zufall, keine Wanduhr-Logik.
  Bleibt eine Stelle daran hängen, gehört das benannt.
-->

## Review-Gate

<!--
`/review` bzw. der `code-reviewer` ist ein blockierendes Gate vor dem Abschluss
(AGENTS.md §4). Was es gefunden hat und wie es aufgelöst wurde — zurückgewiesene
Findings gehören hierher MIT Begründung. Eine Zeile genügt, wenn nichts kam.
-->

## Sicherheitsrelevant

<!--
Berührte sicherheitsrelevante Bereiche: Auth/Login/Passwort-Reset, Terminal-
Token und Standort-Bindung, Fernwartungskanal, DB-Rollen (`elwaportal` als
einziger Anwendungs-DB-User), Secrets, öffentlich erreichbare Portal-Seiten vor
dem Login, neue Abhängigkeiten.

Bewusst akzeptierte Restrisiken gehören ebenfalls hierher, mit Fundstelle
(ADR/KB). Das Prinzip „Terminals greifen nicht direkt auf die DB zu" wird nicht
aufgeweicht — berührt der PR diese Naht, steht das hier.
-->

## Offene Punkte / für den Reviewer

<!--
Bewusst außerhalb des Scopes, bekannte Lücken, Folge-Issues, Entscheidungen, zu
denen du eine zweite Meinung willst. Auch: wie das Arbeitspaket gelaufen ist
(delegiert vs. direkt bearbeitet, gescheiterte Agenten), wo es für die Review
zählt.
-->

## Verknüpfte Issues

<!-- Closes #123 · Teil von #456 -->

## Checkliste

- [ ] Relevante Suiten aus AGENTS.md §3 grün — oder die Lücke oben benannt
- [ ] Review-Gate gelaufen, Findings behoben oder begründet zurückgewiesen
- [ ] Worklog-Eintrag, KB-„Current state", CHANGELOG und ggf. der Änderungslog in
      `docs/kb/05-migration-plan.md` gepflegt
- [ ] Commits und PR-Titel nach Conventional Commits, keine Secrets im Diff
