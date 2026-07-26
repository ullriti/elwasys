# 22. Dead-Letter-Sichtbarkeit: Vorfalls-Meldung über den Terminal-WebSocket + Quittierung im Portal

- **Status:** accepted
- **Datum:** 2026-07-24

## Kontext

Die finale Review vor dem Feldeinsatz (Spec 0001, Track R5) fand im Betriebskonzept eine
Lücke, die nicht im Mechanismus, sondern in der „letzten Meile" liegt: **kritische Vorfälle des
Offline-Pfads erreichen keinen Menschen.**

Konkret werden am Terminal zwei Fehlerbilder nur als `logger.error` in das **lokale Logback-Log
des Raspberry Pi** geschrieben (`OfflineGateway`):

1. **Dead-Letter:** Ein Journal-Eintrag wird vom Backend dauerhaft fachlich abgelehnt (z. B.
   gelöschtes Gerät/Programm) und in die Dead-Letter-Datei verschoben. Damit ist die Buchung
   endgültig **nicht** beim Backend angekommen – ein **stiller Geldverlust**.
2. **Geister-Ausführung:** Beim Replay einer offline gebuchten Ausführung gelingt der `START`,
   die Terminierung scheitert fachlich; der kompensierende `abort` (ADR 0021/#68) schlägt
   ebenfalls fehl – die Ausführung bleibt serverseitig offen.

Beides ist betrieblich relevant, aber unsichtbar: niemand liest routinemäßig das Log eines
Terminals im Waschkeller. Die Review stufte das als Mittelfund „zeitnah" ein (Issue #89); der
Auftraggeber hat entschieden, es **mit FR-2** zu erledigen, weil der in demselben Arbeitspaket
verdrahtete Alarmkanal (H4, Issue #83) genau der Empfänger ist, der bisher fehlte.

## Entscheidung

**Das Terminal meldet solche Vorfälle aktiv an das Backend; offene Vorfälle lösen den
Betriebsalarm aus und werden im Portal quittiert.**

1. **Transport: der bestehende Wartungs-WebSocket, nicht ein neuer REST-Endpunkt.** Neue
   Nachrichtentypen `OFFLINE_INCIDENT` (Terminal → Backend) und `OFFLINE_INCIDENT_ACK`
   (Backend → Terminal) im vorhandenen typisierten Protokoll (`TerminalWsMessage`, Version 1,
   unbekannte Felder werden ignoriert – additiv, bricht keinen Bestandsclient).
   - **Warum WS statt REST:** Die Verbindung besteht ohnehin, ist bereits per Standort-Token
     authentifiziert, und der Standort ist serverseitig aus der Session bekannt. Ein neuer
     REST-Endpunkt hätte zusätzliche API-/Auth-Fläche geschaffen, ohne etwas zu gewinnen.
   - `OFFLINE_INCIDENT` ist die **erste unaufgeforderte Client→Server-Nachricht** des
     Protokolls (bisher waren alle Client→Server-Nachrichten Antworten auf Server-Anfragen).
     Das ist die bewusste Erweiterung: der Vorfall entsteht am Terminal.
   - **Der Standort kommt aus der Session, nie aus der Payload** – ein Terminal kann keine
     Vorfälle für einen fremden Standort melden (dasselbe Prinzip wie die
     Fernwartungs-Standortprüfung, Issue #26).

2. **Persistiert statt nur in-memory.** Der Vorfall wird in der additiven Tabelle
   `terminal_offline_incidents` (Migration V12) festgehalten, inklusive `charged_price` – dem
   **Betrag der verlorenen Buchung**, also dem eigentlichen Schaden.
   - **Warum persistiert:** Eine rein flüchtige In-Memory-Registrierung (billiger, keine
     Migration) hätte einen Backend-Neustart nicht überlebt. Für ein **Geldverlust-Signal**
     ist das zu schwach; der Vorfall ist zugleich der einzige zentrale Beleg.
   - `incident_key` (Art + Idempotenz-Schlüssel des Journal-Eintrags) ist der
     **Idempotenz-Anker** – dasselbe Muster wie `terminal_idempotency_keys` (V4). Das Terminal
     kennt den Zustellstatus nicht und darf nach Reconnect/Neustart erneut melden, ohne
     Doppel-Einträge zu erzeugen.

3. **Zustellung ist neustartfest (Outbox am Terminal).** Ein Dead-Letter tritt typischerweise
   **genau dann** auf, wenn ohnehin etwas kaputt ist – die WS-Verbindung kann in diesem Moment
   fehlen. Eine reine Fire-and-Forget-Meldung ginge dann verloren und der Alarm käme nie.
   Deshalb legt das Terminal Vorfälle lokal ab und sendet sie, sobald die Verbindung steht;
   nach `OFFLINE_INCIDENT_ACK` werden sie entfernt. Wiederholtes Senden ist durch (2) gefahrlos.

4. **Alarm über den vorhandenen Kanal.** `OfflineIncidentHealthIndicator` zählt **offene**
   (nicht quittierte) Vorfälle und tritt der Gruppe `/actuator/health/operational` bei – damit
   greift automatisch der in FR-2 verdrahtete Alarmkanal (Poll-Skript → Pushover/Mail, plus
   externer Uptime-Monitor, siehe `deploy/monitoring/`). Kein zweiter Alarmweg.

5. **Rückweg aus dem Alarm: Quittierung im Portal, kein Zeitverfall.** Ein Vorfall bleibt
   alarmierend, bis ein Admin ihn im Portal quittiert; der Datensatz bleibt danach als Beleg
   erhalten (`acknowledged_at`/`acknowledged_by`).
   - **Warum kein automatischer Verfall nach N Tagen** (die erwogene Alternative): Ein
     Geldverlust soll **aktiv zur Kenntnis genommen** werden, nicht still verjähren. Ein
     Zeitfenster hätte den Alarm zwar bequem selbst zurückgesetzt, aber genau die Eigenschaft
     verloren, für die dieses Arbeitspaket existiert.
   - **Preis dieser Wahl (bewusst):** Ohne Quittierung bleibt `/operational` dauerhaft rot.
     Das ist gewollt („es liegt echt etwas an"), verlangt aber, dass die Quittierung im Portal
     tatsächlich bedienbar und auffindbar ist – deshalb ist sie Teil desselben Arbeitspakets.
   - Eine erneute Meldung **öffnet einen bereits quittierten Vorfall nicht wieder** – sonst
     könnte ein Terminal, das seine Dead-Letter-Datei behält, den Alarm dauerhaft wiederbeleben.

6. **Die Meldung ist fail-safe und niemals im Geldpfad.** Fehler beim Melden dürfen den Replay
   nicht abbrechen, verzögern oder verändern; die bestehenden `logger.error`-Zeilen am Terminal
   bleiben als Rückfallebene erhalten.

## Konsequenzen

**Positiv**

- Ein dead-gelettert Eintrag (verlorenes Geld) erreicht jetzt einen Menschen – über denselben
  Alarmkanal wie alle anderen Betriebsfehlerbilder.
- Zentraler, dauerhafter Beleg je Vorfall inklusive Betrag, Nutzer und Grund; Diagnose ist nicht
  mehr auf das Log eines einzelnen Pi angewiesen.
- Rein additiv: neue Nachrichtentypen (Protokollversion unverändert), neue Tabelle, neue View –
  bestehendes Verhalten unverändert.

**Negativ / Restrisiken**

- **Dauerhaft roter Alarm bei Nichtbeachtung.** Wird nicht quittiert, bleibt `/operational` auf
  503 und kann Alarm-Müdigkeit erzeugen. Bewusst akzeptiert (siehe Entscheidung 5).
- **Zustellung ist best effort.** Bleibt ein Terminal dauerhaft offline oder ist sein
  Datenträger defekt, kann die Meldung ausbleiben; der lokale Log-Eintrag bleibt dann die
  einzige Spur. Die Outbox verkleinert dieses Fenster, schließt es aber nicht.
- **Der Vorfall repariert nichts.** Die verlorene Buchung wird nicht automatisch nachgebucht –
  die Entscheidung, ob und wie ein Betrag nachträglich verbucht wird, bleibt bewusst ein
  menschlicher, im Portal auszuführender Vorgang (Guthaben-Korrektur).

## Referenzen

- Issue #89 (Epic #94), [SYNTHESE.md](../reviews/final/SYNTHESE.md) FR-2,
  [R5-betrieb.md](../reviews/final/R5-betrieb.md)
- ADR [0016](0016-offline-replay-haertung.md) / [0021](0021-offline-replay-haertung-ii.md)
  (Offline-/Replay-Pfad, Dead-Letter-Mechanik, Geister-Execution-Kompensation)
- Alarmkanal: [`deploy/monitoring/README.md`](../../deploy/monitoring/README.md),
  Runbook Kap. 7b
