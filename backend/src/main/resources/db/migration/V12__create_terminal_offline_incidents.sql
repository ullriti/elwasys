-- Issue #89 (finale Review R5, Dead-Letter-Sichtbarkeit): Vorfälle des Terminal-Offline-Pfads,
-- die bisher NUR als logger.error im lokalen Pi-Log landeten und damit niemanden erreichten -
-- ein dead-gelettert Journal-Eintrag ist eine verlorene Offline-Buchung (Geld). Das Terminal
-- meldet solche Vorfälle jetzt über den bestehenden Wartungs-WebSocket-Kanal, das Backend hält
-- sie hier fest und hebt offene (nicht quittierte) Vorfälle in einen Health-Indicator
-- (/actuator/health/operational -> Alerting, siehe deploy/monitoring/).
--
-- Rein additiv, keine bestehende Tabelle wird angefasst.
--
-- Design:
--  * incident_key ist der Idempotenz-Anker (Art + Idempotenz-Schlüssel des Journal-Eintrags,
--    vom Terminal deterministisch gebildet): dieselbe Meldung darf nach einem Reconnect oder
--    einem Terminal-Neustart erneut eintreffen, ohne einen Doppel-Eintrag zu erzeugen -
--    dasselbe Muster wie bei terminal_idempotency_keys (V4).
--  * charged_price hält den lokal berechneten Betrag der verlorenen Buchung fest (der eigentliche
--    Schaden) - NULL, wenn der Vorfall keinen Betrag trug (z.B. ein verwaister START).
--  * user_id ist informativ (wessen Buchung betroffen war) und wird beim Löschen des Nutzers auf
--    NULL gesetzt, statt den Vorfallsbeleg mitzulöschen.
--  * acknowledged_at/-_by: die Quittierung im Portal beendet den Alarm; der Datensatz selbst
--    bleibt als Beleg dauerhaft erhalten (kein Purge - das ist, wie beim Ledger, bewusst).
CREATE TABLE terminal_offline_incidents
(
    id              BIGSERIAL PRIMARY KEY,
    incident_key    VARCHAR(120) NOT NULL UNIQUE,
    location_id     INTEGER      NOT NULL REFERENCES locations (id) ON DELETE CASCADE,
    kind            VARCHAR(40)  NOT NULL,
    entry_type      VARCHAR(20),
    idempotency_key VARCHAR(64),
    user_id         INTEGER      REFERENCES users (id) ON DELETE SET NULL,
    charged_price   NUMERIC(10, 2),
    reason          TEXT         NOT NULL,
    occurred_at     TIMESTAMP,
    reported_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acknowledged_at TIMESTAMP,
    acknowledged_by VARCHAR(100)
);

CREATE INDEX idx_terminal_offline_incidents_location_id ON terminal_offline_incidents (location_id);
-- Der Health-Indicator fragt genau "gibt es offene Vorfälle?" - Teilindex auf die offenen.
CREATE INDEX idx_terminal_offline_incidents_open ON terminal_offline_incidents (acknowledged_at)
    WHERE acknowledged_at IS NULL;
