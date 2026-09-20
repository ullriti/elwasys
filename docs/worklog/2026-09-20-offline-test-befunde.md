# 2026-09-20 — Bewusster Offline- und Neustart-Test am Terminal

**Ziel:** Nach dem Ausrollen von 1.0.2 die Offline-Robustheit nicht mehr nur zufällig
beobachten (sie hatte sich am Vorabend während der N+1-Störung selbst bewiesen), sondern
kontrolliert prüfen — und die Frage klären, was ein Terminal-Neustart mit einer rein lokalen
Buchung macht.

**Aufbau:** Terminal Hilarenhaus per Paketfilter vom Backend getrennt
(`iptables -I OUTPUT -d <Backend-IP> -j DROP`), mit einem abgekoppelten Auftrag, der die Regel
nach 900 s auf jeden Fall wieder entfernt. Bewusst `DROP` statt `REJECT`: Pakete verschwinden
lautlos, wie bei einem echten Netz-, Router- oder Clusterausfall. Ein `REJECT` hätte sofort
„Verbindung abgelehnt" gemeldet — der freundlichere, aber unrealistischere Fall.

## Die Technik hat bestanden

| Messpunkt | Wert |
|---|---|
| Terminal erkennt den Ausfall | 16 s |
| Backend räumt die tote Sitzung ab | 113 s |
| Datenbank während des Ausfalls | unverändert — nichts verloren, nichts erfunden |
| Nachtrag nach Wiederkehr | 8 s, ohne Zutun |
| Doppelbuchung | keine (Idempotenz-Schlüssel 21 → 23, genau 1 neue Ausführung) |
| Dead-Letter-Datei | leer |

Die nachgetragene Buchung trägt ihren **Original-Zeitstempel**, nicht die Nachtragszeit — der
Ausfall verbiegt die Zeitachse nicht.

## Zwei Dinge, die ich fast als Fehler gemeldet hätte

**Die 113 s.** Aus Backend-Sicht stand die Verbindung zunächst noch, die Gesundheitsanzeige
blieb auf `UP`, und auf TCP-Ebene lagen unbestätigte Bytes in der Warteschlange. Das sah nach
einer Lücke aus („ein totes Terminal löst keinen Alarm aus"). Erst der Blick in den Code
zeigte den Herzschlag: alle 30 s ein Ping, Trennung nach 90 s ohne Antwort. Die Verzögerung ist
**konstruiert**, und die gemessenen 113 s liegen exakt im erwarteten Fenster.

**Der liegengebliebene Journal-Eintrag.** Nach dem Nachtrag blieb ein START im Journal und der
Replay meldete beharrlich „0 nachgemeldet". Auch das sah nach Hänger aus — ist aber Absicht:
Ein START ohne Terminierung wird zurückgehalten, sonst entstünde im Backend eine ewig offene
Geisterausführung (Paar-Atomizität, Issue #80).

**Lehre:** Beide Male hätte die Messung allein zur falschen Meldung geführt. Bei „sieht nach
Fehler aus" zuerst prüfen, ob das beobachtete Verhalten konstruiert ist.

## Die Bedienoberfläche hat nicht bestanden — der eigentliche Ertrag

**Es gab gar keine Offline-Anzeige.** Nicht übersehen: Im Oberflächen-Paket kam „offline"
ausschließlich in Kommentaren und in der Ausweichlogik vor. Kein Hinweistext, kein Banner,
kein Statuspunkt. Der Bewohner konnte es nicht sehen, weil es nichts zu sehen gab.

**Und jeder Bedienschritt kostete 10 s Totzeit.** `ApiClient.REQUEST_TIMEOUT` stand fest
verdrahtet auf 10 s, und die interaktiven Pfade fragten den längst bekannten Offline-Zustand
nicht ab — die Hintergrundabfrage war zum Zeitpunkt der ersten Buchung schon rund zehnmal
gescheitert. Zweimal auf die Millisekunde belegt (Journal-`clientTimestamp` → Fehlschlag im
Protokoll): **10,008 s** und **10,002 s**.

Technisch ging nichts verloren. Aber der Bewohner erlebt zweimal 20 s Stillstand ohne
Erklärung — und die wahrscheinlichste Reaktion darauf ist, die Karte noch einmal aufzulegen.
Genau das, was bei einem gestörten System am wenigsten hilft.

## Der Neustart-Test

Eine rein lokale Offline-Ausführung **überlebt einen Client-Neustart nicht.** Nach dem
Neustart kamen nur die beiden backend-bekannten Ausführungen zurück; die offline gebuchte
fehlte. Ursache: `ElwaManager` stellt laufende Ausführungen ausschließlich aus der
Geräteübersicht des **Backends** wieder her — eine noch nicht nachgemeldete Buchung hat dort
gar keine Nummer.

**Eine Vorhersage von mir war falsch.** Ich hatte gewarnt, die Steckdose bliebe dann unter
Strom. Sie blieb es nicht:

```
21:51:56 WARN [Maschine 3] Device has been powered on but there is no
         execution running. Switching it off now.
```

Es gibt einen Abgleich beim Start, der genau das abfängt. Die Warnung war unbegründet — und
das ist der Grund, den Test überhaupt zu fahren, statt aus dem Code zu schließen.

**Was wirklich kaputt war:** Der zurückbleibende START ist **unsterblich**. Der Replay
*überspringt* ihn (Paar-Atomizität), versucht ihn also nie — damit greift der eingebaute
Fehlversuchszähler nicht, und eine Alterung gibt es nicht. Er hätte dort dauerhaft gelegen,
alle 20 s folgenlos angefasst. Und die Wäsche war nirgends verzeichnet: keine Ausführung,
keine Abrechnung, kein Eintrag in der Historie — und **kein Hinweis darauf, dass etwas
fehlt**. Das Schlimmste an dem Befund ist nicht der liegengebliebene Eintrag, sondern die
Stille.

## Die Fixes

1. **Schnellfehler bei bekanntem Offline-Zustand.** `ApiClient` merkt sich die
   Erreichbarkeit: nach einem Kommunikationsfehler laufen Aufrufe mit 2 s statt 10 s, jede
   erfolgreiche Antwort (auch eine fachliche 4xx) hebt das wieder auf.
   **Mit Rückweg:** Alle 60 s läuft ein Versuch trotzdem mit vollem Zeitlimit. Ohne ihn gäbe
   es einen Zustand, aus dem der Client nicht mehr herausfände — ein erreichbares, aber
   dauerhaft langsames Backend würde jeden Schnellversuch reißen lassen und den
   Schnellfehler-Zustand immer wieder selbst bestätigen. Genau dieser Fall ist real
   aufgetreten: die N+1-Abfrage hielt das Backend am Leben, aber so langsam, dass die
   Terminal-Aufrufe in Zeitüberschreitungen liefen. Der Abstand ist mit Bedacht größer als das
   Abfrageintervall, damit der teure Vollversuch fast immer der Hintergrundabfrage zufällt und
   nicht einem wartenden Bewohner.
2. **Sichtbarer Offline-Hinweis** in der Werkzeugleiste des `medium`-Layouts, das auf beiden
   Terminals läuft. Bewusst neben den meist unsichtbaren Zurück-Knopf gelegt, damit er ohne
   zusätzliche Höhe auskommt — die Kopfzeile ist mit 40 px ohnehin knapp (offener
   Backlog-Punkt).
3. **Verwaiste STARTs werden beim Start aufgelöst** statt ewig zu kreisen: Dead-Letter plus
   gemeldeter Vorfall.
   **Bewusst wird NICHT nachgebucht.** Wann der Lauf endete, weiß niemand — der Client war ja
   weg. Ein erfundenes Ende („jetzt") würde bei zeitbasierten Programmen zu Lasten des
   Bewohners falsch abrechnen. Lieber sichtbar machen und die Verwaltung entscheiden lassen,
   als still verlieren oder ungefragt Geld buchen.

## Zu den Tests

Der Schnellfehler-Test misst **Zeit**, nicht nur den Zustandsschalter. Ohne den Fix ist der
Schalter nämlich korrekt gesetzt und der Aufruf dauert trotzdem 10 s — eine Prüfung nur auf
`isBackendUnreachable()` wäre grün gewesen, ohne das Problem zu erfassen. Rot-vor-grün belegt:
**10004 ms** ohne den Fix.

Die drei Tests zur Verwaisung unterscheiden bewusst Fälle, die gleich aussehen: verwaister
START, START zu einem noch laufenden Gerät, vollständiges Paar. Gegenprobe gefahren — eine
naive Umsetzung („alle übrig gebliebenen STARTs verwerfen") fällt bei **beiden** Wächter-Tests
durch, hätte also laufende Wäschen weggeräumt.

## Offen

- Das Backend bleibt blind für eine lokal laufende Offline-Ausführung, bis sie endet (bei
  „Normalwäsche" bis zu drei Stunden, weil die Leerlauf-Abschaltung frühestens nach
  `earliest_auto_end` greifen darf). Das ist die Kehrseite der Paar-Atomizität und wurde hier
  **nicht** angefasst — eine Zwischenmeldung „läuft lokal" wäre ein Entwurfsschritt, kein Fix.
- Der `small`-Layout hat keinen Offline-Hinweis; dort fehlt eine Werkzeugleiste, in die er
  ohne Umbau passen würde. Beide Terminals laufen auf `medium`.
