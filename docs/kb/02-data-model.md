# 02 – Datenmodell (PostgreSQL)

Das Schema wird ausschließlich über **Flyway** verwaltet (siehe „Migrationen" unten). Basis ist
die Baseline `backend/src/main/resources/db/migration/V1__baseline_schema_0_4_0.sql`
(DB-Version `0.4.0`) – die einzige Quelle des Basis-Schemas.

## ER-Überblick

```
user_groups ─┬─< users >──┬─< credit_accounting
             │            │
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
- Seed: `db.version=0.4.0` (reine Notiz; die Schemaversionierung läuft über Flyway, nicht über
  diesen Wert)

### user_groups
Benutzergruppen mit Rabattregel.
- `id`, `name`
- `discount_type` ENUM `DISCOUNT_TYPE` = `NONE` | `FIX` | `FACTOR`
- `discount_value` (double)
- Seed: `Default`

### users
- `id`, `name`, `username` (unique), `email`, `card_ids` (Text, mehrere RFID-IDs)
- `blocked`, `password` (`VARCHAR(255)` – trägt SHA1-Legacy- oder Argon2id-Hashes, siehe
  docs/kb/03-modules.md „Auth"), `is_admin`
- `email_notification`, `push_notification`, `pushover_user_key`
- `password_reset_key`, `password_reset_timeout` – vom Backend für den Passwort-Reset genutzt
  (`PasswordResetService`)
- `deleted`, `last_login`, `group_id` → user_groups (Default 1)
- Seed: `admin` (`is_admin=TRUE`); frische Installationen haben kein Default-Passwort – es wird
  über das Admin-CLI gesetzt (siehe docs/kb/04-build-and-run.md)

### locations
Ein Standort = ein Client-Terminal.
- `id`, `name` (unique)
- `offline_max_duration_minutes` (INT, NOT NULL, Default 60) – wie lange dieses Terminal ohne
  Backend-Verbindung eigenständig neue Buchungen annimmt, bevor es sie ablehnt; im
  Portal-Standorte-Dialog editierbar (`LocationFormDialog`/`AdminLocationsView`), ans Terminal
  über `SnapshotDto#offlineMaxDurationMinutes()` ausgeliefert (siehe docs/kb/03-modules.md)
- Seed: `Default`

### locations_valid_user_groups
n:m Standort ↔ erlaubte Benutzergruppen. Seed: Default-Location ↔ Default-Group.

### devices
Ein Gerät (Waschmaschine/Trockner).
- `id`, `name`, `position`, `location_id` → locations
- fhem: `fhem_name`, `fhem_switch_name`, `fhem_power_name`
- deCONZ: `deconz_uuid`
- Ende-Erkennung: `auto_end_power_threshold` (REAL, Default 0.5 W), `auto_end_wait_time`
  (INT, Default 20 s)
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
Guthaben-Buchungen (unveränderlich).
- `id`, `user_id` → users, `execution_id` → executions (nullable)
- `amount` (numeric, +Aufladung / −Verbrauch), `date`, `description`

### terminal_tokens
Standort-Tokens für die Terminal-REST-API und den WebSocket-Endpunkt.
- `id`, `location_id` → locations (`ON DELETE CASCADE`)
- `token_hash` (VARCHAR(64), unique) – SHA-256-Hex-Hash des Klartext-Tokens; das Klartext-Token
  wird NIE gespeichert (nur einmalig beim Erzeugen angezeigt)
- `label` (optional, rein informativ, z. B. Terminal-Hostname)
- `created_at`, `revoked_at` (NULL = aktiv), `last_used_at` (vom Auth-Filter aktualisiert)
- Mehrere aktive Tokens pro Standort zulässig (Rotation ohne Ausfallfenster: neues Token anlegen,
  Terminal umstellen, altes per `revoked_at` widerrufen)

### terminal_idempotency_keys
Dedupliziert terminal-gemeldete Execution-Ereignisse (Start/Ende/Abbruch/Reset über
`/api/v1/executions/**`, siehe docs/kb/03-modules.md „Idempotenz + Replay").
- `id`, `location_id` → locations (`ON DELETE CASCADE`, rein informativ)
- `idempotency_key` (VARCHAR(64), unique) – die vom Terminal erzeugte UUID; ein Schlüssel
  identifiziert GENAU EIN fachliches Ereignis, unabhängig vom HTTP-Pfad
- `operation` (VARCHAR(50)) – z. B. `execution-start`/`-finish`/`-abort`/`-reset`, rein
  informativ/für Diagnose
- `response_status`, `response_body` (TEXT) – die beim ersten, erfolgreichen Aufruf gelieferte
  Antwort; ein Replay liefert sie unverändert erneut aus, ohne die fachliche Aktion erneut
  auszuführen
- `created_at`
- Nur ERFOLGREICHE Aufrufe werden abgelegt – ein fehlgeschlagener Erstversuch „friert" nicht
  ein; ein erneuter Versuch mit demselben Schlüssel führt die Aktion normal erneut aus

## DB-Rollen & Rechte

`elwaportal` ist der **einzige** Anwendungs-DB-User: volles SELECT/INSERT/UPDATE/DELETE, aber
`REVOKE UPDATE, DELETE` auf `credit_accounting` (Buchungen sind unveränderlich). Siehe
[ADR 0011](../architecture/0011-db-rollen-haertung-ein-app-user.md) und docs/kb/01-architecture.md
(„Kommunikationswege").

## Migrationen

Flyway-Baseline `V1` (Schema 0.4.0) plus rein additive Migrationen; aktueller Stand **V1–V11**.
Herleitung/Abwägung je Version siehe [05-migration-plan.md](05-migration-plan.md) (Änderungslog).

| Version | Wirkung |
|---|---|
| `V1`  | Baseline-Schema 0.4.0 (Bestandstabellen, Enums, Seeds) |
| `V2`  | `users.password` → `VARCHAR(255)` (Platz für Argon2id-Hashes, ~97 Zeichen) |
| `V3`  | Tabelle `terminal_tokens` |
| `V4`  | Tabelle `terminal_idempotency_keys` |
| `V5`  | Spalte `locations.offline_max_duration_minutes` (NOT NULL, Default 60) |
| `V6`  | DB-Rollen gehärtet: `elwaclient1`/`elwaapi` + Gruppe `elwaclients` entfernt (idempotent) |
| `V7`  | Seed-`admin`-Passwort auf `NULL` (nur bei unverändertem Default) |
| `V8`  | Spaltentypo `auto_end_power_threashold` → `auto_end_power_threshold` |
| `V9`  | `locations.client_uid`/`client_ip`/`client_port`/`client_last_seen` entfernt |
| `V10` | App-Reste entfernt: Tabellen `reservations`/`foreign_authkeys`, `users.app_id`/`access_key`/`auth_key` + Trigger, `config`-Seeds `authkey.prefix`/`reservation.duration` |
| `V11` | Performance-Indizes auf `executions(user_id)`/`(device_id)`, `credit_accounting(user_id)` |

Betriebsszenarien: eine frische, leere DB bekommt `V1` normal ausgeführt; eine bestehende
(pre-Flyway) 0.4.0-DB wird über `spring.flyway.baseline-on-migrate=true` als BASELINE@1 markiert
(ohne `V1` erneut auszuführen) und dann mit `V2..V11` fortgeschrieben. Der vollständige
Cutover-Pfad wird durch `deploy/cutover/verify-cutover-migration.sh` verifiziert (Datenerhalt +
Wirksamkeit der Härtungs-Migrationen; siehe docs/kb/04-build-and-run.md und `deploy/cutover/README.md`).

## JPA-Bindung (Hinweise fürs Datenmodell)

Details zu Entities/Repositories/Services: docs/kb/03-modules.md (Abschnitt Backend). Fürs
Datenmodell relevant:

- **Postgres-native Enums** (`DISCOUNT_TYPE`, `PROGRAM_TYPE`, `TIME_UNIT_TYPE`) werden über
  Hibernates `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` gebunden (nicht `@Enumerated`, das gegen eine
  Postgres-ENUM-Spalte fehlschlägt).
- **`credit_accounting.date`** wird bewusst per Anwendungs-Uhr gesetzt (nicht über den
  DB-`CURRENT_TIMESTAMP`-Default).
- **Alle fachlich genutzten Assoziationen sind `FetchType.EAGER`** (bildet den durchgängig eager
  ladenden Alt-`DataManager` nach).

## Historie

- **2026-07-23** — Performance-Indizes `V11` ergänzt
  ([Worklog Pre-Launch AP5](../worklog/2026-07-23-ap5-portal-performance-crud.md) ·
  [Änderungslog](05-migration-plan.md)).
- **2026-07-21** — Alt-/App-Bestand per Migration entfernt: `V6` DB-Rollen-Härtung
  ([ADR 0011](../architecture/0011-db-rollen-haertung-ein-app-user.md)), `V7`
  Admin-Default-Passwort neutralisiert, `V8` Spaltentypo-Fix, `V9` `locations.client_*`,
  `V10` App-Reste (`reservations`/`foreign_authkeys`/`auth_key`-Trigger)
  ([Worklog Phase 5](../worklog/2026-07-21-phase-5-aufraeumen.md) · [Änderungslog](05-migration-plan.md)).
- **2026-07-20/21** — additive Neutabellen `terminal_tokens` (`V3`) und
  `terminal_idempotency_keys` (`V4`), Spalte `offline_max_duration_minutes` (`V5`),
  `users.password` auf 255 verbreitert (`V2`) ([Änderungslog](05-migration-plan.md)).
- **2026-07-22** — Schema-Konsolidierung: das frühere Duplikat `database/database-init.sql` und
  die `database-upgrade/*.sql`-Skripte wurden entfernt; `V1` ist seither die einzige Quelle des
  0.4.0-Schemas ([Worklog Phase-5-Nachtrag](../worklog/2026-07-22-phase-5-nachtrag-common-und-schema.md)).
