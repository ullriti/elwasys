-- Phase 4 AP3 (siehe kb/05-migration-plan.md): Idempotenz-Schlüssel für terminal-gemeldete
-- Execution-Ereignisse (Start/Ende/Abbruch/Reset über /api/v1/executions/**). Rein additiv,
-- keine bestehende Tabelle wird angefasst - der Alt-Code bekommt von dieser Tabelle nichts
-- mit.
--
-- Design (siehe kb/03-modules.md "Idempotenz + Replay" für Details):
--  * Das Terminal erzeugt pro fachlichem Ereignis (z.B. "Programm X auf Gerät Y beendet")
--    genau EINEN UUID-Idempotenz-Schlüssel und sendet ihn im Header "Idempotency-Key" mit.
--    Wiederholte Anfragen mit demselben Schlüssel (z.B. nach einem Verbindungsabbruch vor
--    Erhalt der Antwort) werden dedupliziert: die ZUERST berechnete Antwort wird erneut
--    ausgeliefert, ohne die fachliche Aktion (Abrechnung, Benachrichtigung, ...) ein zweites
--    Mal auszuführen.
--  * idempotency_key ist global eindeutig (ein Schlüssel identifiziert GENAU EIN Ereignis,
--    unabhängig vom HTTP-Pfad) - siehe IdempotencyService.
--  * location_id (Standort-Scope des Terminal-Tokens) ist rein informativ/für Aufräum- und
--    Diagnosezwecke, nicht Teil des Unique-Keys.
--  * response_status/response_body speichern die beim ersten Aufruf tatsächlich gelieferte
--    Antwort, damit ein Replay exakt dieselbe Antwort liefern kann.
CREATE TABLE terminal_idempotency_keys
(
    id              BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(64) NOT NULL UNIQUE,
    location_id     INTEGER     NOT NULL REFERENCES locations (id) ON DELETE CASCADE,
    operation       VARCHAR(50) NOT NULL,
    response_status INTEGER     NOT NULL,
    response_body   TEXT        NOT NULL,
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_terminal_idempotency_keys_location_id ON terminal_idempotency_keys (location_id);
