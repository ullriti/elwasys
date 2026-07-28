# 2026-07-28 — Standort-Token-Verwaltung im Admin-Portal

**Ziel:** Der Auftraggeber wollte für ein weiteres Terminal ein neues Standort-Token anlegen
und fand dafür keine Stelle im Portal. Das war kein Versehen, sondern eine bewusste
Festlegung — die jetzt revidiert wurde. Die Token-Verwaltung kommt ins Portal.

Zuschnitt: ein Arbeitspaket, an den `portal`-Spezialisten delegiert (der Kern ist Vaadin-UI
plus Playwright-E2E; backendseitig fehlte nur eine Lese-Methode). ADR/KB/Worklog beim
Orchestrator.

## Die Vorgeschichte, weil sie den Ausschlag gibt

[ADR 0008](../architecture/0008-api-auth-standort-token-und-admin-session.md) hatte ein
Admin-UI in Aussicht gestellt. [ADR 0018](../architecture/0018-ap4-auth-security-entscheidungen.md)
nahm es sechs Tage zuvor (2026-07-22, Pre-Launch-Review AP4, Issue #43) wieder zurück:
„minimale Variante — Restrisiko dokumentieren, kein Code-Ausbau". Die Begründung war
Aufwand/Nutzen vor dem Launch bei geringer Eintrittswahrscheinlichkeit eines Token-Leaks.

In der Praxis hat sich das sofort gerächt. Der CLI-Weg verlangt Shell-Zugriff auf den
Backend-Host, Kenntnis des Profil-Aufrufs und der DB-Zugangsdaten — für einen Vorgang, der
zum normalen Betrieb gehört (Gerät in Betrieb nehmen, tauschen, Token nach Verdacht
rotieren). Damit machte ADR 0018 ausgerechnet die Rotations-/Widerrufs-Disziplin
unwahrscheinlicher, die dieselbe ADR als **prozessuale** Gegenmaßnahme zum akzeptierten
Restrisiko benennt. ADR 0018 hatte das Admin-UI ausdrücklich als „mögliche spätere
Ausbaustufe, falls sich der Betrieb anders entscheidet" offengehalten — genau dieser Fall.

## Erledigt

### Portal-UI (`719a4c0`)

- **`ui/admin/dialog/TerminalTokenDialog`**: Tokens eines Standorts auflisten (Id,
  Beschriftung, Erstellt, Zuletzt benutzt, Status-Badge), ein neues erzeugen (optionales
  Label) und widerrufen. Erreichbar über eine Zeilenaktion (Schlüssel-Symbol) in der
  Standortliste.
- **Das Klartext-Token wird genau einmal** in einem schreibgeschützten Feld gezeigt, mit
  ausdrücklicher Warnung. Kein Log, keine Notification, kein Grid-Inhalt; der Feldwert wird
  beim Schließen serverseitig geleert. Der `token_hash` wird nirgends angezeigt — er ist für
  den Bediener wertlos und wäre nur ein unnötig ausgestelltes Geheimnis.
- Kein Kopier-Knopf: das Portal führt an keiner Stelle clientseitiges JavaScript aus, und ein
  einzelner Clipboard-Aufruf wäre der Anfang davon.
- Backend unverändert bis auf `TerminalTokenService#findByLocation` (Lese-Methode auf der
  bereits vorhandenen Repository-Query). **Portal und CLI teilen sich denselben Service** —
  es gibt keine zweite Token-Erzeugungslogik.

### Nachbesserungen aus dem Review-Gate

Der `code-reviewer` hat das Gate zunächst **nicht** freigegeben. Behoben:

- **Keine Fehlerbehandlung auf beiden Schreibpfaden.** Es gibt projektweit keinen
  `ErrorHandler`; eine Ausnahme aus dem Klick-Listener landete in Vaadins
  `DefaultErrorHandler` — im Portal passierte sichtbar *nichts*. Schlimmer: nach einem
  Fehlschlag stand weiterhin das **vorherige** Token in der Anzeige, verwechselbar mit dem
  gerade gescheiterten Vorgang.
- **Fehlendes `setMaxLength(100)`** am Label-Feld (Spalte ist `VARCHAR(100)`). In Kombination
  mit dem vorigen Punkt hieß das: langes Label eingeben, klicken, nichts passiert.
- **Die sicherheitsrelevanteste Zusicherung war tautologisch.** Der E2E-Fall schloss den
  Dialog, öffnete neu und prüfte, dass keine Reveal-Box da ist — der Dialog wird aber pro
  Klick neu instanziiert, der Test wäre also auch ohne das Aufräumen grün geblieben. Genau
  die Behauptung aus Javadoc und KB war damit ungedeckt.
- Fehlender Sortier-Tiebreaker, verworfener `revoke()`-Rückgabewert, ein sachlich falsches
  „Warum" im Kommentar und zwei falsche Kommentare in E2E/Spec.

## Zwei Vaadin-Fallen und zwei Bestandsbugs, die daraus fielen

1. **`setDisableOnClick(true)` reaktiviert nicht.** Vaadins `DisableOnClickController` hängt
   nur einen Klick-Listener ein, der serverseitig `setEnabled(false)` setzt — es gibt kein
   Zurücksetzen nach dem Roundtrip. Ohne Gegenmaßnahme ließ sich pro Dialog genau **ein**
   Token erzeugen.
   Der Bestand nahm an zwei Stellen das Gegenteil an, und zwar nicht nur im Kommentar:
   `CreditTopUpDialog`s „Buchen" war nach einer **fehlgeschlagenen Validierung** tot — der
   Admin konnte den Betrag nicht mehr korrigieren und erneut absenden —, und
   `ExpiredExecutionsDialog`s „Abrechnen" nach einem Klick bis zum Neuöffnen. Beides mit
   behoben.
2. **`vaadin-grid`-Slot-Namen sind pro Grid eindeutig, nicht pro Dokument.**
   `vaadin-grid-cell-content-19` existiert einmal in *jedem* Grid der Seite. Solange eine
   Seite ein Grid zeigt, ist das harmlos; ein Grid **im Dialog** liegt aber über dem
   Listen-Grid dahinter, und die globale Neu-Lokalisierung traf zwei Elemente (Strict Mode).
   Die Grid-Helfer in `backend/e2e/tests/helpers.ts` nehmen deshalb einen optionalen `scope`
   (rückwärtskompatibel: ohne ihn bitweise das alte Verhalten).

Dazu ein dritter Fund, der den Test *falsch grün/rot* machte statt die Anwendung kaputt: ein
fehlender Synchronisationspunkt ließ Playwright gepoolte Zellknoten der noch nicht
aktualisierten Liste greifen, der Klick landete auf der Aktion der **falschen** Zeile. Jetzt
wartet der Helfer auf die neue Zeile über deren Accessible Name — ohne `waitForTimeout`.

## Entscheidungen

- **ADR 0018 (#43) revidiert** ([ADR 0024](../architecture/0024-standort-token-verwaltung-im-portal.md)):
  Auftraggeber-Entscheidung vom 2026-07-28. Geändert wird ausschließlich die
  **Verwaltbarkeit**, nicht das Token-Design — weiterhin nur SHA-256-Hash in der DB, kein
  `expires_at`, unveränderter Blast-Radius eines geleakten Tokens. Ebenfalls unverändert
  verworfen bleiben der reduzierte Snapshot-Kartendatenumfang und die eingeschränkte
  Guthabenabfrage (beide speisen die Offline-Fähigkeit des Terminals).
- **Dialog statt eigenem Menüpunkt.** Fachlich ist der Standort die Klammer, unter der ein
  Token gilt; ein eigener Menüpunkt hätte den Standort als Auswahlfeld nachbauen müssen.
  `AdminUsersView` macht es mit „Guthaben aufladen"/„Umsätze ansehen" genauso.
- **Kein `@RolesAllowed` am Dialog.** Er ist keine Route; der Schutz kommt über
  `AdminLocationsView` (`@RolesAllowed("ADMIN")` + Vaadins `NavigationAccessControl`,
  abgesichert durch `RouteAccessAnnotationsTest`). `@EnableMethodSecurity` ist projektweit
  nicht aktiv — eine Annotation am Service wäre wirkungslos gewesen, ein Alleingang dort das
  falsche Signal.
- **Die CLI bleibt.** `TerminalTokenCliRunner` und `deploy/cutover/02-issue-terminal-tokens.sh`
  sind der Weg für die Erstinbetriebnahme, bevor überhaupt ein Admin-Login existiert, und für
  automatisierte Abläufe.

## Nutzersichtbare Änderungen

**Neue Funktion** im Admin-Portal (kein „Verhalten bewahren"-Umbau — hier entsteht bewusst
Neues). Zusätzlich sind zwei Knöpfe im Bestand wieder bedienbar, die nach einem Klick tot
blieben (siehe oben).

## Offen / nächster Schritt

- Unverändert offen: die **Generalprobe** nach [Spec 0001](../specs/0001-finale-review.md)
  vor dem Feldeinsatz und die optische Abnahme des Designs v2.
- Bewusst nicht gebaut: Bearbeiten des Labels nach dem Anlegen (rein informativ), sowie
  Live-Updates der Token-Liste über den `UiBroadcaster` (es gibt kein `TokenChangedEvent`;
  der Dialog lädt nach jeder eigenen Aktion neu, wie `ExpiredExecutionsDialog`).

## Referenzen

- [ADR 0024](../architecture/0024-standort-token-verwaltung-im-portal.md) (revidiert
  [ADR 0018](../architecture/0018-ap4-auth-security-entscheidungen.md) #43, stellt
  [ADR 0008](../architecture/0008-api-auth-standort-token-und-admin-session.md) wieder her)
- [`docs/kb/03-modules.md`](../kb/03-modules.md) (Verwaltungspfade, Portal-Dialoge),
  [`docs/kb/04-build-and-run.md`](../kb/04-build-and-run.md) (Portal- und CLI-Weg),
  [`docs/kb/06-ui-tests.md`](../kb/06-ui-tests.md) (Selektor-Regel, P32/P33),
  [`docs/kb/08-test-plan.md`](../kb/08-test-plan.md),
  [`docs/kb/05-migration-plan.md`](../kb/05-migration-plan.md) (Restrisiken Auth & Security)
