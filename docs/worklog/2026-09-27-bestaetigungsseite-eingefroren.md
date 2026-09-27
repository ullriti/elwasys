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

Damit ist die Kette geschlossen:

1. `onDeactivate()` setzte den Text der E-Mail-Checkbox auf den **Leerstring**.
2. JavaFX rechnet beim Layout einer Beschriftung die Kürzung mit Auslassungszeichen aus und
   fällt bei leerem Text in ein `substring(0, -1)` → Ausnahme **mitten im Layout-Durchlauf**.
3. `Parent.layout()` setzt vor `layoutChildren()` das Flag `performingLayout` und nimmt es
   erst danach zurück. Fliegt dazwischen eine Ausnahme, bleibt es stehen — jedes spätere
   `requestLayout()` dieses Teilbaums wird ab da stillschweigend verworfen.
4. `layoutLabelInArea` ist die Stelle, die den Text tatsächlich in den gezeichneten Knoten
   schreibt. Ohne Layout ändert sich auf dem Bildschirm nichts mehr, obwohl die
   Text-Properties weiterlaufen: **die Seite war eingefroren**.

Deshalb gab es auch nur *eine* Ausnahme — der defekte Pfad lief nie wieder. Drei Tage lang
zeigte die Bestätigungsseite Namen und Guthaben desselben Benutzers; mindestens ein weiterer
Bewohner hat dazwischen gebucht und es nicht gemeldet.

Zweiter, unabhängiger Mangel derselben Seite: `onActivate()` schaltet sie sichtbar, **bevor**
die Programme des neuen Benutzers geladen sind — dazwischen liegt ein Netzwerkaufruf. Bis
`selectProgram()` die neuen Werte schrieb, stand dort ebenfalls der vorige Benutzer.

Dass der Fehler nur auf der Konsole landete (`log/errout`), nicht im Anwendungs-Log, erklärt
die drei Tage: die Fernwartung (LOG_REQUEST) liefert `elwasys.log` aus, nicht `errout`.

## Erledigt

- `UiUtilities.setLabelText(StringProperty, String)` + `BLANK_LABEL_TEXT` (geschütztes
  Leerzeichen): an einer Beschriftung landet nie mehr ein leerer Text. Begründung ausführlich
  im Javadoc der Methode.
- `ConfirmationViewController`: alle beschriftungsgebundenen Properties starten mit dem
  Platzhalter statt mit `null` (der Push-Text war seit dem Entfernen der elwaApp-Kopplung
  dauerhaft `null`), `onDeactivate()` leert über `resetUserBoundFields()` **alle**
  benutzerbezogenen Anzeigewerte, und Benutzername + Guthaben stehen jetzt schon **vor** dem
  Laden der Programme auf dem Bildschirm statt erst danach.
- `Main`: `UncaughtExceptionHandler` für alle Threads und ausdrücklich für den JavaFX-Thread,
  der ins Anwendungs-Log schreibt — ab jetzt ist so ein Absturz über die Fernwartung sichtbar.
- Regressionstests: `ConfirmationPaneResetTest` (TestFX, echtes FXML; 3 von 4 Tests fallen
  gegen den Vor-Fix-Stand) und `UiUtilitiesLabelTextTest`.

## Entscheidungen

- **Platzhalter statt „Checkbox aus dem Layout nehmen".** `managed` ist im FXML an `visible`
  gebunden und lässt sich deshalb nicht setzen, und die Sichtbarkeit steuert eine CSS-Klasse,
  die eine Stilvorlage jederzeit wieder überschreibt. Ein nicht-leerer Text ist die Zusage,
  die unabhängig von Sichtbarkeit, Breite und Stil hält.
- **Zurücksetzen beim Abmelden, nicht nur „richtig neu setzen".** Solange die Seite im Fehler-
  oder Wartefall stehen bleibt, ist ein leeres Feld die einzig ehrliche Anzeige; eine fremde
  Identität mit fremdem Guthaben darf dort nie stehen.
- **Der Fehler-Handler gehört zum Fix, nicht als Beiwerk dazu.** Ein Fehler im JavaFX-Thread
  beendet die Anwendung nicht, kann die Oberfläche aber teilweise unbrauchbar machen. Ohne
  Log-Eintrag ist das aus der Ferne unsichtbar — genau das ist hier passiert.

## Offen / nächster Schritt

- **Das betroffene Terminal muss neu gestartet werden**, um die eingefrorene Seite zu lösen;
  der Fix wirkt erst mit dem nächsten ausgerollten Jar. Zum Zeitpunkt dieser Session lief dort
  eine Wäsche.
- Die Client-E2E-Suiten (`*E2ETest`) brauchen PostgreSQL und ein laufendes Backend und wurden
  lokal **nicht** ausgeführt; die CI deckt sie im PR ab. Lokal grün: 99 Nicht-E2E-Tests.
- Andere Oberflächen-Controller halten ebenfalls Beschriftungs-Properties, die mit `null`
  starten. Hier bewusst nicht mitgeändert (Umfang), aber derselbe Mechanismus — ein eigener
  Durchgang lohnt.

## Referenzen
- `Client-Raspi/src/main/org/kabieror/elwasys/raspiclient/ui/UiUtilities.java` (Javadoc =
  die ausführliche Fehlerbeschreibung)
- docs/kb/06-ui-tests.md, CHANGELOG.md
