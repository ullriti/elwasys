# 02 – Datenmodell (PostgreSQL)

Quelle: `Common/resources/database-init.sql` (DB-Version `0.4.0`).
Upgrades: `Common/resources/database-upgrade/upgrade_0.3.1_0.3.2.sql`,
`upgrade_0.4.0.sql`.

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

- Schema-Version wird in `config.db.version` gehalten; Upgrade-Skripte manuell/über
  DataManager (prüfen, wie Upgrades angewandt werden).
- Passwörter als **SHA1** gespeichert → bei Modernisierung sicherheitskritisch
  (Wechsel auf bcrypt/argon2 einplanen, Migrationspfad nötig).
- Klartext-/schwache Default-Passwörter (`elwaclient1`, `api1234`) → härten.
- Tippfehler in Spaltenname `auto_end_power_threashold` (statt *threshold*) – bei
  Schema-Refactor berücksichtigen (Kompatibilität!).
