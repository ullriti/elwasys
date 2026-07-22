CREATE DATABASE elwasys;

\connect elwasys

/* FUNKTIONEN */
CREATE OR REPLACE FUNCTION random_string(length integer) returns text as 
$$
declare
  /* Verwendet keine 0, O oder I, l für eindeutige Lesbarkeit */
  chars text[] := '{1,2,3,4,5,6,7,8,9,A,B,C,D,E,F,G,H,J,K,L,M,N,P,Q,R,S,T,U,V,W,X,Y,Z,a,b,c,d,e,f,g,h,j,k,m,n,o,p,q,r,s,t,u,v,w,x,y,z}';
  result text := '';
  i integer := 0;
begin
  if length < 0 then
    raise exception 'Given length cannot be less than 0';
  end if;
  for i in 1..length loop
    result := result || chars[1+random()*(array_length(chars, 1)-1)];
  end loop;
  return result;
end;
$$ language plpgsql;


/* KONFIGURATION */
CREATE TABLE config
(
  key   VARCHAR(50) NOT NULL UNIQUE,
  value TEXT
);
INSERT INTO config (key, value) VALUES ('db.version', '0.4.0');
INSERT INTO config (key, value) VALUES ('authkey.prefix', random_string(2));
/* Dauer einer Reservierung in Sekunden */
INSERT INTO config (key, value) VALUES ('reservation.duration', 900);

/* USER MANAGEMENT */
CREATE TYPE DISCOUNT_TYPE AS ENUM ('NONE', 'FIX', 'FACTOR');
CREATE TABLE user_groups (
  id             SERIAL PRIMARY KEY,
  name           VARCHAR(50) NOT NULL,
  discount_type  DISCOUNT_TYPE    DEFAULT 'NONE',
  discount_value DOUBLE PRECISION DEFAULT 0
);
INSERT INTO user_groups (name) VALUES ('Default');

CREATE TABLE users
(
  id                     SERIAL PRIMARY KEY,
  name                   VARCHAR(50)                    NOT NULL,
  username               VARCHAR(50)                    NOT NULL UNIQUE,
  email                  VARCHAR(50),
  card_ids               TEXT                           NOT NULL DEFAULT '',
  blocked                BOOLEAN                                 DEFAULT FALSE,
  password               VARCHAR(50),
  is_admin               BOOLEAN                        NOT NULL DEFAULT FALSE,
  email_notification     BOOLEAN                        NOT NULL DEFAULT TRUE,
  push_notification      BOOLEAN                        NOT NULL DEFAULT TRUE,
  pushover_user_key      VARCHAR(50)                    NOT NULL DEFAULT '',
  password_reset_key     VARCHAR(50),
  password_reset_timeout TIMESTAMP WITHOUT TIME ZONE,
  deleted                BOOLEAN                        NOT NULL DEFAULT FALSE,
  last_login             TIMESTAMP WITHOUT TIME ZONE,
  group_id               INTEGER REFERENCES user_groups NOT NULL DEFAULT 1,
  /* ID des Benutzers der elwaApp, falls eine Verbindung von der App aus aufgebaut wurde */
  app_id                 VARCHAR(50)                    DEFAULT NULL,
  /* Der Code, mit der sich die App beim Server authentifizieren kann */
  access_key             VARCHAR(50)                    DEFAULT NULL,
  /* Der 6-stellige einmalige Code zum Aufbau der Verbindung zwischen App und Server */
  /* Wird nach der ersten Anmeldung auf NULL gesetzt */
  auth_key               VARCHAR(50)                    DEFAULT NULL
);

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

CREATE TRIGGER user_authkey_trigger
  BEFORE INSERT ON users
  FOR EACH ROW EXECUTE PROCEDURE user_authkey_trigger_function();

INSERT INTO users (name, username, password, is_admin)
VALUES ('Administrator', 'admin', 'd033e22ae348aeb5660fc2140aec35850c4da997', TRUE);

/* LOCATION MANAGEMENT */
CREATE TABLE locations
(
  id               SERIAL PRIMARY KEY,
  name             VARCHAR(50) NOT NULL UNIQUE,
  client_uid       VARCHAR(50),
  client_ip        VARCHAR(50),
  client_port      INT,
  client_last_seen TIMESTAMP
);
INSERT INTO locations (name) VALUES ('Default');

CREATE TABLE locations_valid_user_groups
(
  location_id INT REFERENCES locations ON DELETE CASCADE,
  group_id    INT REFERENCES user_groups ON DELETE CASCADE
);
INSERT INTO locations_valid_user_groups (location_id, group_id)
VALUES
  (
    (SELECT id
     FROM locations
     LIMIT 1),
    (SELECT id
     FROM user_groups
     LIMIT 1)
  );

/* DEVICE MANAGEMENT */
CREATE TABLE devices
(
  id                        SERIAL PRIMARY KEY,
  name                      VARCHAR(50)                  NOT NULL,
  position                  INT                          NOT NULL DEFAULT 0,
  location_id               INTEGER REFERENCES locations NOT NULL DEFAULT 1,
  fhem_name                 VARCHAR(50)                  NOT NULL DEFAULT '',
  fhem_switch_name          VARCHAR(50)                  NOT NULL DEFAULT '',
  fhem_power_name           VARCHAR(50)                  NOT NULL DEFAULT '',
  deconz_uuid               VARCHAR(64)                  DEFAULT '',
  auto_end_power_threashold REAL                         NOT NULL DEFAULT 0.5,
  auto_end_wait_time        INT                          NOT NULL DEFAULT 20,
  enabled                   BOOLEAN                      NOT NULL DEFAULT TRUE
);

/* Valid users on devices */
CREATE TABLE devices_valid_user_groups
(
  device_id INT REFERENCES devices ON DELETE CASCADE,
  group_id  INT REFERENCES user_groups ON DELETE CASCADE
);

CREATE TYPE PROGRAM_TYPE AS ENUM ('FIXED', 'DYNAMIC');
CREATE TYPE TIME_UNIT_TYPE AS ENUM ('SECONDS', 'MINUTES', 'HOURS');

CREATE TABLE programs
(
  id                SERIAL PRIMARY KEY,
  name              VARCHAR(50)  NOT NULL,
  type              PROGRAM_TYPE NOT NULL,
  max_duration      INTEGER      NOT NULL,
  free_duration     INTEGER      NOT NULL DEFAULT 0,
  flagfall          NUMERIC,
  rate              NUMERIC,
  time_unit         TIME_UNIT_TYPE,
  auto_end          BOOLEAN      NOT NULL DEFAULT TRUE,
  earliest_auto_end INTEGER      NOT NULL DEFAULT 0,
  enabled           BOOLEAN      NOT NULL DEFAULT TRUE
);

/* Valid users on programs */
CREATE TABLE programs_valid_user_groups
(
  program_id INT REFERENCES programs ON DELETE CASCADE,
  group_id   INT REFERENCES user_groups ON DELETE CASCADE
);

/* Available programs on devices */
CREATE TABLE device_program_rel
(
  device_id  INTEGER REFERENCES devices ON DELETE CASCADE  NOT NULL,
  program_id INTEGER REFERENCES programs ON DELETE CASCADE NOT NULL
);

/* EXECUTION */
CREATE TABLE executions
(
  id         SERIAL PRIMARY KEY,
  device_id  INTEGER REFERENCES devices ON DELETE SET DEFAULT  NOT NULL DEFAULT -1,
  program_id INTEGER REFERENCES programs ON DELETE SET DEFAULT NOT NULL DEFAULT -1,
  user_id    INTEGER REFERENCES users ON DELETE SET DEFAULT    NOT NULL DEFAULT -1,
  start      TIMESTAMP,
  stop       TIMESTAMP,
  finished   BOOLEAN                                           NOT NULL DEFAULT FALSE
);

/* CREDIT ACCOUNTING */
CREATE TABLE credit_accounting
(
  id           SERIAL PRIMARY KEY,
  user_id      INTEGER REFERENCES users NOT NULL,
  execution_id INTEGER REFERENCES executions,
  amount       NUMERIC                  NOT NULL,
  date         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  description  TEXT
);

/* Authkey-Verzeichnis */
CREATE TABLE foreign_authkeys
(
  /* Der Auth-Key-Prefix, für den ein anderer Server zuständig ist */
  prefix    VARCHAR(50) NOT NULL,
  /* Die Adresse des zuständigen Servers */
  server_address  VARCHAR(50) NOT NULL
);

/* Reservierungen von Geräten */
CREATE TABLE reservations
(
  id         SERIAL PRIMARY KEY,
  user_id    INTEGER REFERENCES users NOT NULL,
  device_id  INTEGER REFERENCES devices NOT NULL,
  start_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CONSTRAINT  res_unique_constraint UNIQUE(user_id, device_id)
);

/* USERS & PERMISSIONS */
CREATE GROUP elwaclients;
CREATE USER elwaclient1 WITH PASSWORD 'elwaclient1'
  IN GROUP elwaclients;

GRANT SELECT ON ALL TABLES IN SCHEMA public TO GROUP elwaclients;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO GROUP elwaclients;

GRANT INSERT, UPDATE ON executions TO GROUP elwaclients;
GRANT UPDATE ON SEQUENCE executions_id_seq TO GROUP elwaclients;

GRANT UPDATE ON locations TO GROUP elwaclients;

GRANT UPDATE ON devices TO GROUP elwaclients;

GRANT INSERT ON credit_accounting TO GROUP elwaclients;
GRANT UPDATE ON SEQUENCE credit_accounting_id_seq TO GROUP elwaclients;

CREATE USER elwaportal;

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO elwaportal;
GRANT SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO elwaportal;
REVOKE UPDATE, DELETE ON credit_accounting FROM elwaportal;

CREATE USER elwaapi WITH PASSWORD 'api1234';
GRANT SELECT ON ALL TABLES IN SCHEMA public TO elwaapi;
GRANT UPDATE ON users TO elwaapi;
GRANT INSERT, DELETE ON reservations TO elwaapi;