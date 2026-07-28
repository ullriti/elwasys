# 24. Fernwartungs-Log: gedeckelte Übertragung und angehobene WebSocket-Frame-Grenze

- **Status:** accepted
- **Datum:** 2026-07-28

## Kontext

Beim Aufsetzen einer lokalen Entwicklungsumgebung fiel auf, dass die Fernwartung
(„Log anzeigen" im Portal, ADR [0005](0005-fernwartung-ueber-ausgehende-websocket-verbindung.md))
die Verbindung des Terminals abreißen lässt:

```
Backend WebSocket connection closed: CloseStatus[code=1009,
  reason=The decoded text message was too big for the output buffer
         and the endpoint does not support partial messages]
```

Ursache ist das Zusammenspiel dreier Einzelentscheidungen, von denen jede für sich harmlos aussah:

1. **Das Terminal schickte die KOMPLETTE Logdatei** als Antwort auf `LOG_REQUEST` – ein
   `Files.readAllLines()` über die gesamte Datei, verpackt in **einen** WebSocket-Text-Frame
   (`TerminalWebSocketClient#buildLogPayload`).
2. **Keine der beiden Seiten hob die Frame-Grenze an.** Es galt beidseitig der
   JSR-356-Default von **8 KiB**.
3. **Die logback-Konfiguration der Terminals rollt nur täglich, ohne Größenlimit**
   (`Client-Raspi/setup.sh`, `Client-Raspi/res/logback.xml`).

Damit ist die Grenze nach wenigen Minuten Terminal-Betrieb überschritten – **„Log anzeigen" war
auf praktisch jedem Feldgerät unbenutzbar**, nicht nur in der Entwicklungsumgebung.

Verschärfend kommt hinzu:

- **Der Abriss trifft die ganze Verbindung, nicht nur die Log-Anfrage.** Nach dem `1009` sind
  auch Status, Neustart und die Zustellung der Offline-Vorfälle (ADR
  [0022](0022-dead-letter-sichtbarkeit.md)) bis zum Reconnect tot. Der Regressionstest zeigt
  genau das: ohne den Fix fällt auch der nachfolgende Restart-Test durch.
- **Es schaukelt sich auf.** Jeder Fehlversuch schreibt neue Zeilen ins Log, die Datei wächst,
  der nächste Versuch scheitert wieder.

## Entscheidung

**Die Log-Antwort wird an der Quelle gedeckelt, und die Frame-Grenze wird beidseitig angehoben.**
Beides zusammen – die Deckelung allein genügt nicht, weil auch ein gekürztes Log über den
8 KiB des Defaults liegt.

1. **Deckelung an der Quelle (Terminal): die letzten 1000 Zeilen, hart begrenzt auf 128 KiB.**
   `Utilities#readLogTail` liest nur den **Schwanz** der Datei (Sprung ans Ende statt
   `readAllLines`), damit eine sehr große Logdatei nicht komplett in den Speicher des Pi wandert.
   Der Byte-Deckel greift zusätzlich zum Zeilenlimit – sonst könnte eine Datei mit wenigen, dafür
   sehr langen Zeilen die Grenze weiterhin sprengen. Die beim Sprung angeschnittene erste Zeile
   wird verworfen statt halbiert ausgeliefert.
   - **Warum das Ende und nicht der Anfang:** Für die Diagnose zählen die letzten Ereignisse; der
     Dialog im Portal zeigt sie ohnehin unten.
   - **Warum überhaupt kürzen statt nur den Puffer hochzusetzen** (die erwogene Alternative):
     Ein höherer Puffer verschiebt die Grenze nur. Ein Tageslog eines gesprächigen Terminals
     erreicht auch 16 MiB, und dann ginge diese Datenmenge zusätzlich über eine oft schwache
     Anbindung im Waschkeller.

2. **Die Kürzung ist sichtbar.** Wurde gekürzt, ist die erste gelieferte Zeile ein Hinweis
   darauf (`--- gekürzt: nur das Ende der Logdatei … ---`). Ein stillschweigend unvollständiges
   Log wäre schlimmer als ein kurzes: der Admin würde aus dem Fehlen einer Zeile falsche
   Schlüsse ziehen.

3. **Frame-Grenze beidseitig auf 1 MiB** – Terminal über einen explizit konfigurierten
   `WebSocketContainer` für den `StandardWebSocketClient`, Backend über ein
   `ServletServerContainerFactoryBean`. Das ist das **Sicherheitsnetz**, nicht der eigentliche
   Fix: Es hält Abstand zur gedeckelten Nutzlast und deckt künftige Nachrichtentypen ab, statt
   das Protokoll dauerhaft einen einzigen Frame vor dem Abriss zu betreiben.
   - Beide Werte müssen zusammenpassen, weil **jede Seite nur ihren eigenen Empfangspuffer
     prüft** – deshalb stehen sie in beiden Klassen mit gegenseitigem Verweis.
   - Nur Text-Frames; dieses Protokoll kennt keine Binär-Frames.

## Konsequenzen

**Positiv**

- „Log anzeigen" funktioniert wieder – und zwar erstmals zuverlässig auf einem Gerät, das
  länger als ein paar Minuten läuft.
- Die Fernwartung insgesamt (Status/Neustart/Vorfallsmeldungen) bricht nicht mehr als
  Kollateralschaden einer Log-Anfrage weg.
- Die Antwortgröße ist jetzt **beschränkt und vorhersagbar**, unabhängig davon, wie lange ein
  Terminal schon läuft.

**Negativ / Restrisiken**

- **Nutzersichtbare Verhaltensänderung** (bewusst, vom Auftraggeber freigegeben): Bei einem Log
  über 1000 Zeilen zeigt das Portal nur noch dessen Ende. Vorher zeigte es theoretisch alles –
  praktisch scheiterte der Abruf in genau diesen Fällen, es geht also keine funktionierende
  Fähigkeit verloren. Wer das ganze Log braucht, holt es weiterhin per SSH vom Gerät.
- **Die Grenze ist weiterhin endlich.** Ein einzelner Log-Eintrag über 128 KiB (etwa ein sehr
  großer Stacktrace) würde beschnitten. Bewusst akzeptiert – der Byte-Deckel ist genau dafür da.
- **Die logback-Konfiguration bleibt unangetastet** (tägliches Rollen ohne Größenlimit). Das
  Plattenwachstum auf dem Pi ist ein eigenes Thema und wird hier nicht mitentschieden; die
  Fernwartung ist jetzt unabhängig davon.

## Referenzen

- ADR [0005](0005-fernwartung-ueber-ausgehende-websocket-verbindung.md) (Fernwartung über die
  ausgehende WebSocket-Verbindung), ADR [0022](0022-dead-letter-sichtbarkeit.md) (weitere
  Nutzlast auf demselben Kanal)
- [`docs/kb/03-modules.md`](../kb/03-modules.md) – Nachrichtenprotokoll des Terminal-WebSockets
- Regressionstests: `UtilitiesLogTailTest` (Client-Raspi),
  `TerminalMaintenanceRealClientE2ETest#thePortalCanFetchTheLogOfAVerboseClientWithoutLosingTheConnection`
