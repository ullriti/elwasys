-- Entfernt die DB-Reste der mobilen App (elwaapi), die laut Auftraggeber nicht mehr
-- relevant ist (Entscheidung 2026-07-20, siehe docs/kb/05-migration-plan.md "Entscheidungen"
-- sowie docs/kb/02-data-model.md). Der DB-User "elwaapi" selbst wurde bereits in Phase 5 AP2
-- (V6__harden_db_roles.sql) entfernt; diese Migration räumt die verbliebenen
-- App-Relikt-Objekte auf: den Auth-Key-Trigger/die zugehörigen Funktionen, die
-- App-Relikt-Spalten auf users, sowie die Reservierungs-/Fremd-Authkey-Tabellen, die die
-- (nicht portierte) Reservierungsfunktion der App stützten.
--
-- Reihenfolge wegen Abhängigkeiten: zuerst der Trigger (feuert die Triggerfunktion bei
-- INSERT auf users), dann die Triggerfunktion, dann generate_user_authkey() (wird von der
-- Triggerfunktion aufgerufen und liest users.auth_key sowie den Config-Schlüssel
-- authkey.prefix), erst danach die Spalte users.auth_key selbst.
--
-- Verifiziert vor dieser Migration: UserEntity mappt app_id/access_key/auth_key nicht
-- (siehe dortigen Javadoc-Hinweis), das Backend liest/schreibt sie nirgends produktiv.
-- Die Terminal-UI (Client-Raspi, medium-UI) zeigte lediglich eine seit Phase 4 tote
-- "nicht verbunden"-Auth-Key-Anzeige (nie mit echten Werten befüllt) - diese wird im
-- selben Arbeitspaket (Phase 5 AP4) aus den Controllern/FXML entfernt. Die Config-Schlüssel
-- authkey.prefix (Präfix für generate_user_authkey) und reservation.duration (Dauer einer
-- App-Reservierung) werden ebenfalls von keinem Java-Code mehr gelesen (grep über
-- backend/src und Common/src ergab keine Treffer außerhalb der eingefrorenen Baseline).
--
-- V1__baseline_schema_0_4_0.sql und Common/resources/database-init.sql bleiben als
-- eingefrorene 0.4.0-Baseline unangetastet (siehe docs/kb/04-build-run.md).

DROP TRIGGER IF EXISTS user_authkey_trigger ON users;
DROP FUNCTION IF EXISTS user_authkey_trigger_function();
DROP FUNCTION IF EXISTS generate_user_authkey();

ALTER TABLE users DROP COLUMN IF EXISTS app_id;
ALTER TABLE users DROP COLUMN IF EXISTS access_key;
ALTER TABLE users DROP COLUMN IF EXISTS auth_key;

DROP TABLE IF EXISTS reservations;
DROP TABLE IF EXISTS foreign_authkeys;

DELETE FROM config WHERE key IN ('authkey.prefix', 'reservation.duration');
