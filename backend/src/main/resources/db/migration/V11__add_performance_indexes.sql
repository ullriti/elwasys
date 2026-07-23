-- Issue #37 (Pre-Launch AP5): Indizes für die heißen Guthaben-/Historie-Pfade.
--
-- getCredit() läuft bei JEDEM Kartenlogin und jedem Start (Summe über credit_accounting +
-- Unfinished-Scan über executions); das Admin-Dashboard liest die Geräte-Historie
-- (executions.device_id). Die V1-Baseline legt für diese Fremdschlüssel keine Indizes an -
-- nach Übernahme der Alt-DB (Jahre an Daten) werden das sonst Full-Table-Scans.
--
-- Rein additiv, idempotent (IF NOT EXISTS). Keine Datenänderung.
CREATE INDEX IF NOT EXISTS idx_executions_user_id ON executions (user_id);
CREATE INDEX IF NOT EXISTS idx_executions_device_id ON executions (device_id);
CREATE INDEX IF NOT EXISTS idx_credit_accounting_user_id ON credit_accounting (user_id);
