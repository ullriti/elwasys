-- DB-Härtung (Phase 5 AP2, siehe docs/kb/05-migration-plan.md): entfernt die in
-- V1__baseline_schema_0_4_0.sql (bzw. dem Alt-Weg Common/resources/database-init.sql) mit
-- Default-Passwörtern angelegten Rollen 'elwaclient1' (Passwort 'elwaclient1') und 'elwaapi'
-- (Passwort 'api1234') sowie deren Gruppe 'elwaclients'. Diese drei Rollen/Gruppen wurden vom
-- Alt-Client (JDBC-Direktzugriff auf die DB) verwendet - seit Phase 4 AP4/AP5 spricht
-- Client-Raspi nur noch über die Backend-REST-API/den Standort-Token (kein Direkt-DB-Zugriff
-- des Clients mehr, siehe docs/kb/05-migration-plan.md "Phase 4"). Der Backend-User 'elwaportal'
-- (siehe application.yml, ELWASYS_DB_USER) bleibt unverändert mit seinen bestehenden Grants
-- aus V1 bestehen - er ist ab jetzt der EINZIGE Anwendungs-DB-User.
--
-- Cluster-weite-Rollen-Problematik (wie im V1-Header beschrieben): PostgreSQL-Rollen sind
-- NICHT pro Datenbank, sondern für den gesamten Cluster gültig. Die Test-DBs in diesem Projekt
-- teilen sich einen Cluster (siehe backend/run-backend-tests.sh, backend/e2e/scripts/
-- start-backend.sh, Client-Raspi/ci-support/start-test-backend.sh) - jede dieser DBs führt
-- diese Migration beim Hochfahren eigenständig aus. Das bringt ZWEI Ausprägungen desselben
-- Grundproblems mit sich, beide werden hier robust abgefangen:
--   1. Die Rolle kann bereits von der Migration einer ANDEREN Datenbank desselben Clusters
--      gedroppt worden sein -> jeder Schritt steht daher hinter einem Existenz-Wächter
--      (pg_catalog.pg_roles WHERE rolname = ...).
--   2. Umgekehrt kann die Rolle in DIESER Datenbank zwar existieren, aber (z.B. weil eine
--      Alt-Weg-DB desselben Clusters noch über database-init.sql läuft/lief, siehe
--      Client-Raspi-Testharnesses) noch Rechte/Objekte in EINER ANDEREN Datenbank desselben
--      Clusters besitzen. DROP OWNED BY wirkt nur auf die AKTUELL VERBUNDENE Datenbank -
--      DROP ROLE schlägt dann trotzdem mit "role ... cannot be dropped because some objects
--      depend on it" (SQLSTATE 2BP01, "dependent_objects_still_exist") fehl, weil Postgres
--      Rollenabhängigkeiten clusterweit (pg_shdepend) prüft. In einer echten Produktivumgebung
--      mit genau EINER elwasys-Datenbank tritt das nicht auf; im geteilten Test-Cluster wird
--      dieser Fall daher NICHT als Migrationsfehler behandelt, sondern abgefangen (RAISE
--      NOTICE) - die Rolle bleibt dann (rechte-bereinigt in dieser DB, aber noch nicht
--      gedroppt) bestehen, bis auch die letzte Datenbank, die sie noch referenziert, migriert
--      wurde.
DO
$do$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'elwaclient1') THEN
    EXECUTE 'DROP OWNED BY elwaclient1';
    BEGIN
      EXECUTE 'DROP ROLE elwaclient1';
    EXCEPTION WHEN dependent_objects_still_exist THEN
      RAISE NOTICE 'Rolle elwaclient1 besitzt noch Rechte in einer anderen Datenbank '
          'desselben Clusters - DROP ROLE übersprungen (Grants in dieser DB sind bereits '
          'entfernt).';
    END;
  END IF;
END
$do$;

DO
$do$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'elwaapi') THEN
    EXECUTE 'DROP OWNED BY elwaapi';
    BEGIN
      EXECUTE 'DROP ROLE elwaapi';
    EXCEPTION WHEN dependent_objects_still_exist THEN
      RAISE NOTICE 'Rolle elwaapi besitzt noch Rechte in einer anderen Datenbank desselben '
          'Clusters - DROP ROLE übersprungen (Grants in dieser DB sind bereits entfernt).';
    END;
  END IF;
END
$do$;

-- Die Gruppe 'elwaclients' erst NACH ihren Mitgliedern droppen: 'elwaclient1' war per
-- "IN GROUP elwaclients" Mitglied - DROP OWNED BY elwaclient1 (oben) entfernt zwar dessen
-- Grants/Objekte, nicht aber die Gruppenmitgliedschaft selbst. PostgreSQL löst die
-- Mitgliedschaft beim (in dieser DB erfolgreichen) DROP ROLE elwaclient1 automatisch mit auf;
-- bleibt elwaclient1 wegen Fall 2 oben bestehen, bleibt auch dessen Mitgliedschaft in
-- elwaclients bestehen - dieselbe dependent_objects_still_exist-Abfangung greift dann auch
-- hier.
DO
$do$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'elwaclients') THEN
    EXECUTE 'DROP OWNED BY elwaclients';
    BEGIN
      EXECUTE 'DROP GROUP elwaclients';
    EXCEPTION WHEN dependent_objects_still_exist THEN
      RAISE NOTICE 'Gruppe elwaclients besitzt noch Rechte/Mitglieder in einer anderen '
          'Datenbank desselben Clusters - DROP GROUP übersprungen (Grants in dieser DB sind '
          'bereits entfernt).';
    END;
  END IF;
END
$do$;
