# 2026-09-27 — Eingefrorene Bestätigungsseite: fremder Name und fremdes Guthaben bei der Buchung

**Ziel:** Einen Feldbefund aufklären — ein Bewohner legte seinen Chip auf und sah bei der
Buchung den Benutzernamen und das Guthaben eines *anderen* Bewohners, während die Buchung
selbst korrekt von seinem eigenen Konto abgebucht wurde.

## Ausgangslage

Gemeldet vom Betreiber für das Terminal Hilarenhaus. Die naheliegende Vermutung (vertauschte
Kartennummer, Dublette in `users.card_ids`) war schnell widerlegt:

- Die Karte gehört in der Datenbank eindeutig zu einem einzigen nicht gelöschten Benutzer;
  über den gesamten Bestand gibt es keine Dublette mit zwei aktiven Inhabern.
- Das Terminal-Log zeigt zur fraglichen Zeit den **richtigen** Namen
  (`Card detected: ******0559` → `User logged in: <richtiger Benutzer>`).
- Die entstandene Ausführung trägt die richtige Benutzer-Id, ebenso die Guthabenbuchung.
- Auch der Offline-Schnappschuss auf dem Gerät ordnet Karte, Name und Guthaben sauber zu.

Anmeldung, Berechtigung und Abrechnung waren also korrekt — **nur die Anzeige log**.

## Untersuchung

Name und Guthaben stehen gemeinsam nur auf **einer** Seite: der Bestätigungsseite
(`ConfirmationPane.fxml`, „Guthaben" und „Dein Benutzername im Waschportal"). In
`log/errout` des Geräts stand genau eine Ausnahme:

```
Exception in thread "JavaFX Application Thread"
java.lang.StringIndexOutOfBoundsException: begin -1, end 0, length 0
    at javafx.scene.control.skin.LabeledSkinBase.layoutLabelInArea(LabeledSkinBase.java:590)
    at javafx.scene.control.skin.CheckBoxSkin.layoutChildren(CheckBoxSkin.java:147)
    ...
    at javafx.scene.Scene$ScenePulseListener.pulse(Scene.java:2515)
```

Zeitstempel der Datei: **2026-09-24 18:29:53,70** — 0,75 s nachdem im Terminal-Log die
Buchung des *anderen* Benutzers begann (`Starting execution …` um 18:29:52,95). Das ist genau
der Übergang Bestätigungsseite → Geräteliste, also `onDeactivate()`. Checkboxen gibt es nur
auf dieser einen Seite.

### Erste Erklärung — und warum sie nicht reichte

Naheliegend war: `onDeactivate()` setzt den Text der E-Mail-Checkbox auf den Leerstring, und
ein leerer Beschriftungstext bringt das Layout zu Fall. Diese Erklärung hatte zwei Löcher.
Erstens setzt der Bestand an mehreren Stellen leere Texte, ohne dass je etwas passiert wäre.
Zweitens erklärte sie nicht, warum das **zweite** Terminal (gleicher Code, gleiche Auflösung,
Nutzer mit E-Mail-Adresse) nie abstürzte.

Das Review-Gate (`code-reviewer`) hat genau hier widersprochen: Zeile 590 in `LabeledSkinBase`
ist nicht die Kürzung mit Auslassungszeichen, sondern die **Mnemonic-Unterstreichung**.

### Gemessene Ursache

An JavaFX 17.0.20 nachgestellt (die Sonde ist als `ConfirmationPaneLayoutTest` im Repo
geblieben):

| Mnemonic-Parsing | Text | danach | Ergebnis |
|---|---|---|---|
| an | mit `_` | `""` | **Absturz** (`Range [-1, 0) out of bounds for length 0`) |
| an | mit `_` | Platzhalter | kein Absturz |
| an | ohne `_` | `""` | kein Absturz |
| aus | mit `_` | `""` | kein Absturz |

Es braucht also **beide** Bedingungen. Eine `CheckBox` hat Mnemonic-Parsing per Voreinstellung
an (ein `Label` nicht — deshalb sind die leeren Label-Texte im Bestand harmlos), sie deutet
einen Unterstrich im Text als Tastenkürzel. Der Text lautet `"Bei Fertigstellung Email an " +
Adresse`, und eine Abfrage über den Bestand zeigt: **genau ein aktiver Bewohner hat einen
Unterstrich in seiner Adresse** — derselbe, dessen Sitzung den Absturzzeitstempel trägt. Damit
ist auch das zweite Terminal erklärt: dort hat niemand einen Unterstrich in der Adresse.

Als Nebenwirkung wurde die Adresse bis dahin **verstümmelt angezeigt**: Mnemonic-Parsing
schluckt den Unterstrich (`max_muster@…` → `maxmuster@…`). Im Test festgehalten.

### Die Wirkung

`Parent.layout()` setzt vor `layoutChildren()` das Flag `performingLayout` und nimmt es erst
danach zurück. Fliegt dazwischen eine Ausnahme, bleibt es stehen — jedes spätere
`requestLayout()` dieses Knotens wird ab da stillschweigend verworfen. Und weil
`layoutLabelInArea` die Stelle ist, die den Text tatsächlich in den gezeichneten Knoten
schreibt, bleibt die Anzeige stehen, während die Properties weiterlaufen.

**Wie weit das reicht, ist nachgemessen und hängt vom Zustand der Vorfahren ab:** war ein
Vorfahre im selben Durchlauf nur `DIRTY_BRANCH`, bleibt allein das Bedienelement hängen; war er
`NEEDS_LAYOUT` (z. B. nach einer Änderung der Kinderliste, wie sie `onDeactivate()` auslöst),
bleibt der ganze Zweig darunter stehen. Im Feld liefen die Gerätekacheln nachweislich weiter
(Bildschirmfoto mit aktueller Restlaufzeit) — die gesamte Oberfläche war also **nicht** tot.
Welchen Bildausschnitt der Bewohner genau gelesen hat, ist damit nicht abschließend belegt;
belegt ist der Absturz, sein Zeitpunkt und dass er genau diese Seite trifft.

Es gab auch nur *eine* Ausnahme — der defekte Pfad lief nie wieder.

Zweiter, unabhängiger Mangel derselben Seite: `onActivate()` schaltet sie sichtbar, **bevor**
die Programme des neuen Benutzers geladen sind — dazwischen liegt ein Netzwerkaufruf. Bis
`selectProgram()` die neuen Werte schrieb, stand dort ebenfalls der vorige Benutzer.

Dass der Fehler nur auf der Konsole landete (`log/errout`), nicht im Anwendungs-Log, erklärt
die drei Tage: die Fernwartung (LOG_REQUEST) liefert `elwasys.log` aus, nicht `errout`.

## Erledigt

- `ConfirmationPane.fxml`: **`mnemonicParsing="false"`** an beiden Benachrichtigungs-Checkboxen.
  Nimmt dem Absturz den Pfad und zeigt die Adresse wieder vollständig an. Ein Touch-Terminal
  ohne Tastatur hat für Tastenkürzel ohnehin keine Verwendung; `ui/small` setzt das an seinen
  Schaltflächen seit jeher so.
- `UiUtilities.setLabelText(StringProperty, String)` + `BLANK_LABEL_TEXT` (geschütztes
  Leerzeichen): nimmt dem Absturz die zweite Bedingung. Beide Sicherungen sind einzeln
  wirksam (Messung oben), die Testmatrix steht im Javadoc der Methode.
- `ConfirmationViewController`: alle beschriftungsgebundenen Properties starten mit dem
  Platzhalter statt mit `null` (der Push-Text war seit dem Entfernen der elwaApp-Kopplung
  dauerhaft `null`), `onDeactivate()` leert über `resetUserBoundFields()` **alle**
  benutzerbezogenen Anzeigewerte, und Benutzername + Guthaben stehen jetzt schon **vor** dem
  Laden der Programme auf dem Bildschirm statt erst danach.
- `Main`: `UncaughtExceptionHandler` für alle Threads und ausdrücklich für den JavaFX-Thread,
  der ins Anwendungs-Log schreibt — ab jetzt ist so ein Absturz über die Fernwartung sichtbar.
- Regressionstests: `ConfirmationPaneLayoutTest` (fährt die Absturzfolge auf dem echten FXML,
  hält `mnemonicParsing == false` fest, misst die Bedingungsmatrix und prüft, dass der
  Unterstrich nicht mehr verschluckt wird — gegen den Vor-Fix-Stand 2 von 3 rot),
  `ConfirmationPaneResetTest` (3 von 3 rot) und `UiUtilitiesLabelTextTest`.

## Entscheidungen

- **Beide Sicherungen, nicht eine.** `mnemonicParsing="false"` beseitigt die Ursache an dieser
  Seite; der Platzhalter schützt auch dort, wo künftig jemand wieder ein Bedienelement mit
  Mnemonic-Parsing einbaut. Jede für sich wurde als wirksam gemessen.
- **Zurücksetzen beim Abmelden bleibt — als Hygiene, nicht als Fehlerbehebung.** Der
  Detailbereich hängt an der Style-Klasse `program-selected`, ist im fraglichen Fenster also
  unsichtbar; die Änderung ist damit **nicht nutzersichtbar**. Werte eines anderen Benutzers in
  einem Zustand stehen zu lassen, der sie nicht zeigen soll, bleibt trotzdem falsch — erst
  recht auf der Seite, die genau damit aufgefallen ist.
- **Kein automatischer Neustart bei einem Fehler im JavaFX-Thread.** Das Review hat ihn
  vorgeschlagen (`ElwaManager.instance.restart()`), begründet mit „die gesamte Szene ist tot".
  Die Messung zeigt das so nicht: im Feld liefen Kacheln und Restlaufzeit weiter. Ein Neustart
  bei *jeder* Oberflächen-Ausnahme ist an einem Gerät mit laufender Wäsche ein größerer
  Eingriff als der Fehler, den er heilen soll (Neustart-Schleifen, Abbruch während einer
  Buchung). Der Handler loggt; ein gezielter Wächter auf dauerhaft `needsLayout` wäre der
  nächste Schritt, wenn so etwas wieder auftritt.
- **Der Fehler-Handler gehört zum Fix, nicht als Beiwerk dazu.** Ein Fehler im JavaFX-Thread
  beendet die Anwendung nicht, kann die Oberfläche aber teilweise unbrauchbar machen. Ohne
  Log-Eintrag ist das aus der Ferne unsichtbar — genau das ist hier passiert.

## Offen / nächster Schritt

- **Das betroffene Terminal muss neu gestartet werden**, um die eingefrorene Seite zu lösen;
  der Fix wirkt erst mit dem nächsten ausgerollten Jar. Zum Zeitpunkt dieser Session lief dort
  eine Wäsche.
- Die Client-E2E-Suiten (`*E2ETest`) brauchen PostgreSQL und ein laufendes Backend und wurden
  lokal **nicht** ausgeführt; die CI deckt sie im PR ab. Lokal grün: 99 Nicht-E2E-Tests.
- Andere Oberflächen-Controller setzen ebenfalls leere Beschriftungstexte
  (`ToolbarPaneController`, `ui/small`). Geprüft: dort hängen ausschließlich `Label` ohne
  Mnemonic-Parsing — **kein** Absturzrisiko, deshalb bewusst nicht angefasst.
- Die Gerätekachel zeigt den Namen des letzten Benutzers unter einem Personen-Icon ohne
  Beschriftung, optisch identisch mit der Anmelde-Anzeige in der Werkzeugleiste. Das war die
  erste Fehlspur beim Aufklären und ist für Bewohner missverständlich — eigener kleiner PR.
- Der Änderungslog in `docs/kb/05-migration-plan.md` ist seit 2026-07-27 nicht mehr gepflegt
  (auch die September-Einträge fehlen dort). Kein Rückstand dieser Session, aber ein offener
  Aufräumpunkt.

## Referenzen
- `Client-Raspi/src/main/org/kabieror/elwasys/raspiclient/ui/UiUtilities.java` (Javadoc =
  die ausführliche Fehlerbeschreibung)
- docs/kb/06-ui-tests.md, CHANGELOG.md
