# 2026-07-28 — Fernwartungs-Log sprengte die WebSocket-Frame-Grenze

**Anlass:** Der Auftraggeber setzte eine lokale Entwicklungsumgebung auf (Backend mit
Demo-Profil gegen eine lokale PostgreSQL auf abweichendem Port, dazu ein echter
Terminal-Client). Beim Betrieb des Clients fiel im Log ein Zyklus auf, der sich nicht
beruhigte:

```
Connected to the backend WebSocket.
Backend acknowledged HELLO: {locationId=2.0, locationName=Waschkueche Nord, …}
Backend WebSocket connection closed: CloseStatus[code=1009,
  reason=The decoded text message was too big for the output buffer …]
Scheduling a reconnect to the backend WebSocket in 5s.
```

Zuschnitt: **direkt bearbeitet, nicht delegiert** (AGENTS.md §4). Die Änderung liegt auf einer
gemeinsamen Wire-Naht von Terminal und Backend — die gedeckelte Nutzlast und die Frame-Grenze
beider Seiten müssen zueinander passen; ein einziger Kontext ist hier robuster als zwei
Übergaben.

## Der Befund: nicht die lokale Umgebung, sondern jedes Feldgerät

Drei je für sich harmlose Entscheidungen ergaben zusammen einen Fehler:

1. `TerminalWebSocketClient#buildLogPayload` schickte die **komplette** Logdatei
   (`Files.readAllLines`) als **einen** Text-Frame.
2. Weder Client noch Backend hoben die Frame-Grenze an — beidseitig galt der
   JSR-356-Default von **8 KiB** (im ganzen Repo kein `maxTextMessageBufferSize`).
3. Die logback-Konfiguration der Terminals (`setup.sh`, `res/logback.xml`) rollt **täglich
   ohne Größenlimit**.

Damit ist die Grenze nach wenigen Minuten Betrieb überschritten. Das ist **kein Artefakt der
Entwicklungsumgebung**: „Log anzeigen" war auf praktisch jedem Terminal im Feld unbenutzbar.

Zwei Verschärfungen, die den Fund von „Dialog bleibt leer" zu „Betriebsproblem" machen:

- **Der Abriss trifft die ganze Verbindung.** Nach dem `1009` sind auch Status, Neustart und
  die Zustellung der Offline-Vorfälle (ADR 0022) bis zum Reconnect tot.
- **Es schaukelt sich auf.** Jeder Fehlversuch schreibt neue Zeilen ins Log, die Datei wächst,
  der nächste Versuch scheitert wieder.

Der Auftraggeber hat den Befund am laufenden System bestätigt: nach Leeren der Logdatei und
Neustart funktionierte „Log anzeigen" — der Größenzusammenhang war damit belegt, bevor eine
Zeile Code entstand.

## Erledigt

### Deckelung an der Quelle (Terminal)

Neuer Helfer `Utilities#readLogTail(fileName, maxLines, maxBytes)` neben dem bereits dort
liegenden `getCurrentLogFile()`: liest nur den **Schwanz** der Datei (Sprung ans Ende statt
`readAllLines`, damit ein großes Log nicht komplett in den Speicher des Pi wandert), verwirft
die beim Sprung angeschnittene erste Zeile und deckelt zusätzlich per Byte-Budget — sonst
könnte eine Datei mit wenigen, sehr langen Zeilen die Grenze weiterhin sprengen. Wurde gekürzt,
ist die erste gelieferte Zeile ein sichtbarer Hinweis darauf; ein stillschweigend
unvollständiges Log wäre schlimmer als ein kurzes.

`TerminalWebSocketClient` nutzt ihn mit 1000 Zeilen / 128 KiB.

### Frame-Grenze beidseitig auf 1 MiB (Sicherheitsnetz)

Terminal über einen explizit konfigurierten `WebSocketContainer` für den
`StandardWebSocketClient`, Backend **je Session** in
`TerminalWebSocketHandler#afterConnectionEstablished`. Beide Konstanten verweisen aufeinander,
weil jede Seite nur ihren **eigenen** Empfangspuffer prüft.

Die Deckelung allein hätte nicht gereicht: auch 128 KiB liegen über den 8 KiB des Defaults.

### Entscheidung festgehalten

[ADR 0024](../architecture/0024-fernwartungs-log-deckelung-und-frame-grenze.md) — inklusive der
verworfenen Alternative („nur den Puffer hochsetzen", verschiebt die Grenze bloß) und der
bewusst akzeptierten, vom Auftraggeber freigegebenen Verhaltensänderung (das Portal zeigt bei
großen Logs nur noch das Ende).

## Tests

- **`UtilitiesLogTailTest`** (Client-Raspi, 6 Fälle): Zeilenlimit, Byte-Deckel bei wenigen sehr
  langen Zeilen, Verwerfen der angeschnittenen ersten Zeile, kleine Datei bleibt ungekürzt und
  ohne Hinweiszeile, fehlende/leere Datei.
- **`TerminalMaintenanceRealClientE2ETest#thePortalCanFetchTheLogOfAVerboseClientWithoutLosingTheConnection`**
  — der eigentliche Regressionstest, auf Wire-Ebene mit echtem Client-Prozess gegen echtes
  Backend: bläst die Logdatei des laufenden Terminals auf und fordert das Log an. Die Logdatei
  wird über das Arbeitsverzeichnis des Subprozesses **gefunden** statt über ihren Datumsanteil
  konstruiert (keine Datumslogik im Test); extern angehängt werden darf, weil logbacks
  `FileAppender` die Datei im Append-Modus offen hält.

**Gegenprobe gemacht:** Mit temporär zurückgedrehtem Produktivcode fällt der Test mit genau dem
gemeldeten Fehlerbild durch (`CloseStatus[code=1009 …]` im Backend-Log,
`TerminalRequestTimeoutException` im Test) — **und der nachfolgende Restart-Test gleich mit**.
Das ist der Beleg für die oben beschriebene Kaskade: eine Log-Anfrage nimmt die ganze
Fernwartung mit.

Suiten grün: **Backend 334/334**, **Client 116/116**, Cross-Component 4/4.

## Review-Gate

Das blockierende Gate fand vier Punkte, alle behoben:

1. **Kontext-Killer im Backend (schwer).** Die Frame-Grenze lag zuerst als
   `ServletServerContainerFactoryBean` in `TerminalWebSocketConfig` – container-weit, und die
   Bean verlangt einen **echten** Servlet-Container. In den Backend-Tests mit
   `@SpringBootTest(webEnvironment=MOCK)` gibt es keinen: `Attribute
   'jakarta.websocket.server.ServerContainer' not found in ServletContext`, **196 Fehler**. Die
   Cross-Component-Suite hatte das nicht gezeigt, weil sie mit `RANDOM_PORT` gegen echtes Tomcat
   läuft. Jetzt je Session im Handler – funktioniert in jeder Umgebung und gilt genau für diesen
   Endpunkt statt für jede WebSocket-Verbindung der Anwendung.
2. **Selbst eingebaute Flakiness im E2E (mittel).** Der Test prüfte, ob die **letzte** gelieferte
   Zeile die zuletzt angehängte Füllzeile ist. Der echte Client loggt aber nebenher weiter
   (Heartbeat, Offline-Abgleich) – zwischen Anhängen und Log-Anfrage kann eine eigene Zeile
   dazukommen. Ersetzt durch „Ende enthalten, Anfang weggekürzt, Größe gedeckelt": deterministisch
   und beweist dasselbe.
3. **Rollover-Kante beim Tail-Lesen (klein).** Rollt logback die Datei genau zwischen
   Größenabfrage und Lesen weg, bleibt der Puffer teilweise ungefüllt – der Rest wäre als
   NUL-Zeichen dekodiert worden. Jetzt wird nur der tatsächlich gelesene Teil dekodiert.
4. **Irreführende Hinweiszeile (klein, nutzersichtbar).** Sie las sich „max. 128 KiB von
   100 KiB", wenn nicht der Byte-Deckel, sondern das Zeilenlimit gegriffen hatte. Jetzt nennt sie
   zuerst die Dateigröße, dann die angewandten Grenzen.

## Offen / Nicht angefasst

- **Die logback-Konfiguration der Terminals bleibt unverändert** (tägliches Rollen ohne
  Größenlimit). Das Plattenwachstum auf dem Pi ist ein eigenes Thema; die Fernwartung ist jetzt
  unabhängig davon.
- **Kein Blättern im Log.** Wer mehr als das Ende braucht, holt die Datei weiterhin per SSH.
  Eine seitenweise Übertragung wäre eine Protokollerweiterung ohne aktuellen Bedarf.

## Referenzen

- [ADR 0024](../architecture/0024-fernwartungs-log-deckelung-und-frame-grenze.md),
  [ADR 0005](../architecture/0005-fernwartung-ueber-ausgehende-websocket-verbindung.md)
- [`docs/kb/03-modules.md`](../kb/03-modules.md) — Nachrichtenprotokoll, Abschnitt „Frame-Grenze"
