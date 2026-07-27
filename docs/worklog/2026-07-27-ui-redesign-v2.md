# 2026-07-27 — UI-Redesign v2 des Waschportals

**Ziel:** Die vom Auftraggeber gelieferte Design-Spezifikation
[`docs/specs/0002-ui-design/v2/`](../specs/0002-ui-design/v2/README.md) im
Vaadin-Flow-Portal umsetzen — Theme, Rahmen, Login, Dialoge, Dashboard und Listen —
und zwar im **vollen** Umfang: einschließlich der funktionalen Ergänzungen (Freitext-Filter,
sortierbare Spalten) und des Restzeit-Fortschrittsbalkens, die der Prototyp zusätzlich
zum reinen Umstyling zeigt.

Zuschnitt: vier parallele Arbeitspakete (Theme/Rahmen/Login · Dialoge · Dashboard ·
Listen) plus zwei Nachbesserungspakete aus dem Review-Gate.

## Erledigt

### Theme, Rahmen, Login (`fbaa3e5`, `c2c2dbb`)

- `backend/src/main/resources/portal-theme.css` auf v2 gehoben (Token-Deltas nach
  [`TOKENS.md`](../specs/0002-ui-design/v2/TOKENS.md)): weichere Radien, flachere Schatten,
  hellerer Inhaltsgrund, **outlined** statt grau gefüllte Eingabefelder, Grids ohne
  Zebra-Streifen und ohne Spaltentrenner, weiße Grid-Kopfzeile mit Versalien-Labels.
  Weiterhin ein zur Laufzeit injiziertes Stylesheet, **kein kompiliertes Theme** — die
  Lizenz-Rahmenbedingung aus [ADR 0019](../architecture/0019-ap6-vaadin-lizenz-restrisiko.md)
  gilt unverändert.
- Kopfbalken auf 64 px, mit Logo-Mark, **Name der aktuellen Ansicht** und Benutzer-Chip
  mit Initialen-Avatar; Seitenleiste als Pillen mit blauem Aktivzustand; Login als
  zweigeteilter Bildschirm (Markenfläche links, Formular rechts).
- Neue Komponente `ui/component/NavbarViewName`: leitet den Ansichtsnamen über
  `AfterNavigationObserver` aus den **vorhandenen** `@PageTitle`-Annotationen ab. Bewusst
  nicht `HasDynamicTitle` — das wertet Vaadin nur am Navigationsziel aus, es würde die
  Annotationen also ersetzen statt lesen und jede einzelne View anfassen (Begründung im
  Javadoc der Klasse).

### Dialoge (`674a1ab`)

- `AbstractFormDialog`: Schließen-Kreuz in der Kopfzeile, Kopf und Fuß stehen fest, nur
  der Inhalt scrollt.
- Gerät-, Programm- und Gruppen-Dialog in Abschnitte gegliedert (Stammdaten ·
  Gateway-Anbindung · Automatisches Ende · Zuordnung bzw. Stammdaten · Preis · Laufzeit ·
  Zuordnung). Feldsatz und Validierung unverändert.
- Programmtyp als **Segmented Control** (weiterhin die bestehende `RadioButtonGroup`, nur
  anders dargestellt — Bindung und Werte unberührt), Beträge mit €-Suffix.

### Dashboard (`f5730f3`, `5e29845`)

- Gerätekarten mit Statuspunkt, „Deaktiviert"-Chip, Kennzahlen-Panel (Programm / Nutzer /
  Restzeit) und **Fortschrittsbalken der Restzeit**.
- Verlaufs-Überschrift mit Anzahl, kompakte Historie, sortierbare Historien-Spalten —
  **echt sortiert**: die Sortierrichtung geht über `Sort` in die Datenbankabfrage
  (`toHistorySort`), nicht nur in die gerade geladene Seite. Der stabile Tiebreak
  `id DESC` aus AP5 bleibt erhalten.

### Listen (`2eea265`)

- Freitext-Filter je Liste sowie zusätzliche sortierbare Spalten. Die Vergleicher hängen
  an den **Rohwerten** (`setComparator(CreditAccountingEntryEntity::getAmount)` usw.), nicht
  am formatierten Anzeigetext — sonst sortierte „10,00 €" vor „9,00 €".

### Nachbesserungen aus dem Review (`cb4bf89`, `5e29845`)

- `ui/component/ListFilterField` fasst drei getrennt nachgebaute Filter-Implementierungen
  (`AbstractAdminListView`, `AdminOfflineIncidentsView`, `UserDashboardView`) zusammen. Sie
  sahen gleich aus, hatten aber **zwei verschiedene Suchsemantiken** — dasselbe Feld
  verhielt sich im selben Portal unterschiedlich. Jetzt eine Ausstattung, eine Semantik;
  die Komponente normalisiert dabei die geschützten Leerzeichen aus den Währungsformaten
  (U+00A0/U+202F), sonst fände „1,50 €", mit normaler Leertaste getippt, nichts.
- Sichtbarer Fokusring am Segmented Control, zugänglicher Name am Fortschrittsbalken,
  eindeutiger Name am Dialog-Schließen-Kreuz.
- Die doppelte `COUNT(*)`-Abfrage je Gerätekarte entfernt.

### Optische Abnahme

`backend/e2e/tests-shots/portal-shots.spec.ts` läuft über alle Seiten und Dialoge und legt
nummerierte Screenshots ab. Es liegt **bewusst außerhalb** von Playwrights `testDir`
(`./tests`), läuft also nicht im CI mit: es ist kein Regressionstest, sondern das Werkzeug
für die optische Abnahme durch den Auftraggeber. Es seedet seine eigenen Daten und räumt
sie wieder ab.

## Drei Fehler, die kein grüner Test gefunden hat

Der Grund, sie hier auszuschreiben: alle drei lagen in Bereichen, die die Suiten strukturell
nicht sehen. Zwei davon standen wörtlich so im gelieferten Entwurf.

1. **Weißer Kopfbalken.** Lumo legt auf die Navbar der primären Sektion einen Verlauf
   `linear-gradient(var(--lumo-contrast-5pct), …)`. In v1 war der Token halbtransparent, das
   Markenblau schien durch. v2 setzt ihn auf ein **deckendes** `#f8fafc` — der Verlauf
   übermalte den Kopfbalken damit vollständig weiß, samt der weißen Schrift darauf. Die
   Hintergrundfarbe allein genügt nicht, das Bild muss ausdrücklich weg
   (`background-image: none`). Gefunden **erst per Screenshot**; keine Test-Suite konnte das
   sehen, weil Texte, Struktur und Selektoren korrekt blieben.
2. **Segmented Control nicht bedienbar.** Der Programmtyp-Umschalter blendete den
   Radio-`<input>` per `display:none` aus. Das ist das fokussierbare Element der Komponente —
   der Umschalter war damit weder per Tastatur noch per Klick erreichbar. Jetzt deckt das
   Eingabefeld den ganzen Segmentknopf ab (transparent), statt zu verschwinden; nur der
   Radio-Punkt selbst wird flächenlos. Nachgezogen: der sichtbare Fokusring (WCAG 2.4.7) —
   ohne ihn wäre bei unsichtbarem Punkt UND unsichtbarem Eingabefeld nirgends zu sehen, wo
   der Fokus steht.
3. **Zeilen-Hover griff nie.** Die Regel
   `vaadin-grid::part(row):hover > [part~="body-cell"]` wurde vom Browser **schon beim
   Parsen verworfen**: nach der CSS-Shadow-Parts-Spezifikation darf auf `::part()` kein
   Kombinator in den Shadow-Baum hinein folgen. Sie stammte wörtlich aus dem Entwurf.
   Korrigiert auf `::part(body-row):hover` (Zellen transparent, damit die Zeilenfarbe
   durchscheint), im Browser nachgemessen.

Dazu ein vierter Punkt, der beim Aufräumen zunächst falsch eingeordnet wurde:
`AppLayout#setDrawerOpened(true)` war in diesem Branch neu hinzugekommen und sah wie ein
wirkungsloser Aufruf aus (auf dem Desktop steht die Leiste ohnehin offen). Es war
stattdessen eine **Regression auf schmalen Fenstern**: unterhalb von 900 px führt die
`AppLayout` die Leiste als Overlay und hält sie geschlossen — erzwungen offen verdeckte sie
nach dem Login zwei Drittel des Bildschirms. Wieder entfernt.

## Entscheidungen

- **Voller v2-Umfang (Auftraggeber-Entscheidung).** Umgesetzt wird nicht nur das Umstyling,
  sondern auch das, was der Prototyp funktional zusätzlich zeigt: Freitext-Filter,
  sortierbare Spalten **und** der Fortschrittsbalken der Restzeit. Der Prototyp stellt
  Letzteren ausdrücklich zur Disposition („wird der Balken nicht gewünscht, entfällt er
  ersatzlos", `MAPPING.md`) — er ist beauftragt.
- **Keine ADR.** Dieses Arbeitspaket setzt eine vom Auftraggeber **gelieferte**
  Design-Spezifikation um; die tragenden Entscheidungen (Vaadin Flow, Portal im Backend,
  Laufzeit-CSS statt kompiliertem Theme) stehen bereits in ADR 0003 und ADR 0019 und werden
  von v2 nicht berührt. Es entsteht kein neues Muster mit Breitenwirkung, keine
  Technologiewahl, keine geänderte Test-/Security-Strategie — die Entscheidung „voller
  Umfang inkl. Fortschrittsbalken" ist eine Umfangs-, keine Architekturentscheidung und ist
  hier plus im Änderungslog des Migrationsplans festgehalten.
- **Filter clientnah.** `ListFilterField` filtert auf dem bereits geladenen
  `ListDataProvider`. Alle drei Aufrufstellen laden ihre Zeilen ohnehin vollständig; ein
  Nachreichen in die Datenbankabfrage wäre Aufwand ohne Wirkung. Die Dashboard-Historie
  bleibt davon unberührt — sie ist lazy paginiert (AP5) und sortiert deshalb serverseitig.
- **Segmented Control bleibt eine `RadioButtonGroup`.** Der Prototyp lässt `MenuBar`/
  `ButtonGroup` als Alternative zu; das hätte Bindung und Validierung in Handarbeit
  bedeutet. Es ändert sich ausschließlich die Darstellung.

## Nutzersichtbare Änderungen

Dieses Arbeitspaket ändert bewusst das Erscheinungsbild — es ist kein „Verhalten bewahren"-
Umbau. Fachlich bleibt alles gleich: gleiche Views, gleiche Felder, gleiche Validierung,
gleiche Meldungen. Neu **bedienbar** sind der Freitext-Filter je Liste und die zusätzlichen
sortierbaren Spalten.

## Wirkung auf die E2E-Suite (Selektor-Falle)

`NavbarViewName` sorgt dafür, dass **jeder Ansichtsname ab sofort zweimal im DOM steht** —
einmal im Kopfbalken, einmal in der Seitenleiste bzw. als Seitenüberschrift. Jedes
`page.getByText('<Ansichtsname>', { exact: true })` läuft damit in Playwrights Strict Mode.
Genau das ist in diesem Branch bereits passiert (P15 in
`backend/e2e/tests/user-portal.spec.ts`, „Übersicht"); der Fall prüft jetzt direkt den
Menüpunkt (`vaadin-side-nav-item[path="user"]`) und mit `toContainText` statt `toHaveText`,
weil `vaadin-side-nav-item` zusätzlich ein verstecktes „Toggle child items"-Label mitführt.
Beides steht als Regel in [`../kb/06-ui-tests.md`](../kb/06-ui-tests.md), Abschnitt
„Vaadin-Flow-Selektoren" — sonst tritt derselbe Fehler bei jedem neuen Test wieder auf.

## Offen / nächster Schritt

- **Optische Abnahme durch den Auftraggeber** über den Screenshot-Durchlauf.
- Die Testabdeckung des Redesigns (Portal-E2E und Backend-Unit-Tests für die neuen
  Komponenten) entsteht in einem **parallelen Arbeitspaket** — die Suiten-Zahlen in der KB
  werden dort nachgezogen, nicht hier.
- Unverändert offen: die Generalprobe nach
  [Spec 0001](../specs/0001-finale-review.md) vor dem Feldeinsatz.

## Referenzen

- Spezifikation: [`docs/specs/0002-ui-design/v2/`](../specs/0002-ui-design/v2/README.md)
  (README · `MAPPING.md` · `TOKENS.md` · Prototyp · `portal-theme-v2.css`)
- [ADR 0003](../architecture/0003-portal-als-vaadin-flow-im-backend.md),
  [ADR 0019](../architecture/0019-ap6-vaadin-lizenz-restrisiko.md) (Laufzeit-CSS statt Theme)
- [`docs/kb/06-ui-tests.md`](../kb/06-ui-tests.md) (Selektor-Strategie, Portal-Design zur
  Laufzeit), [`docs/kb/03-modules.md`](../kb/03-modules.md) (Portal-Komponenten),
  [`docs/kb/05-migration-plan.md`](../kb/05-migration-plan.md) (Änderungslog)
- Vorgeschichte des Portal-Designs:
  [Worklog Portal-Design](2026-07-22-portal-design.md),
  [Worklog Dialog-Gerüst](2026-07-26-portal-dialog-geruest-und-fernwartungs-toolbar.md)
