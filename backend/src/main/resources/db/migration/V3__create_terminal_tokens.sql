-- Phase 2 AP4 (siehe docs/kb/05-migration-plan.md): Standort-Token-Auth für die Terminal-REST-API
-- (/api/v1/**) und den WebSocket-Endpunkt. Rein additiv, keine bestehende Tabelle wird
-- angefasst - der Alt-Code (Common/Client-Raspi/Portal) bekommt von dieser Tabelle nichts mit.
--
-- Design (siehe docs/kb/02-data-model.md, docs/kb/03-modules.md für Details):
--  * Ein Standort kann MEHRERE gültige Tokens haben (n:1 zu locations) - das ermöglicht
--    Rotation ohne Downtime: ein neues Token anlegen, Terminal umstellen, dann das alte per
--    revoked_at deaktivieren, statt ein einzelnes Token blind zu überschreiben.
--  * Nur der Hash (SHA-256, hex) wird gespeichert, NIE das Klartext-Token - siehe
--    TerminalTokenService. Der Hash ist selbst der Lookup-Schlüssel (unique + indiziert),
--    das Klartext-Token wird nur einmalig beim Erzeugen angezeigt (siehe
--    TerminalTokenCliRunner).
--  * revoked_at (nullable): NULL = aktiv, gesetzt = deaktiviert (Widerruf ohne Löschen, damit
--    die Historie/Audit-Spur erhalten bleibt).
--  * last_used_at (nullable, vom Auth-Filter aktualisiert): rein informativ, hilft beim
--    Aufräumen ungenutzter Tokens.
CREATE TABLE terminal_tokens
(
    id            SERIAL PRIMARY KEY,
    location_id   INTEGER      NOT NULL REFERENCES locations (id) ON DELETE CASCADE,
    token_hash    VARCHAR(64)  NOT NULL UNIQUE,
    label         VARCHAR(100),
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at    TIMESTAMP,
    last_used_at  TIMESTAMP
);

CREATE INDEX idx_terminal_tokens_location_id ON terminal_tokens (location_id);
