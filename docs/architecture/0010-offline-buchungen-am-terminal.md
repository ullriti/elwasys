# 10. Offline-Robustheit und Offline-Buchungen am Terminal

- **Status:** accepted
- **Datum:** 2026-07-21

## Kontext

Nachdem die Terminals nicht mehr direkt auf die DB, sondern über die Backend-API arbeiten
(ADR 4), entstünde bei einem nicht erreichbaren Backend ein Ausfall. Terminals stehen
unbeaufsichtigt im Feld; ein kurzer Netz-Schluckauf darf laufende Vorgänge nicht abbrechen
und Nutzer nicht aussperren.

## Entscheidung

Terminals bleiben bei nicht erreichbarem Backend **eigenständig bedienfähig**: laufende
Executions werden lokal zu Ende geführt, und für eine definierte Zeitspanne werden
**Offline-Buchungen** akzeptiert und später übermittelt (Journal/Snapshot). Festlegungen
des Auftraggebers: **60-Minuten-Default, aber über das Portal konfigurierbar**; kein
Sicherheitsabschlag; bei zwischenzeitlichen Sperrungen gilt der Snapshot-Stand;
unverschlüsselter Snapshot/Journal ist OK; Uhren-Drift-Toleranz einplanen (umgesetzt als
±5 Minuten, konfigurierbar). Die Übermittlung nutzt Idempotenz, damit ein Replay keine
Doppelbuchung erzeugt.

## Konsequenzen

- Terminal bleibt bei Netzstörungen bedienfähig; kein Datenverlust laufender Vorgänge.
- Konfigurierbares Offline-Fenster und Drift-Toleranz.
- Idempotente Ereignismeldung ist Voraussetzung für den Replay nach Wiederverbindung.

Herkunft: docs/kb/05-migration-plan.md, Abschnitte Konzeptskizze Offline-Buchungen und
Entscheidungen.
