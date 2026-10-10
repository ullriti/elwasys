# 2026-10-11 — Terminal-WebSocket: Leerlauf-Wächter gegen die halboffene Verbindung

**Ziel:** Verhindern, dass die Fernwartungs-Verbindung eines Terminals dauerhaft stumm bleibt,
ohne dass der Client es merkt.

## Ausgangslage

Am 2026-09-29 um 20:31 schloss das Backend die WebSocket-Sitzungen **beider** Standorte
(`did not respond to heartbeat within PT1M30S`). Beide Terminals meldeten danach nie wieder
„connected": `operational` stand elf Tage auf 503, der Alarm (`EndpointProbeFailed`) lief
korrekt. Buchungen funktionierten die ganze Zeit über REST und das Offline-Journal.

Befund auf den Geräten (nur lesend):

- Client-Prozess lief, `backend.url` und `/etc/hosts` stimmten, das Backend war erreichbar.
- Die TCP-Verbindung stand auf `ESTABLISHED`, `lastsnd`/`lastrcv` = 963 330 s, also **seit dem
  Ausfall kein einziges Byte**.
- Im Client-Log **kein** „Backend WebSocket connection closed" und kein „Scheduling a
  reconnect" — `afterConnectionClosed` wurde nie aufgerufen.

## Ursache

Der Client war **rein reaktiv**: er antwortete auf `PING` und verließ sich darauf, dass ihn
ein Verbindungsabbruch über `afterConnectionClosed` erreicht. Das Schließen durch das Backend
muss das Terminal aber nicht erreichen (Verlust zwischen Backend und Gerät). Das Gerät hielt
die Verbindung für offen, das Backend hatte sie bereits abgeräumt.

Offen bleibt, **warum** die PONGs beider Standorte im selben Moment ausblieben (REST lief
weiter; Traefik und Backend wurden nicht neu gestartet).

## Erledigt

- `TerminalWebSocketClient`: Leerlauf-Wächter (`checkLiveness`). Kommt länger als 120 s keine
  Nachricht des Backends (es pingt alle 30 s), wird die Session als tot verworfen, best effort
  geschlossen und neu verbunden. Prüfung alle 30 s auf dem vorhandenen Reconnect-Scheduler;
  jede Ausnahme wird dort abgefangen, damit der periodische Task nie still stirbt.
- `afterConnectionClosed` ignoriert das verspätete Ende einer bereits abgelösten Session, damit
  die frisch aufgebaute Verbindung nicht ein zweites Mal neu aufgebaut wird.
- Aus dem Review-Gate übernommen: monotone Uhr (`System.nanoTime()`, ein Pi ohne RTC springt
  per NTP), Sitzungswechsel unter Lock, Nachzügler einer abgelösten Session verdecken die
  Stille nicht, `Throwable` statt `Exception` im periodischen Task, 60-s-Connect-Timeout.
- Regressionstest `TerminalWebSocketClientLivenessTest` (7 Tests, ohne Netz, ohne Sleeps).

## Entscheidungen

- **120 s Leerlauf-Grenze:** drei verpasste PINGs plus Puffer; ein einzelner Netz-Schluckauf
  löst keinen Neuaufbau aus, ein toter Kanal wird nach spätestens ~150 s ersetzt.
- **Kein eigener ausgehender Heartbeat:** nicht nötig, solange das Backend verlässlich pingt.

## Offen / nächster Schritt

- Backend-Seite: warum blieben beide PONGs gleichzeitig aus? Thread-Dump beim nächsten Vorfall;
  `TerminalHeartbeatScheduler` läuft auf dem gemeinsamen Spring-Scheduler.
- Der Wächter greift erst auf Geräten mit dem neuen Client-Jar (Release + Terminal-Update).
- Der Verbindungsaufbau hat jetzt ein 60-s-Timeout (`orTimeout`); eine nach dem Timeout doch noch
  zustande kommende Verbindung wird vom Guard in `afterConnectionClosed` abgefangen. Ob Tyrus
  selbst ein Connect-Timeout hat, wurde nicht verifiziert.

## Referenzen

- docs/kb/03-modules.md (Heartbeat), Client-Raspi/src/main/.../ws/TerminalWebSocketClient.java
