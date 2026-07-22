# 5. Fernwartung über ausgehende WebSocket-Verbindung des Terminals

- **Status:** accepted
- **Datum:** 2026-07-20

## Kontext

Die Alt-Fernwartung funktioniert umgekehrt: das Terminal lauscht als Server (Port 3591)
und registriert seine IP in der DB (`locations.client_ip/-port`), das Portal wählt es
über ein eigenes TCP-Nachrichtenprotokoll (`Common.maintenance.*`,
`MaintenanceServerManager`) an. Das ist NAT-/Firewall-unfreundlich und erzwingt eine
IP-Registry.

## Entscheidung

Die Richtung wird umgekehrt: das Terminal hält eine **ausgehende, dauerhafte
WebSocket-Verbindung** zum Backend (`/api/v1/terminal-ws`, unter demselben
Standort-Token-Sicherheitsfilter wie die REST-Endpunkte). Status, Logs und Restart laufen
als JSON-Nachrichten mit explizitem Typ-/Versionsfeld (`LOG_REQUEST`/`RESTART_REQUEST`/
`RESTART_RESPONSE`) über diese Verbindung; das neue Admin-Portal vermittelt sie über den
`TerminalMaintenanceService`. Die Server-Rolle des Clients und `client_ip/-port` in der DB
entfallen. Das Alt-TCP-Protokoll wird nicht ins neue Portal portiert; das Alt-Portal
bedient Alt-Clients bis zum Cutover weiter über sein eigenes Protokoll.

## Konsequenzen

- NAT-/Firewall-freundlich, keine IP-Registry mehr nötig.
- Erweiterbares, versioniertes Nachrichtenformat.
- Bis zum Terminal-Cutover zeigt die neue Fernwartungs-UI überwiegend "Nicht verbunden" –
  ein korrekter, erwarteter Zustand.

Herkunft: docs/kb/05-migration-plan.md, Abschnitte Zielarchitektur und Entscheidungen.
