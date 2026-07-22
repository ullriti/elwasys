-- Rollback-/Rückbau-SQL für einen ABGEBROCHENEN Cutover (Phase 6 AP2, siehe
-- docs/kb/05-migration-plan.md "Phase 6 - Produktivumschaltung (Cutover)"). Macht die additiven
-- Flyway-Migrationen V3..V10 (siehe backend/src/main/resources/db/migration/) so weit
-- rückgängig, dass die DB wieder dem ALTEN Feld-Schema entspricht, wie es das Alt-Portal +
-- der Alt-Client (JDBC-Direktzugriff) erwarten - OHNE Geschäftsdaten zu verlieren
-- (users/user_groups/locations/devices/programs/device_program_rel/executions/
-- credit_accounting/*_valid_user_groups bleiben unangetastet, außer der rein
-- Default-Fall-beschränkten Admin-Passwort-Wiederherstellung in Schritt V7 unten).
--
-- Gegenstück zu deploy/cutover/verify-cutover-migration.sh (AP1): dieses Skript spielt
-- typischerweise gegen genau die dort hinterlassene Test-DB `elwasys_cutover_verify`
-- (bzw. eine echte, per Cutover bereits auf V10 migrierte Produktiv-DB).
--
-- Jede Umkehrung ist idempotent (IF EXISTS/IF NOT EXISTS/WHERE-Wächter) - mehrfaches
-- Ausführen dieses Skripts gegen dieselbe DB ändert den Zustand beim zweiten Mal nicht mehr
-- (siehe deploy/cutover/verify-rollback.sh, Abschnitt "Idempotenz").
--
-- NICHT umgekehrt wird V2 (users.password VARCHAR(50)->255, siehe Begründung ganz unten vor
-- dem COMMIT) - alle anderen additiven/härtenden Migrationen werden strikt in umgekehrter
-- Reihenfolge zurückgebaut (V10 zuerst, dann V9..V3), damit Abhängigkeiten (z.B. GRANT auf
-- die von der V10-Umkehrung wiederhergestellte Tabelle "reservations") in der richtigen
-- Reihenfolge aufgelöst werden.
--
-- SICHERHEITS-HINWEIS: Die Umkehrung von V6 legt die Alt-Rollen elwaclient1/elwaapi mit ihren
-- historischen DEFAULT-Klartext-Passwörtern ('elwaclient1' bzw. 'api1234') wieder an - siehe
-- deploy/cutover/README.md, Abschnitt "Rollback", für die Empfehlung, diese nach einem
-- Rollback zeitnah zu rotieren.
--
-- Läuft als EINE Transaktion (BEGIN...COMMIT) - entweder alle Umkehrungen greifen, oder gar
-- keine (schlägt eine Anweisung fehl, macht ROLLBACK das gesamte Skript rückgängig statt
-- einen halb zurückgebauten Zwischenzustand zu hinterlassen).
BEGIN;

-- ============================================================================================
-- V10__drop_app_remnants.sql umkehren: Auth-Key-Trigger/-Funktionen + App-Relikt-Spalten auf
-- users + Tabellen reservations/foreign_authkeys + config-Zeilen authkey.prefix/
-- reservation.duration wieder anlegen. random_string() (siehe V1-Baseline
-- V1__baseline_schema_0_4_0.sql) wurde von V10 NICHT entfernt und wird hier vorausgesetzt.
-- ============================================================================================

-- Reihenfolge wie in der V1-Baseline (V1__baseline_schema_0_4_0.sql): erst die Spalte
-- users.auth_key (wird von der Triggerfunktion per NEW.auth_key beschrieben), dann die
-- Funktionen, dann der Trigger selbst.
ALTER TABLE users ADD COLUMN IF NOT EXISTS app_id VARCHAR(50) DEFAULT NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS access_key VARCHAR(50) DEFAULT NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS auth_key VARCHAR(50) DEFAULT NULL;

CREATE OR REPLACE FUNCTION generate_user_authkey() returns text as
$$
declare
  config_row config%ROWTYPE;
  count_result integer := 0;
  authkey text := 'nox';
begin
  /* Erzeuge einmaligen Authkey */
  SELECT * INTO config_row FROM config WHERE key='authkey.prefix';
  LOOP
      authkey := config_row.value || random_string(4);
    SELECT COUNT(*) INTO count_result FROM users WHERE auth_key=authkey;
    EXIT WHEN count_result=0;
    END LOOP;
    return authkey;
end;
$$ language plpgsql;

CREATE OR REPLACE FUNCTION user_authkey_trigger_function() returns trigger as
$$
begin
  new.auth_key := generate_user_authkey();
  return new;
end;
$$ language plpgsql;

-- "CREATE OR REPLACE TRIGGER" (PostgreSQL 14+, hier: PostgreSQL 16, siehe
-- verify-cutover-migration.sh) ist die idempotente Form - kein manueller Existenz-Wächter
-- nötig, anders als bei CREATE TABLE/CREATE ROLE weiter unten.
CREATE OR REPLACE TRIGGER user_authkey_trigger
  BEFORE INSERT ON users
  FOR EACH ROW EXECUTE PROCEDURE user_authkey_trigger_function();

CREATE TABLE IF NOT EXISTS reservations
(
  id         SERIAL PRIMARY KEY,
  user_id    INTEGER REFERENCES users NOT NULL,
  device_id  INTEGER REFERENCES devices NOT NULL,
  start_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CONSTRAINT  res_unique_constraint UNIQUE(user_id, device_id)
);

CREATE TABLE IF NOT EXISTS foreign_authkeys
(
  prefix          VARCHAR(50) NOT NULL,
  server_address  VARCHAR(50) NOT NULL
);

-- ON CONFLICT (key) DO NOTHING statt UPSERT: ein bereits vorhandener Wert (z.B. weil das
-- Rollback ein zweites Mal läuft) wird NICHT überschrieben.
INSERT INTO config (key, value) VALUES ('authkey.prefix', random_string(2))
    ON CONFLICT (key) DO NOTHING;
INSERT INTO config (key, value) VALUES ('reservation.duration', '900')
    ON CONFLICT (key) DO NOTHING;

-- ============================================================================================
-- V9__drop_obsolete_location_client_columns.sql umkehren: locations.client_*-Spalten wieder
-- anlegen (kommen leer/NULL zurück - kein Datenverlust möglich, die Spalten wurden von V9
-- ersatzlos gedroppt, ihr letzter Inhalt ist nicht rekonstruierbar; siehe README.md-Caveat).
-- ============================================================================================
ALTER TABLE locations ADD COLUMN IF NOT EXISTS client_uid VARCHAR(50);
ALTER TABLE locations ADD COLUMN IF NOT EXISTS client_ip VARCHAR(50);
ALTER TABLE locations ADD COLUMN IF NOT EXISTS client_port INT;
ALTER TABLE locations ADD COLUMN IF NOT EXISTS client_last_seen TIMESTAMP;

-- ============================================================================================
-- V8__rename_auto_end_power_threshold.sql umkehren: Spalte zurück auf den alten Tippfehler-
-- Namen "auto_end_power_threashold" (Alt-Portal/Alt-Client kennen nur diesen Namen). Nur
-- umbenennen, wenn der neue Name existiert UND der alte noch nicht - sonst (zweiter Lauf)
-- ist nichts zu tun.
-- ============================================================================================
DO
$do$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns
             WHERE table_schema = 'public' AND table_name = 'devices'
               AND column_name = 'auto_end_power_threshold')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                      WHERE table_schema = 'public' AND table_name = 'devices'
                        AND column_name = 'auto_end_power_threashold') THEN
    ALTER TABLE devices RENAME COLUMN auto_end_power_threshold TO auto_end_power_threashold;
  END IF;
END
$do$;

-- ============================================================================================
-- V7__remove_default_admin_password.sql umkehren (idempotent + verhaltensbewahrend): stellt
-- NUR den von V7 genullten Default-Fall wieder her (password IS NULL). Ein echt gesetztes
-- Passwort (Alt- ODER Neu-Portal, Argon2id oder SHA1) wird NIEMALS überschrieben - siehe
-- README.md-Caveat, dass ein im Neu-Portal gesetztes Argon2-Passwort vom Alt-Portal ohnehin
-- nicht verstanden wird (dafür ist ein manueller Reset nötig, nicht Teil dieser Umkehrung).
-- ============================================================================================
UPDATE users
SET password = 'd033e22ae348aeb5660fc2140aec35850c4da997'
WHERE username = 'admin'
  AND password IS NULL;

-- ============================================================================================
-- V6__harden_db_roles.sql umkehren: Alt-Rollen elwaclient1 (Passwort 'elwaclient1', Gruppe
-- elwaclients), Gruppe elwaclients, User elwaapi (Passwort 'api1234') + deren Grants wieder
-- anlegen (DDL/Grants aus der V1-Baseline (V1__baseline_schema_0_4_0.sql) übernommen). Rollen können nicht
-- per "CREATE ROLE IF NOT EXISTS" abgesichert werden (PostgreSQL kennt diese Syntax nicht) -
-- daher Existenz-Wächter per DO-Block wie in V6 selbst.
-- ============================================================================================
DO
$do$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'elwaclients') THEN
    EXECUTE 'CREATE GROUP elwaclients';
  END IF;
END
$do$;

DO
$do$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'elwaclient1') THEN
    EXECUTE $create$CREATE USER elwaclient1 WITH PASSWORD 'elwaclient1' IN GROUP elwaclients$create$;
  ELSE
    -- Rolle besteht bereits (z.B. clusterweit aus einer anderen DB desselben Clusters, siehe
    -- V6-Header) - Gruppenmitgliedschaft trotzdem idempotent sicherstellen (kein Fehler, falls
    -- die Mitgliedschaft schon besteht).
    EXECUTE 'GRANT elwaclients TO elwaclient1';
  END IF;
END
$do$;

DO
$do$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'elwaapi') THEN
    EXECUTE $create$CREATE USER elwaapi WITH PASSWORD 'api1234'$create$;
  END IF;
END
$do$;

-- GRANT ist selbst idempotent (ein bereits vorhandenes Privileg erneut zu vergeben ist ein
-- No-Op, kein Fehler) - kein zusätzlicher Wächter nötig. Reihenfolge/Inhalt 1:1 aus
-- der V1-Baseline (V1__baseline_schema_0_4_0.sql) übernommen. Setzt voraus, dass "reservations" bereits
-- existiert (siehe V10-Umkehrung oben, GRANT INSERT, DELETE ON reservations TO elwaapi).
GRANT SELECT ON ALL TABLES IN SCHEMA public TO GROUP elwaclients;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO GROUP elwaclients;

GRANT INSERT, UPDATE ON executions TO GROUP elwaclients;
GRANT UPDATE ON SEQUENCE executions_id_seq TO GROUP elwaclients;

GRANT UPDATE ON locations TO GROUP elwaclients;

GRANT UPDATE ON devices TO GROUP elwaclients;

GRANT INSERT ON credit_accounting TO GROUP elwaclients;
GRANT UPDATE ON SEQUENCE credit_accounting_id_seq TO GROUP elwaclients;

GRANT SELECT ON ALL TABLES IN SCHEMA public TO elwaapi;
GRANT UPDATE ON users TO elwaapi;
GRANT INSERT, DELETE ON reservations TO elwaapi;

-- ============================================================================================
-- V3__create_terminal_tokens.sql, V4__create_terminal_idempotency_keys.sql,
-- V5__add_offline_max_duration_to_locations.sql umkehren: reine Neu-System-Artefakte, die
-- NICHT zum Alt-Schema gehören (Standort-Token-Auth/Idempotenz/Offline-Zeitfenster gibt es im
-- Alt-Portal/Alt-Client nicht) - werden ersatzlos entfernt.
-- ============================================================================================
DROP TABLE IF EXISTS terminal_idempotency_keys;
DROP TABLE IF EXISTS terminal_tokens;
ALTER TABLE locations DROP COLUMN IF EXISTS offline_max_duration_minutes;

-- ============================================================================================
-- V2__widen_users_password_column.sql wird BEWUSST NICHT umgekehrt (kein
-- "ALTER TABLE users ALTER COLUMN password TYPE VARCHAR(50)"): ein im Neu-System evtl.
-- bereits gesetzter Argon2id-Hash ist konstant 97 Zeichen lang (siehe V2-Header) - ein
-- Verengen auf VARCHAR(50) würde ihn ABSCHNEIDEN und damit zerstören/unbrauchbar machen. Der
-- Alt-Code (Portal/Client) liest/schreibt die Spalte nur als String ohne eigene
-- Längenprüfung - er stört sich nicht an der breiteren VARCHAR(255)-Spalte. Diese Migration
-- bleibt daher wie sie ist: additiv/abwärtskompatibel, keine Rückbau-Notwendigkeit.
-- ============================================================================================

-- ============================================================================================
-- flyway_schema_history droppen: zwingend, sonst würde ein SPÄTERER erneuter Cutover die
-- (hier manuell zurückgebauten) Migrationen V3..V10 NICHT erneut anwenden (Flyway hält sie
-- sonst weiterhin für bereits angewendet). Nach diesem DROP verhält sich die DB wieder exakt
-- wie eine unmigrierte Alt-Weg-DB: der nächste Cutover-Versuch baselined erneut auf V1 und
-- wendet V2..V10 frisch an (siehe deploy/cutover/verify-rollback.sh, Abschnitt
-- "Re-Cutover-Beweis").
-- ============================================================================================
DROP TABLE IF EXISTS flyway_schema_history;

COMMIT;
