-- Aufraeumen obsoleter Spalten (Phase 5 AP3, siehe kb/02-data-model.md,
-- kb/05-migration-plan.md): locations.client_uid/client_ip/client_port/client_last_seen
-- dienten der Alt-Fernwartung, bei der sich das Terminal per IP/Port beim Server anmeldete
-- (Client haelt Verbindung offen, Portal verbindet sich zum Client). Seit Phase 4 AP5 laeuft
-- die Fernwartung umgekehrt ueber einen ausgehenden WebSocket-Kanal des Terminals zum
-- Backend (siehe org.kabieror.elwasys.backend.ws.TerminalConnectionRegistry); eine
-- DB-Registrierung von IP/Port/UID findet nicht mehr statt.
--
-- Verifiziert vor dieser Migration: LocationEntity mappt diese vier Spalten nicht (nur noch
-- im Javadoc erwaehnt), das Backend liest/schreibt sie nirgends produktiv mehr (nur
-- Kommentare in AdminDashboardView/TerminalConnectionRegistry verweisen historisch darauf).
--
-- IF EXISTS zur Sicherheit, falls eine Spalte auf einer Installation bereits fehlt.
ALTER TABLE locations DROP COLUMN IF EXISTS client_uid;
ALTER TABLE locations DROP COLUMN IF EXISTS client_ip;
ALTER TABLE locations DROP COLUMN IF EXISTS client_port;
ALTER TABLE locations DROP COLUMN IF EXISTS client_last_seen;
