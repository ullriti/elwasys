# 02 – Datenmodell (PostgreSQL)

Quelle: `Common/resources/database-init.sql` (DB-Version `0.4.0`).
Upgrades (historisch, seit Phase 2 AP1 nicht mehr fortgeschrieben – siehe „Flyway-Baseline“
unten): `Common/resources/database-upgrade/upgrade_0.3.1_0.3.2.sql`, `upgrade_0.4.0.sql`.

## ER-Überblick

```
user_groups ─┬─< users >──┬─< credit_accounting
             │            │
             │            └─< reservations >── devices
             ├─< locations_valid_user_groups >── locations ──< devices
             ├─< devices_valid_user_groups >── devices
             └─< programs_valid_user_groups >── programs
                                                  │
devices ──< device_program_rel >── programs      │
executions >── devices, programs, users ─────────┘
```

## Tabellen

### config
Schlüssel/Wert-Konfiguration.
- `key` (unique), `value`
- Seeds: `db.version=0.4.0`, `authkey.prefix` (2 Zufallszeichen),
  `reservation.duration=900` (Sek.)

### user_groups
Benutzergruppen mit Rabattregel.
- `id`, `name`
- `discount_type` ENUM `DISCOUNT_TYPE` = `NONE` | `FIX` | `FACTOR`
- `discount_value` (double)
- Seed: `Default`

### users
- `id`, `name`, `username` (unique), `email`, `card_ids` (Text, mehrere RFID-IDs)
- `blocked`, `password` (SHA1-Hash, 40 hex), `is_admin`
- `email_notification`, `push_notification`, `pushover_user_key`
- `password_reset_key`, `password_reset_timeout`
- `deleted`, `last_login`, `group_id` → user_groups (Default 1)
- App-Anbindung: `app_id`, `access_key`, `auth_key` (Trigger `user_authkey_trigger`
  generiert `auth_key` beim INSERT über `generate_user_authkey()`)
- Seed: `admin` / Passwort-Hash `d033e22ae348aeb5660fc2140aec35850c4da997`
  (= SHA1 von „admin“), `is_admin=TRUE`

### locations
Ein Standort = ein Client-Terminal.
- `id`, `name` (unique)
- `client_uid`, `client_ip`, `client_port`, `client_last_seen` (vom Client gepflegt)
- Seed: `Default`

### locations_valid_user_groups
n:m Standort ↔ erlaubte Benutzergruppen. Seed: Default-Location ↔ Default-Group.

### devices
Ein Gerät (Waschmaschine/Trockner).
- `id`, `name`, `position`, `location_id` → locations
- fhem: `fhem_name`, `fhem_switch_name`, `fhem_power_name`
- deCONZ: `deconz_uuid`
- Ende-Erkennung: `auto_end_power_threashold` (REAL, Default 0.5 W),
  `auto_end_wait_time` (INT, Default 20 s)
- `enabled`

### devices_valid_user_groups
n:m Gerät ↔ erlaubte Benutzergruppen.

### programs
Waschprogramm / Tarif.
- `id`, `name`
- `type` ENUM `PROGRAM_TYPE` = `FIXED` | `DYNAMIC`
- `max_duration`, `free_duration` (Sek.)
- `flagfall` (Grundgebühr), `rate` (Preis pro Zeiteinheit), `time_unit`
  ENUM `TIME_UNIT_TYPE` = `SECONDS` | `MINUTES` | `HOURS`
- `auto_end`, `earliest_auto_end` (Sek.), `enabled`

### programs_valid_user_groups
n:m Programm ↔ erlaubte Benutzergruppen.

### device_program_rel
n:m Gerät ↔ verfügbare Programme.

### executions
Eine Programm-Ausführung (Waschvorgang).
- `id`, `device_id` → devices, `program_id` → programs, `user_id` → users
  (alle `ON DELETE SET DEFAULT`, Default -1)
- `start`, `stop`, `finished`

### credit_accounting
Guthaben-Buchungen.
- `id`, `user_id` → users, `execution_id` → executions (nullable)
- `amount` (numeric, +Aufladung / −Verbrauch), `date`, `description`

### foreign_authkeys
Verzeichnis für Föderation mit anderen Servern.
- `prefix` (Auth-Key-Prefix), `server_address`

### reservations
Gerätereservierungen.
- `id`, `user_id` → users, `device_id` → devices, `start_time`
- Unique-Constraint (`user_id`, `device_id`)

## DB-Rollen & Rechte

- **Gruppe `elwaclients`**, User `elwaclient1` (PW `elwaclient1`):
  SELECT auf alles; INSERT/UPDATE auf `executions`; UPDATE auf `locations`, `devices`;
  INSERT auf `credit_accounting`. (Terminal darf nur Nötiges schreiben.)
- **User `elwaportal`**: volles SELECT/INSERT/UPDATE/DELETE, aber `REVOKE UPDATE, DELETE`
  auf `credit_accounting` (Buchungen sind unveränderlich).
- **User `elwaapi`** (PW `api1234`): SELECT auf alles; UPDATE auf `users`;
  INSERT/DELETE auf `reservations` (für die mobile App).

## Migrations-relevante Beobachtungen

- Passwörter als **SHA1** gespeichert → bei Modernisierung sicherheitskritisch
  (Wechsel auf bcrypt/argon2 einplanen, Migrationspfad nötig).
- Klartext-/schwache Default-Passwörter (`elwaclient1`, `api1234`) → härten.
- Tippfehler in Spaltenname `auto_end_power_threashold` (statt *threshold*) – bei
  Schema-Refactor berücksichtigen (Kompatibilität!).

## Flyway-Baseline (seit Phase 2 AP1, 2026-07-20)

Neues Backend-Modul (`backend/`, siehe kb/03-modules.md) übernimmt die Schemapflege künftig
über **Flyway** statt der handgepflegten SQL-Skripte. Details/Entscheidungen siehe
kb/05-migration-plan.md (Änderungslog, Phase 2 AP1); hier die für das Datenmodell relevante
Zusammenfassung:

- **Baseline-Migration**: `backend/src/main/resources/db/migration/V1__baseline_schema_0_4_0.sql`.
  Inhaltlich eine 1:1-Übernahme von `Common/resources/database-init.sql` (ohne die
  psql-only `CREATE DATABASE`/`\connect`-Zeilen, die für eine bereits per JDBC-URL gewählte
  Ziel-DB nicht gebraucht werden). **Nicht** separat aus den beiden
  `database-upgrade/*.sql`-Skripten rekonstruiert: `database-init.sql` enthält bereits deren
  Endzustand (0.4.0), ein frischer Lauf beider Wege ergibt also per Konstruktion dasselbe
  Schema – siehe Verifikation unten. Einzige technische Anpassung: die Rollenanlage
  (`CREATE GROUP`/`CREATE USER` für `elwaclients`/`elwaclient1`/`elwaportal`/`elwaapi`) ist in
  einen `DO`-Block mit Existenzprüfung (`pg_roles`) gefasst, weil PostgreSQL-Rollen
  Cluster-weit sind (nicht pro Datenbank) und ein zweiter Lauf gegen denselben Cluster sonst
  mit „role already exists“ fehlschlagen würde. Der Spaltentypo
  `auto_end_power_threashold` bleibt bewusst erhalten (Umbenennung erst Phase 5, siehe
  Roadmap).
- **Zwei Betriebsszenarien**, beide mit `backend/verify-schema-baseline.sh` verifiziert:
  1. **Frische, leere DB**: Flyway führt `V1` normal aus → schema-äquivalent zu einer frischen
     `database-init.sql`-DB (verifiziert per `pg_dump --schema-only`-Diff; einzige Abweichung
     ist Flywayss eigene Buchführungstabelle `flyway_schema_history` + deren PK/Index, die
     bewusst nicht Teil des Anwendungsschemas ist).
  2. **Bestehende Alt-Weg-DB** (über `database-init.sql` + ggf. `database-upgrade/*.sql`
     angelegt, mit Daten): `spring.flyway.baseline-on-migrate=true` (siehe
     `backend/src/main/resources/application.yml`) markiert sie beim ersten Start als bereits
     auf `baselineVersion=1` migriert, ohne `V1` erneut auszuführen oder Daten zu verändern.
     Verifiziert: Backend startet sauber (Health-Endpoint UP), `flyway_schema_history` enthält
     genau eine `BASELINE`-Zeile bei Version 1, Bestandsdaten (z. B. der `admin`-Nutzer)
     bleiben unverändert.
- **`config.db.version` stillgelegt**: Untersuchung von Common/Client-Raspi/Portal (Grep über
  alle drei Module) zeigt, dass **kein** Java-Code (`DataManager`, `ConfigurationManager` o.
  ä.) den Wert von `config.db.version` je liest – es gab nie einen automatischen
  Upgrade-Mechanismus im Code; die `database-upgrade/*.sql`-Skripte wurden offenbar manuell
  vom Betreiber per `psql -f` ausgeführt, `db.version` diente rein als von Hand gepflegte
  Notiz in den SQL-Skripten selbst. Verhalten bewahren heißt hier: der Seed-Wert
  `db.version = '0.4.0'` bleibt in der Flyway-Baseline erhalten (für den Fall, dass ihn
  doch irgendein Alt-Code oder externes Werkzeug liest), wird aber **ab sofort nicht mehr
  fortgeschrieben** – zukünftige Schemaänderungen laufen ausschließlich über weitere
  Flyway-Migrationen (`V2__...`, `V3__...`, …). `Common/resources/database-upgrade/*.sql`
  wird nicht mehr gepflegt; die vorhandenen Dateien (`upgrade_0.3.1_0.3.2.sql`,
  `upgrade_0.4.0.sql`) bleiben unverändert als historisches Artefakt im Repo (Bestands-DBs,
  die noch über sie hochgezogen werden, landen ohnehin beim Endstand 0.4.0, den die Baseline
  abbildet).
- **Rollen/Grants**: Die DB-Rollen `elwaclient1`/`elwaportal`/`elwaapi` (siehe „DB-Rollen &
  Rechte“ oben) werden von der Baseline unverändert mit angelegt/gegrantet – das Backend
  selbst nutzt sie in AP1 noch nicht (es hat noch keine fachlichen Endpunkte); die
  Ablösung durch einen einzelnen technischen Backend-User ist laut Roadmap erst Phase 5
  vorgesehen.

## JPA-Entities (seit Phase 2 AP2, 2026-07-20)

Details zu den Entities/Repositories/Services siehe kb/03-modules.md (Abschnitt Backend)
und kb/05-migration-plan.md (Änderungslog, AP2). Für das Datenmodell relevante Erkenntnisse:

- **Postgres-native Enums** (`DISCOUNT_TYPE`, `PROGRAM_TYPE`, `TIME_UNIT_TYPE`) lassen sich
  nicht per einfachem `@Enumerated(STRING)` gegen eine Postgres-ENUM-Spalte binden (Fehler
  „column is of type … but expression is of type character varying“) – die Entities nutzen
  dafür Hibernates `@JdbcTypeCode(SqlTypes.NAMED_ENUM)`.
- **`auth_key`-Trigger bleibt wirksam**: die Spalte ist zwar bewusst nicht in `UserEntity`
  gemappt (siehe Rahmenbedingungen zu den App-Relikt-Spalten), der DB-Trigger
  `user_authkey_trigger` (`BEFORE INSERT ON users`) befüllt sie bei jedem per JPA
  ausgeführten INSERT trotzdem automatisch – verifiziert (keine NOT-NULL-/Constraint-
  Verletzung beim Anlegen eines `UserEntity` über den Backend-Testlauf).
- **`credit_accounting.date`**: die Spalte hat einen `CURRENT_TIMESTAMP`-DB-Default, den
  der Alt-Code nie explizit überschreibt. `CreditAccountingEntryEntity` setzt den Wert
  stattdessen bewusst per Anwendungs-Uhr – siehe kb/05-migration-plan.md (Änderungslog,
  AP2) für die Begründung.
- **Alle fachlich genutzten Assoziationen sind `FetchType.EAGER`** (kein `LAZY`), um den
  Alt-`DataManager` nachzubilden, der beim Laden eines Objekts immer sofort alle
  referenzierten Objekte mitlädt – siehe kb/05-migration-plan.md (Änderungslog, AP2).
