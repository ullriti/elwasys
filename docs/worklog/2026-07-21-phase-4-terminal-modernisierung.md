# 2026-07-21 — Phase 4: Terminal-Modernisierung (AP1–AP6)

**Ziel:** Die Raspberry-Pi-Terminals (JavaFX-Client) modernisieren und vom direkten
Datenbankzugriff lösen: Unterbau aktualisieren, den Client auf die REST-API + Standort-Token
umstellen, die Fernwartung auf eine ausgehende WebSocket-Verbindung umdrehen und
Offline-Robustheit einbauen – Bedienfluss und nutzer-sichtbares Verhalten unverändert.

## Erledigt
- **AP1 – Sicherheitsnetz ausbauen**: neuer `DeconzSimulator` (JDK-`HttpServer` + minimaler
  RFC-6455-WebSocket, keine neue Abhängigkeit) bildet den deCONZ-Gateway nach, analog zum
  `FhemSimulator`. 3 neue deCONZ-E2E-Klassen (C2–C5, C11, C12 als deCONZ-Pendants) +
  `ClientSmallUiSmokeE2ETest` (erste `ui/small`-Abdeckung, 320×240). Befund (kein Bug):
  `ui/small` hat einen zu `ui/medium` umgekehrten Bedienfluss (erst Gerätewahl, dann Karte).
  Kein Produktivcode geändert. UI-Suite 37→46, Client-E2E 19→28.
- **AP2 – Client-Unterbau modernisieren**: JavaFX 20 → **23.0.2** (höchste noch Java-21-
  lauffähige Version), SLF4J → 2.0.18, Logback → 1.5.38. Befund: die Gateways nutzten
  bereits `java.net.http`/Spring-WS bzw. Telnet – keine Codeänderung nötig; nur der (tote)
  elwaApp-Push-Zweig in `ExecutionFinisher` auf `java.net.http`+Gson migriert. `unirest`,
  HttpComponents und `org.json` aus `Client-Raspi/pom.xml` entfernt. Suiten unverändert.
- **AP3 – Backend-Vorbereitung (additiv, API v1)**: Inventur aller `DataManager`-/Direkt-
  DB-Aufrufe des Clients gegen die REST-API (13 Zugriffspunkte). Neuer anonymer
  `GET /api/v1/devices/overview` (Geräteauswahl vor Kartenlogin, Wiederaufnahme-Scan/C13);
  Idempotenz-Schlüssel (`Idempotency-Key`-Header, Migration `V4`) + optionaler
  `clientTimestamp` für alle vier Execution-Endpunkte; `GET /api/v1/snapshot` (Standort-Daten
  ohne Passwort-Hashes) für Offline. `NotificationService` an `finish`/`abort` angebunden
  (weiter hinter Flag, Default AUS). Zwei Fallstricke behoben. 22 neue Tests → 195/195.
- **AP4 – Client-Cutover auf die REST-API (Kernstück)**: neue `api/ApiClient`- +
  `model/Client*`-Schicht ersetzt `Common.DataManager` in `ElwaManager`/`ExecutionManager`/
  `ExecutionFinisher`/UI-Controllern; `elwasys.properties`/`setup.sh` fragen `backend.url`/
  `backend.token` statt DB-Zugangsdaten ab; Benachrichtigungsversand aus dem Client entfernt
  (Backend versendet zentral). Fernwartungs-Registrierung bleibt planmäßig bis AP5 auf dem
  Alt-DB-Pfad. **Kernfund**: die Testharness baute den Backend-Testjar ohne `-Pproduction`
  → Vaadins Online-Lizenzcheck riss nach ~60s den Spring-Kontext ein (Zeitbombe, erklärte
  das deterministische Wegbrechen ab der 7. Testklasse). Fix: `-Pproduction`. Client-E2E
  **28/28** (keine Tests verloren), Backend 198/198.
- **AP5 – Fernwartung umdrehen (letzter Direkt-DB-Zugriff entfernt)**: neues Client-Package
  `ws/` (`TerminalWebSocketClient`) baut eine ausgehende, dauerhafte WS-Verbindung zu
  `/api/v1/terminal-ws` auf (gleiches Standort-Token), beantwortet `PING`/`PONG` und die drei
  Fernwartungs-Anfragen (`STATUS`/`LOG`/`RESTART`, jetzt mit echter `RESTART_RESPONSE`) inkl.
  Reconnect+Backoff. `MaintenanceServerManager` (TCP-Server) und `LocationManager` (Direkt-DB-
  Registrierung) **entfernt** – kein `DataManager`-/JDBC-Import mehr in `src/main`.
  `locations.client_*`-Spalten werden nicht mehr genutzt (bleiben bis Phase 5). Alte Cross-
  Component-Suite (Alt-TCP) durch `TerminalMaintenanceRealClientE2ETest` im Backend-Modul
  ersetzt (echter Client-Jar als Subprozess) → **3/3, 2× reproduziert**. Backend 199/199.
- **AP6 – Offline-Robustheit**: Migration `V5` (`locations.offline_max_duration_minutes`,
  Default 60, im Portal editierbar, über `SnapshotDto` ausgeliefert). Backend `offline/`
  (`ClientTimestampPolicy`: akzeptiert `clientTimestamp` innerhalb Max-Dauer + Drift-Toleranz
  ±5min, unterdrückt Replay-Benachrichtigungen zu alter Ereignisse). Client `offline/`
  (`OfflineSnapshotStore`/`OfflineJournal`/`OfflinePricing`/`OfflineGateway`): Stufe A
  (online gestartete Ausführungen lokal zu Ende führen + nachmelden statt Fehler) und Stufe B
  (komplett neue Buchungen offline gegen den Snapshot). Replay über die seit AP3 idempotenten
  Endpunkte, bricht bei jedem Fehler komplett ab (Journal unverändert, Neuversuch von vorn).
  Normalbetrieb-Verhalten identisch. 8+3+1 neue Tests → Backend 207/207, UI 47/47,
  Client-E2E 29/29.
- **CI-Stabilität (nach PR #8)**: drei nur-in-CI auftretende Ursachen behoben – Testharness-
  Pfadbug (doppelter `dirname`-Pfad), `ApiClient`-Härtung (einmaliges Retry auf transiente
  `IOException`, sicher da GET idempotent + Idempotency-Key), und der eigentliche deCONZ-
  Fehlschlag: reihenfolgeabhängige Surefire-Sortierung ließ eine unfertige Ausführung von
  `ClientUsageE2ETest` (rein-fhem-Gerät) vom deCONZ-Start-Scan schalten → `DeconzException`;
  robuster, reihenfolge-unabhängiger Fix in den drei deCONZ-Testklassen + Produktionshärtung
  in `DeconzDevicePowerManager` (klarer Fehler bei leerer deCONZ-Id). Lokal deterministisch
  über erzwungene Reihenfolge bewiesen.
- **Phase 4 abgeschlossen (QA-Review, ohne blockierende Befunde)**: Diff-Review aller 23
  Commits (139 Dateien, ~9.645 Zeilen); `git diff -- Portal Common` LEER. Tiefenprüfung der
  geld-/sicherheitskritischen Pfade (Idempotenz, Offline-Journal append-only,
  `OfflineGateway` konservativ, `SnapshotUserDto` ohne Passwortfeld, `V5` rein additiv,
  Notification-Gating AUS). Suiten unabhängig nachgefahren.

## Entscheidungen
- Offline-Detailfragen vorab festgelegt (Snapshot/Journal/Replay-Semantik, Zeitfenster,
  Drift-Toleranz); dokumentierte, bewusst in Kauf genommene Restrisiken für Phase 5/6
  vermerkt (Offline-Neustart mitten in laufender Ausführung, zwei vestigiale
  `java.sql.SQLException`-Importe, Replay-Komplettabbruch bei fachlichem Fehler).

## Offen / nächster Schritt
- Phase 5 (Aufräumen): Alt-Portal + DataManager entfernen, DB-Rollen/Schema härten, Doku.

## Referenzen
- docs/kb/05-migration-plan.md (Änderungslog), docs/kb/03-modules.md, docs/kb/01-architecture.md,
  docs/kb/02-data-model.md, docs/kb/06-ui-tests.md
